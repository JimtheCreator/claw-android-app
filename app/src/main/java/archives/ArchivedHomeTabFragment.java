package archives;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.claw.ai.databinding.FragmentArchivedHomeTabBinding;
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


/**
 * This page is archived for the sole reason it has a tabLayout
 *
 * <p>It is not fully developed and these are what are available: The search works to the point you can only view cryptos.
 * The TABLAYOUT DOES DISPLAY ANYTHING AT THIS MOMENT.
 * Do not use this only use it if you willingly to add a changes which defeats the whole reason of archiving it.
 * Reason it's archived is because i intend to use it in the future, just for the TabLayout design;
 * <p>
 * <p>
 * NEW PAGE
 *
 * @see fragments.HomeTabFragment
 */
public class ArchivedHomeTabFragment extends Fragment {

    // Initialize a variable to store the previous slideOffset
    private float lastSlideOffset = -1;
    boolean isBottomSheetExpanded = false;
    private FragmentArchivedHomeTabBinding binding;
    private boolean isSearchExpanded = false;
    private BottomSheetBehavior<View> bottomSheetBehavior;
    private HomeViewModel homeViewModel;
    private List<Symbol> popularSymbolList;
    private List<Symbol> searchedSymbolList;
    private CryptosAdapter popularCryptosAdapter;
    private CryptosAdapter searchedCryptosAdapter;

    private DisplayMetrics metrics;

    // Add these variables
    private int currentPopularPage = 1;
    private boolean isLoadingPopular = false;
    private String currentQuery = "";

    private final String[] tabTitles = {"Trending", "Top Gainers", "Top Losers", "New"};

    // Lifecycle Methods
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentArchivedHomeTabBinding.inflate(inflater, container, false);
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
//        setupPopularCryptosList();
        setupSearchedCryptoList();
        setupObservers();
    }

    private void initializeViews() {
        binding.viewPager.setAdapter(new TabPagerAdapter(requireActivity()));

        new TabLayoutMediator(binding.tabLayout, binding.viewPager,
                (tab, position) -> tab.setText(tabTitles[position])).attach();

        popularSymbolList = new ArrayList<>();
        searchedSymbolList = new ArrayList<>();

        popularCryptosAdapter = new CryptosAdapter(requireContext(), popularSymbolList);
        searchedCryptosAdapter = new CryptosAdapter(requireContext(), searchedSymbolList);

        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        binding.dateText.setText(DateUtils.getFormattedDate());

        // Initially hide searched list and show the empty state
        binding.searchedCryptosList.setVisibility(View.GONE);
        binding.emptySearchState.setVisibility(View.GONE);

        binding.searchBox.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                KeyboardQuickFunctions.closeKeyboard(textView, requireContext());
                String query = textView.getText().toString().trim();

                // 🔍 Perform your search logic here
                performSearch(query);

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
                    isBottomSheetExpanded = false;
                    bottomSheetBehavior.setDraggable(true);
                    searchBarStateBeforeClicked();
                    KeyboardQuickFunctions.closeKeyboard(binding.searchBox, requireContext());
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheetBehavior.setDraggable(false);
                    isBottomSheetExpanded = true;
                    imeKeyboardSearch(bottomSheet);
                } else if (newState == BottomSheetBehavior.STATE_HALF_EXPANDED) {
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

//            binding.getRoot().post(() -> {
//                bottomSheetBehavior.setPeekHeight((int) (metrics.heightPixels * 0.1));
//                bottomSheetBehavior.setFitToContents(false);
//                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
//            });
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
        binding.searchedCryptosList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.searchedCryptosList.setHasFixedSize(true);
        binding.searchedCryptosList.setAdapter(searchedCryptosAdapter);
        binding.searchedCryptosList.addItemDecoration(new SpacebetweenItems(15));
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
                currentQuery = s.toString().trim();

                performSearch(currentQuery);
            }
        });
    }

    private void performSearch(String query) {
        if (query.length() >= 3) {
            homeViewModel.searchCryptos(query, 20);
            binding.emptySearchState.setVisibility(View.GONE);
        } else {
            // Clear search results if query is too short
            searchedSymbolList.clear();
            searchedCryptosAdapter.notifyDataSetChanged();

            if (query.isEmpty()) {
                binding.emptySearchState.setVisibility(View.GONE);
            } else {
                binding.emptySearchState.setVisibility(View.VISIBLE);
            }
        }
    }


    // Update setupObservers to handle both lists separately
    private void setupObservers() {
        // Observe popular cryptos list
//        homeViewModel.getCryptoList().observe(getViewLifecycleOwner(), symbols -> {
//            Timber.d("Received %d popular symbols", symbols.size());
//            if (!symbols.isEmpty()) {
//                isLoadingPopular = false;
//                popularSymbolList.clear();
//                popularSymbolList.addAll(symbols);
//
//                popularCryptosAdapter.setData(popularSymbolList);
//
//
//                // Show loading indicator based on state
//                binding.popularLoadingIndicator.setVisibility(View.GONE);
//            }
//        });


        // Observe search results
        homeViewModel.getSearchResults().observe(getViewLifecycleOwner(), symbols -> {
            Timber.d("Received %d search results", symbols.size());
            searchedSymbolList.clear();

            if (!symbols.isEmpty()) {
                searchedSymbolList.addAll(symbols);
                binding.emptySearchState.setVisibility(View.GONE);
            } else if (currentQuery.length() >= 2) {
                // Show empty state if no results for valid search
                binding.emptySearchState.setVisibility(View.VISIBLE);
            }

            searchedCryptosAdapter.setData(searchedSymbolList);
        });

        // Handle errors
        homeViewModel.getErrorMessage().observe(getViewLifecycleOwner(), this::showErrorToast);

        // Handle loading state
//        homeViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
//            binding.popularLoadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
//        });
    }

    private void showErrorToast(String message) {
        if (getContext() != null && message != null && !message.isEmpty()) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
}