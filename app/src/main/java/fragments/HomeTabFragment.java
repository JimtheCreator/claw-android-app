package fragments;

import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.claw.ai.MainActivity;
import com.claw.ai.R;
import com.claw.ai.databinding.FragmentHomeTabBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import adapters.SymbolAdapter;
import animations.BounceEdgeEffectFactory;
import animations.MotionAnimation;
import factory.HomeViewModelFactory;
import model_interfaces.OnWatchlistActionListener;
import models.Symbol;
import space.SpacebetweenItems;
import timber.log.Timber;
import utils.DateUtils;
import utils.KeyboardQuickFunctions;
import viewmodels.HomeViewModel;
import viewmodels.google_login.AuthViewModel;

public class HomeTabFragment extends Fragment {

    // Initialize a variable to store the previous slideOffset
    private float lastSlideOffset = -1;
    boolean isBottomSheetExpanded = false;
    private FragmentHomeTabBinding binding;

    private boolean isSearchExpanded = false;

    // Add these class variables
    private final Handler searchHandler = new Handler();
    private static final long DEBOUNCE_DELAY = 300; // 300ms delay
    private String currentQuery = "";
    private BottomSheetBehavior<View> bottomSheetBehavior;
    private HomeViewModel homeViewModel;
    HomeViewModelFactory factory;
    int half_expanded_state_tag = 0;
    int expanded_state_tag = 0;
    int collapsed_state_tag = 0;

    private List<Symbol> searchedSymbolList, watchListSymbolList;
    private SymbolAdapter searchedSymbolAdapter, watchlistAdapter;

    private DisplayMetrics metrics;
    private AuthViewModel authViewModel;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentHomeTabBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews();
        setupBottomSheet();
        initializeSearch();
        setupClickListeners();
        setupBackPressHandler();
        setupSearchedCryptoList();
        setupObservers();

        if (bottomSheetBehavior != null) {
            int peekHeight = bottomSheetBehavior.getPeekHeight();
            binding.watchlistScrollView.setPadding(
                    binding.watchlistScrollView.getPaddingLeft(),
                    binding.watchlistScrollView.getPaddingTop(),
                    binding.watchlistScrollView.getPaddingRight(),
                    peekHeight // Add padding to the bottom
            );
        }
    }


    private void initializeViews() {
        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        // Ensure web_client_id is correctly defined in your strings.xml
        authViewModel.initialize(requireActivity(), getString(R.string.web_client_id));

        searchedSymbolList = new ArrayList<>();
        watchListSymbolList = new ArrayList<>();

        factory = new HomeViewModelFactory(requireActivity().getApplication());
        homeViewModel = new ViewModelProvider(this, factory).get(HomeViewModel.class);

        initializeAdapters();
        setupRecyclerViews();

        binding.dateText.setText(DateUtils.getFormattedDate());

        // Initially hide searched list and show the empty state
        binding.searchedCryptosList.setVisibility(View.GONE);
        binding.emptySearchState.setVisibility(View.GONE);

        binding.searchBox.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                KeyboardQuickFunctions.closeKeyboard(textView, requireContext());
                String query = textView.getText().toString().trim();

                bottomSheetBehavior.setPeekHeight((int) (metrics.heightPixels * 0.1));
                bottomSheetBehavior.setFitToContents(false);
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);

                return true; // We handled the action
            }
            return false;
        });
    }

    // Bottom Sheet Configuration
    private void setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.nest);
        metrics = getDisplayMetrics();
        bottomSheetBehavior.setPeekHeight((int) (metrics.heightPixels * 0.1));
        bottomSheetBehavior.setFitToContents(false);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                Timber.tag("BottomSheet").d("State: %s", newState);
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    collapsed_state_tag = 1;
                    isBottomSheetExpanded = false;
                    bottomSheetBehavior.setDraggable(true);
                    searchBarStateBeforeClicked();
                    KeyboardQuickFunctions.closeKeyboard(binding.searchBox, requireContext());
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    expanded_state_tag = 3;
                    bottomSheetBehavior.setDraggable(false);
                    isBottomSheetExpanded = true;
                    imeKeyboardSearch(bottomSheet);

                    if (half_expanded_state_tag == 0) {
                        searchBarStateWhenClicked();
                    }

                } else if (newState == BottomSheetBehavior.STATE_HALF_EXPANDED) {
                    half_expanded_state_tag = 2;
                    bottomSheetBehavior.setDraggable(false);
                    isBottomSheetExpanded = false;
                    searchBarStateWhenClicked();

                }

            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                Timber.tag("BottomSheet").d("Slide: %s", slideOffset);
                // Check if the slideOffset has changed direction (dragging down)
                if (lastSlideOffset != -1) {
                    // If the slideOffset is decreasing, it's being dragged down
                    if (slideOffset < lastSlideOffset) {
                        // Bottom sheet is being dragged down
                        bottomSheetBehavior.setDraggable(true);
                    }
                }

                // Update the lastSlideOffset for the next slide
                lastSlideOffset = slideOffset;

                // Optional: Log the slideOffset value for debugging
                Timber.tag("BottomSheet").d("Slide: %s", slideOffset);
            }
        });

    }

    // Search Handling
    private void initializeSearch() {
        searchBarStateBeforeClicked();
    }

    private void searchBarStateBeforeClicked() {
        binding.frameSearchBox.setVisibility(View.GONE);
        binding.dummySearchbox.setVisibility(View.VISIBLE);
        binding.exitSearch.setVisibility(View.GONE);
        // Hide search results when search is closed
        binding.searchedCryptosList.setVisibility(View.GONE);
        bottomSheetBehavior.setPeekHeight((int) (metrics.heightPixels * 0.1));
        bottomSheetBehavior.setFitToContents(false);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        MotionAnimation.animateSmoothScrollToBottom(binding.scrollView);
    }

    private void searchBarStateWhenClicked() {
        binding.frameSearchBox.setVisibility(View.VISIBLE);
        binding.dummySearchbox.setVisibility(View.GONE);
        binding.exitSearch.setVisibility(View.VISIBLE);
        binding.searchBox.requestFocus();
        KeyboardQuickFunctions.showKeyboard(binding.searchBox, requireContext());
        // Show searched list when search is active
        binding.searchedCryptosList.setVisibility(View.VISIBLE);

        bottomSheetBehavior.setPeekHeight((int) (metrics.heightPixels * 0.1));
        bottomSheetBehavior.setFitToContents(false);
        bottomSheetBehavior.setHalfExpandedRatio(0.6f);

        if (!isBottomSheetExpanded) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
        } else {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }

    }

    private void imeKeyboardSearch(View bottomSheet) {
        binding.frameSearchBox.setVisibility(View.VISIBLE);
        binding.dummySearchbox.setVisibility(View.GONE);
        binding.exitSearch.setVisibility(View.VISIBLE);
        binding.searchBox.clearFocus();
        binding.searchedCryptosList.setVisibility(View.VISIBLE);
        MotionAnimation.animateSmoothScrollToTop(binding.scrollView);
        bottomSheetBehavior.setPeekHeight((int) (metrics.heightPixels * 0.1));
        bottomSheetBehavior.setFitToContents(false);
        bottomSheetBehavior.setHalfExpandedRatio(0.6f);

        if (!isBottomSheetExpanded) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
        } else {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }

    }
    private void setupClickListeners() {
        binding.signup.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchToTab(R.id.more);
            }
        });

        binding.clearKeyboardText.setOnClickListener(v -> {
            binding.searchBox.setText("");
            KeyboardQuickFunctions.showKeyboard(binding.searchBox, requireContext());
            clearSearch();
        });

        binding.searchBar.setOnClickListener(v -> {
            MotionAnimation.animateSmoothScrollToTop(binding.scrollView);
            searchBarStateWhenClicked();
        });

        binding.exitSearch.setOnClickListener(v -> {
            KeyboardQuickFunctions.closeKeyboard(binding.searchBox, requireContext());
            searchBarStateBeforeClicked();
            binding.searchBox.setText("");

            bottomSheetBehavior.setPeekHeight((int) (metrics.heightPixels * 0.1));
            bottomSheetBehavior.setFitToContents(false);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            MotionAnimation.animateSmoothScrollToBottom(binding.scrollView);
        });

        binding.addSymbolButton.setOnClickListener(v -> {
            MotionAnimation.animateSmoothScrollToTop(binding.scrollView);
            searchBarStateWhenClicked();
        });

        // Handle back press
        requireActivity().getOnBackPressedDispatcher().addCallback(requireActivity(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED || bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HALF_EXPANDED) {
                    MotionAnimation.animateSmoothScrollToBottom(binding.scrollView);
                    KeyboardQuickFunctions.closeKeyboard(binding.searchBox, requireContext());
                    binding.searchBox.setText("");
                    // Clear search results
                    searchedSymbolList.clear();
                    searchedSymbolAdapter.notifyDataSetChanged();
                    searchBarStateBeforeClicked();
                }
            }
        });
    }

    private void handleSearchClose() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED || bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HALF_EXPANDED) {
            MotionAnimation.animateSmoothScrollToBottom(binding.scrollView);
            KeyboardQuickFunctions.closeKeyboard(binding.searchBox, requireContext());
            binding.searchBox.setText("");
            // Clear search results
            searchedSymbolList.clear();
            searchedSymbolAdapter.notifyDataSetChanged();
            searchBarStateBeforeClicked();
        } else {
            requireActivity().finishAffinity();
        }
    }

    // Back Press Handling
    private void setupBackPressHandler() {
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        handleSearchClose();
                    }
                }
        );
    }

    // Helper Methods
    private DisplayMetrics getDisplayMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        requireActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
        return metrics;
    }


    /**
     * Initializes the RecyclerView that displays the search results for cryptocurrencies.
     *
     * <p>This method sets up a vertically scrolling list using {@link LinearLayoutManager},
     * attaches an instance of {@link SymbolAdapter} to provide the data and views,
     * and applies a custom {@link BounceEdgeEffectFactory} to give the RecyclerView
     * an iOS-style bouncy overscroll effect.</p>
     *
     * @see SymbolAdapter
     * @see BounceEdgeEffectFactory
     */
    private void setupSearchedCryptoList() {
        int gap = 30;                // total px between items
        double thick = 1.4;          // px thickness of divider

        binding.searchedCryptosList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.searchedCryptosList.setHasFixedSize(true);
        binding.searchedCryptosList.setAdapter(searchedSymbolAdapter);
        binding.searchedCryptosList.addItemDecoration(new SpacebetweenItems(gap, thick, ContextCompat.getColor(requireContext(), R.color.searchbar_colour)));
        binding.searchedCryptosList.setEdgeEffectFactory(new BounceEdgeEffectFactory(requireContext()));
        binding.searchedCryptosList.setNestedScrollingEnabled(false);

        binding.searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                searchHandler.removeCallbacks(searchRunnable);
                currentQuery = s.toString().trim();

                if (!currentQuery.isEmpty()) {
                    binding.clearKeyboardText.setVisibility(View.VISIBLE);
                    showLoading(true);
                    searchHandler.postDelayed(searchRunnable, DEBOUNCE_DELAY);
                } else {
                    clearSearch();
                    binding.clearKeyboardText.setVisibility(View.GONE);
                }
            }
        });
    }

    private final Runnable searchRunnable = new Runnable() {
        @Override
        public void run() {
            if (currentQuery.length() >= 2) {
                homeViewModel.searchCryptos(currentQuery, 20);
            }
        }
    };

    private void showLoading(boolean show) {
        binding.searchProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.emptySearchState.setVisibility(View.GONE);
    }

    private void clearSearch() {
        showLoading(false);
        searchedSymbolList.clear();
        searchedSymbolAdapter.notifyDataSetChanged();
    }

    // Update setupObservers to handle both lists separately
    private void setupObservers() {
        homeViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (binding != null)
                binding.searchProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        homeViewModel.getIsWatchlistLoading().observe(getViewLifecycleOwner(), isWatchlistLoading -> {
            if (binding != null) {
                binding.homeProgressBar.setVisibility(isWatchlistLoading ? View.VISIBLE : View.GONE);
                binding.symbolWatchlistRecyclerview.setVisibility(isWatchlistLoading ? View.GONE : View.VISIBLE);
            }
        });

        homeViewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) showErrorToast(error);
        });

        homeViewModel.getSearchResults().observe(getViewLifecycleOwner(), symbols -> {
            if (binding == null) return;
            Timber.d("Search results observer: %d symbols. Query: '%s'", symbols != null ? symbols.size() : 0, binding.searchBox.getText().toString());
            searchedSymbolAdapter.setData(symbols != null ? symbols : Collections.emptyList());
            if (symbols == null || symbols.isEmpty()) {
                if (!binding.searchBox.getText().toString().trim().isEmpty()) { // Only show empty state if query is not empty
                    binding.emptySearchState.setVisibility(View.VISIBLE);
                    binding.searchedCryptosList.setVisibility(View.GONE);
                } else { // Query is empty, hide both
                    binding.emptySearchState.setVisibility(View.GONE);
                    binding.searchedCryptosList.setVisibility(View.GONE);
                }
            } else {
                binding.emptySearchState.setVisibility(View.GONE);
                binding.searchedCryptosList.setVisibility(View.VISIBLE);
            }
        });

        homeViewModel.getWatchlist().observe(getViewLifecycleOwner(), symbols -> {
            if (binding == null) return;

            // Enhanced logging
            Log.d("HomeTabFragment", "=== WATCHLIST OBSERVER DEBUG ===");
            Log.d("HomeTabFragment", "symbols is null? " + (symbols == null));
            Log.d("HomeTabFragment", "symbols size: " + (symbols != null ? symbols.size() : "null"));

            if (symbols != null && !symbols.isEmpty()) {
                Log.d("HomeTabFragment", "First symbol: " + symbols.get(0).getSymbol());
                Log.d("HomeTabFragment", "First symbol name: " + symbols.get(0).getSymbol());
            }

            // Check adapter state
            Log.d("HomeTabFragment", "watchlistAdapter is null? " + (watchlistAdapter == null));
            Log.d("HomeTabFragment", "Current adapter item count: " + (watchlistAdapter != null ? watchlistAdapter.getItemCount() : "adapter null"));

            // Check RecyclerView state
            Log.d("HomeTabFragment", "RecyclerView visibility: " + binding.symbolWatchlistRecyclerview.getVisibility());
            Log.d("HomeTabFragment", "RecyclerView adapter: " + (binding.symbolWatchlistRecyclerview.getAdapter() != null ? "attached" : "null"));

            if (symbols != null) {
                watchlistAdapter.setData(symbols);
                Log.d("HomeTabFragment", "After setData - adapter item count: " + watchlistAdapter.getItemCount());
                watchlistAdapter.notifyDataSetChanged();
                Log.d("HomeTabFragment", "notifyDataSetChanged() called");
            }

            updateWatchlistVisibility(symbols);

            Log.d("HomeTabFragment", "=== END WATCHLIST OBSERVER DEBUG ===");
        });

        homeViewModel.getWatchlistUpdateResult().observe(getViewLifecycleOwner(), result -> {
            // Check if 'result' itself is null FIRST
            if (result == null) {
                Timber.w("WatchlistUpdateResult is null");
                return;
            }

            Symbol affectedSymbol = result.getSymbol(); // Get the Symbol object from the result

            // Now check if the 'affectedSymbol' is null
            if (affectedSymbol == null) {
                Timber.w("Affected Symbol within WatchlistUpdateResult is null. Success: %s, IsAdded: %s", result.isSuccess(), result.isAdded());
                // Potentially show a generic success/failure message if symbol details are missing
                if (result.isSuccess()) {
                    Toast.makeText(getContext(), "Watchlist operation successful.", Toast.LENGTH_SHORT).show();
                } else {
                    String errorMsg = (result.getError() != null && result.getError().getMessage() != null)
                            ? result.getError().getMessage()
                            : "Watchlist operation failed";
                    Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();
                }
                return;
            }

            // If we reach here, 'affectedSymbol' is not null
            if (result.isSuccess()) {
                String action = result.isAdded() ? "added to" : "removed from";
                // Ensure affectedSymbol.getSymbol() (for the ticker) is not null if you use it.
                String ticker = affectedSymbol.getSymbol() != null ? affectedSymbol.getSymbol() : "Unknown Symbol";
                Toast.makeText(getContext(), ticker + " " + action + " watchlist.", Toast.LENGTH_SHORT).show();
            } else {
                String errorMsg = (result.getError() != null && result.getError().getMessage() != null)
                        ? result.getError().getMessage()
                        : "operation failed";
                String ticker = affectedSymbol.getSymbol() != null ? affectedSymbol.getSymbol() : "Unknown Symbol";
                Toast.makeText(getContext(), "Watchlist " + errorMsg + " for " + ticker, Toast.LENGTH_LONG).show();
            }
        });

        authViewModel.getAuthState().observe(getViewLifecycleOwner(), authState -> {
            if (binding == null) return;
            String currentUid = getCurrentUserId();
            switch (authState) {
                case AUTHENTICATED:
                    if (currentUid != null) {
                        Timber.d("Auth state: AUTHENTICATED, User: %s", currentUid);
                        showSymbolWatchlist();
                        homeViewModel.loadWatchlist(currentUid);
                        homeViewModel.connectToWatchlistWebSocket(currentUid);
                        // Visibility is handled by watchlist observer based on content and auth state
                    } else { // Should ideally not happen if AuthState is AUTHENTICATED
                        Timber.w("Auth state: AUTHENTICATED, but UID is null!");
                        showNoSymbolWatchlist(); // Fallback
                        homeViewModel.disconnectWebSocket();
                    }
                    break;
                case UNAUTHENTICATED:
                case ERROR: // Treat ERROR as UNAUTHENTICATED for watchlist display
                    Timber.d("Auth state: %s", authState.toString());
                    showNoSymbolWatchlist();
                    watchlistAdapter.setData(Collections.emptyList()); // Clear adapter data
                    homeViewModel.disconnectWebSocket();
                    break;
            }
            // After auth state change, watchlist observer will update visibility based on new data & auth status
            updateWatchlistVisibility(homeViewModel.getWatchlist().getValue());
        });
    }

    private void showNoSymbolWatchlist() {
        binding.whenNotSignedInWatchlistLayout.setVisibility(View.VISIBLE);
        binding.symbolWatchlistRecyclerview.setVisibility(View.GONE);
        binding.emptyWatchlistLayout.setVisibility(View.GONE);

    }

    private void showSymbolWatchlist() {
        binding.whenNotSignedInWatchlistLayout.setVisibility(View.GONE);
        binding.symbolWatchlistRecyclerview.setVisibility(View.VISIBLE);
    }

    private void setupRecyclerViews() {
        // Searched Cryptos List
        binding.searchedCryptosList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.searchedCryptosList.setAdapter(searchedSymbolAdapter);
        binding.searchedCryptosList.addItemDecoration(new SpacebetweenItems(30, 1.4, ContextCompat.getColor(requireContext(), R.color.searchbar_colour)));
        binding.searchedCryptosList.setEdgeEffectFactory(new BounceEdgeEffectFactory(requireContext()));
        binding.searchedCryptosList.setNestedScrollingEnabled(false); // Important if inside ScrollView

        // Watchlist RecyclerView
        binding.symbolWatchlistRecyclerview.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.symbolWatchlistRecyclerview.setAdapter(watchlistAdapter);
        binding.symbolWatchlistRecyclerview.addItemDecoration(new SpacebetweenItems(30, 1.4, ContextCompat.getColor(requireContext(), R.color.searchbar_colour)));
        binding.symbolWatchlistRecyclerview.setEdgeEffectFactory(new BounceEdgeEffectFactory(requireContext()));
        binding.symbolWatchlistRecyclerview.setNestedScrollingEnabled(false); // Important if inside ScrollView
    }

    private void initializeAdapters() {
        OnWatchlistActionListener commonWatchlistListener = new OnWatchlistActionListener() {
            @Override
            public void onAddToWatchlist(String ignoredUserId, Symbol symbol, String source)   { // userId from adapter is ignored
                String currentUid = getCurrentUserId();
                if (currentUid != null && symbol != null) {
                    homeViewModel.addToWatchlist(currentUid, symbol, source);
                } else {
                    Toast.makeText(getContext(), "Please sign in to modify watchlist.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onRemoveFromWatchlist(String ignoredUserId, String symbolTicker) { // userId from adapter is ignored
                String currentUid = getCurrentUserId();
                if (currentUid != null && symbolTicker != null) {
                    homeViewModel.removeFromWatchlist(currentUid, symbolTicker);
                } else {
                    Toast.makeText(getContext(), "Please sign in to modify watchlist.", Toast.LENGTH_SHORT).show();
                }
            }
        };

        // Assuming homeViewModel is an instance of HomeViewModel
        searchedSymbolAdapter = new SymbolAdapter(
                getContext(),
                searchedSymbolList,
                true,
                getCurrentUserId(),
                commonWatchlistListener,
                homeViewModel::onSymbolClicked  // Adding the OnSymbolClickListener
        );

        watchlistAdapter = new SymbolAdapter(
                getContext(),
                new ArrayList<>(),
                false,
                getCurrentUserId(),
                commonWatchlistListener,
                homeViewModel::onSymbolClicked  // Adding the OnSymbolClickListener
        );
    }

    private String getCurrentUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return (user != null) ? user.getUid() : null;
    }

    private void updateWatchlistVisibility(List<Symbol> watchlistSymbols) {
        if (binding == null) return;
        boolean isSignedIn = (authViewModel.getAuthState().getValue() == AuthViewModel.AuthState.AUTHENTICATED && getCurrentUserId() != null);
        Log.d("HomeTabFragment", "updateWatchlistVisibility: isSignedIn=" + isSignedIn + ", watchlistSymbols=" + (watchlistSymbols != null ? watchlistSymbols.size() : "null"));

        if (watchlistSymbols == null) {
            // Data is still loading, do not update empty state or RecyclerView visibility here
            return;
        }

        if (isSignedIn) {
            binding.whenNotSignedInWatchlistLayout.setVisibility(View.GONE);
            if (watchlistSymbols.isEmpty()) {
                // Show empty state only after data is loaded and it's empty
                binding.emptyWatchlistLayout.setVisibility(View.VISIBLE);
                binding.symbolWatchlistRecyclerview.setVisibility(View.GONE);
                binding.homeProgressBar.setVisibility(View.GONE); // Ensure progress bar is hidden
            } else {
                // Show RecyclerView with data
                binding.emptyWatchlistLayout.setVisibility(View.GONE);
                binding.symbolWatchlistRecyclerview.setVisibility(View.VISIBLE);
            }
        } else {
            showNoSymbolWatchlist();
        }
    }

    private void showErrorToast(String message) {
        if (getContext() != null && message != null && !message.isEmpty()) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        String currentUid = getCurrentUserId();
        // Check if the user is signed in.
        if (authViewModel.getAuthState().getValue() == AuthViewModel.AuthState.AUTHENTICATED && currentUid != null) {
            // Always refresh the watchlist when the fragment becomes active.
            // This ensures data is fresh after returning from the symbol detail activity or elsewhere.
            Timber.d("HomeTabFragment resumed, reloading watchlist for user %s", currentUid);
            homeViewModel.loadWatchlist(currentUid);

            // The call below is also inside loadWatchlist, but calling it here ensures the
            // websocket connects even if loadWatchlist is busy. It's a safe redundancy.
            homeViewModel.connectToWatchlistWebSocketIfNeeded(currentUid);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // FIXED: Only disconnect if the fragment is being permanently destroyed
        // Not just when the view is being recreated
        if (getActivity() != null && getActivity().isFinishing()) {
            homeViewModel.disconnectWebSocket();
        }
        binding = null;
    }
}