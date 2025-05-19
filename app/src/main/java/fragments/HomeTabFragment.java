package fragments;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import com.claw.ai.R;
import com.claw.ai.databinding.FragmentHomeTabBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;


import adapters.CryptosAdapter;
import animations.BounceEdgeEffectFactory;
import animations.MotionAnimation;
import home_tabs.TabPagerAdapter;
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
    int half_expanded_state_tag = 0;
    int expanded_state_tag = 0;
    int collapsed_state_tag = 0;

    private List<Symbol> searchedSymbolList;
    private CryptosAdapter searchedCryptosAdapter;

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
    }


    private void initializeViews() {
        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        // Ensure web_client_id is correctly defined in your strings.xml
        authViewModel.initialize(requireActivity(), getString(R.string.web_client_id));

        searchedSymbolList = new ArrayList<>();

        searchedCryptosAdapter = new CryptosAdapter(requireContext(), searchedSymbolList, true);

        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
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

                    if (half_expanded_state_tag == 0){
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

    // Click Listeners
    private void setupClickListeners() {
        binding.clearKeyboardText.setOnClickListener(v ->{
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
    }

    private void handleSearchClose() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED || bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HALF_EXPANDED) {
            MotionAnimation.animateSmoothScrollToBottom(binding.scrollView);
            KeyboardQuickFunctions.closeKeyboard(binding.searchBox, requireContext());
            binding.searchBox.setText("");
            // Clear search results
            searchedSymbolList.clear();
            searchedCryptosAdapter.notifyDataSetChanged();
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
     * attaches an instance of {@link CryptosAdapter} to provide the data and views,
     * and applies a custom {@link BounceEdgeEffectFactory} to give the RecyclerView
     * an iOS-style bouncy overscroll effect.</p>
     *
     * @see CryptosAdapter
     * @see BounceEdgeEffectFactory
     */
    private void setupSearchedCryptoList() {
        int gap = 30;                // total px between items
        double thick = 1.4;          // px thickness of divider

        binding.searchedCryptosList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.searchedCryptosList.setHasFixedSize(true);
        binding.searchedCryptosList.setAdapter(searchedCryptosAdapter);
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
        searchedCryptosAdapter.notifyDataSetChanged();
    }

    // Update setupObservers to handle both lists separately
    private void setupObservers() {
        // Handle errors
        homeViewModel.getErrorMessage().observe(getViewLifecycleOwner(), this::showErrorToast);

        homeViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.searchProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        homeViewModel.getSearchResults().observe(getViewLifecycleOwner(), results -> {
            if (results == null || results.isEmpty()) {
                binding.emptySearchState.setVisibility(View.VISIBLE);
                binding.searchedCryptosList.setVisibility(View.GONE);
            } else {
                binding.emptySearchState.setVisibility(View.GONE);
                binding.searchedCryptosList.setVisibility(View.VISIBLE);
                // Update adapter data
                searchedCryptosAdapter.setData(results);
            }
        });

        authViewModel.getAuthState().observe(getViewLifecycleOwner(), authState -> {
            switch (authState) {
                case AUTHENTICATED:
                    if (isAdded() && !isStateSaved()) {
                        showSymbolWatchlist();
                    }
                    break;
                case UNAUTHENTICATED:
                    showNoSymbolWatchlist();
                    break;
                case ERROR:
                    // Error message is handled by its own observer.
                    // Ensure UI is in a reasonable state, e.g., show login page if not authenticated.
                    if (!authViewModel.isUserSignedIn()) {
                        showNoSymbolWatchlist();
                    }
                    break;
            }
        });

        authViewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
            }
        });

    }

    private void showNoSymbolWatchlist() {
        binding.whenNotSignedInWatchlistLayout.setVisibility(View.VISIBLE);
        binding.symbolWatchlistRecyclerview.setVisibility(View.GONE);
    }

    private void showSymbolWatchlist() {
        binding.whenNotSignedInWatchlistLayout.setVisibility(View.GONE);
        binding.symbolWatchlistRecyclerview.setVisibility(View.VISIBLE);

        loadSymbolWatchListFromServer();
    }

    private void loadSymbolWatchListFromServer() {

    }

    private void showErrorToast(String message) {
        if (getContext() != null && message != null && !message.isEmpty()) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
}