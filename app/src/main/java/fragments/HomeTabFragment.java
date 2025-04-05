package fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.TransitionManager;

import com.claw.ai.databinding.FragmentHomeTabBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.util.ArrayList;
import java.util.List;

import adapters.CryptosAdapter;
import animations.BounceEdgeEffectFactory;
import models.Symbol;
import timber.log.Timber;
import utils.DateUtils;
import utils.KeyboardQuickFunctions;
import viewmodels.HomeViewModel;

public class HomeTabFragment extends Fragment {

    private FragmentHomeTabBinding binding;
    private boolean isSearchExpanded = false;
    private BottomSheetBehavior<NestedScrollView> bottomSheetBehavior;
    HomeViewModel homeViewModel;
    List<Symbol> symbolList;
    CryptosAdapter cryptosAdapter;

    DisplayMetrics metrics;

    // Add these variables
    private int currentPopularPage = 1;
    private boolean isLoadingPopular = false;
    private String currentQuery = "";


    // Lifecycle Methods
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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
        setupPopularCryptosList();  // Add this
        setupSearchedCryptoList();  // Add this
        setupObservers();
    }

    private void initializeViews() {
        symbolList = new ArrayList<>();
        cryptosAdapter = new CryptosAdapter(requireContext(), symbolList);
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        binding.dateText.setText(DateUtils.getFormattedDate());
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
        bottomSheetBehavior.setHalfExpandedRatio(0.4f);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);

        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                Timber.tag("BottomSheet").d("State: %s", newState);
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
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
        TransitionManager.beginDelayedTransition(binding.main);
        binding.doneSearch.setVisibility(View.GONE);
    }

    private void searchBarStateWhenClicked() {
        binding.frameSearchBox.setVisibility(View.VISIBLE);
        binding.dummySearchbox.setVisibility(View.GONE);
        binding.doneSearch.setVisibility(View.VISIBLE);
        binding.searchBox.requestFocus();
        KeyboardQuickFunctions.showKeyboard(binding.searchBox, requireContext());
    }

    // Click Listeners
    private void setupClickListeners() {
        binding.searchBar.setOnClickListener(v -> {
            bottomSheetBehavior.setPeekHeight(0);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            animateSearchState(true);
            searchBarStateWhenClicked();
        });

        binding.doneSearch.setOnClickListener(v -> {
            KeyboardQuickFunctions.closeKeyboard(binding.searchBox, requireContext());
            searchBarStateBeforeClicked();
            binding.searchBox.setText("");

            if (isSearchExpanded) {
                binding.getRoot().post(() -> {
                    animateSearchState(false);
                    bottomSheetBehavior.setPeekHeight((int) (metrics.heightPixels * 0.1));
                    bottomSheetBehavior.setFitToContents(false);
                    bottomSheetBehavior.setHalfExpandedRatio(0.4f);
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
                });
            }
        });
    }

    // Back Press Handling
    private void setupBackPressHandler() {
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        KeyboardQuickFunctions.closeKeyboard(binding.searchBox, requireContext());
                        searchBarStateBeforeClicked();
                        binding.searchBox.setText("");

                        if (isSearchExpanded) {
                            binding.getRoot().post(() -> {
                                animateSearchState(false);
                                bottomSheetBehavior.setPeekHeight((int) (metrics.heightPixels * 0.1));
                                bottomSheetBehavior.setFitToContents(false);
                                bottomSheetBehavior.setHalfExpandedRatio(0.4f);
                                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
                            });
                        }
                    }
                }
        );
    }

    // Animation Utilities
    private void animateSearchState(boolean expand) {
        float headerAlpha = expand ? 0f : 1f;
        float translation = expand ? -binding.headerContainer.getHeight() : 0f;

        binding.headerContainer.animate()
                .alpha(headerAlpha)
                .translationY(translation)
                .setDuration(300)
                .start();

        binding.mainpagelayout.animate()
                .translationY(translation)
                .setDuration(300)
                .start();

        isSearchExpanded = expand;
    }

    // Helper Methods
    private DisplayMetrics getDisplayMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        requireActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
        return metrics;
    }


    /**
     * Sets up the RecyclerView that shows a list of popular cryptocurrencies.
     *
     * <p>This configuration uses a {@link LinearLayoutManager} for vertical scrolling,
     * a {@link CryptosAdapter} to bind the data to views, and applies a
     * {@link BounceEdgeEffectFactory} to enhance the overscroll behavior with
     * a spring-like bounce effect.</p>
     *
     * @see CryptosAdapter
     * @see BounceEdgeEffectFactory
     */
    // Update setupPopularCryptosList
    private void setupPopularCryptosList() {
        binding.popularCryptosList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.popularCryptosList.setHasFixedSize(true);
        binding.popularCryptosList.setAdapter(cryptosAdapter);
        binding.popularCryptosList.setEdgeEffectFactory(new BounceEdgeEffectFactory(requireContext()));

        binding.popularCryptosList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                assert layoutManager != null;
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                if (!isLoadingPopular && (visibleItemCount + firstVisibleItemPosition) >= totalItemCount) {
                    homeViewModel.loadNextPage();
                }
            }
        });

        homeViewModel.loadTopCryptos(1);
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
    private void setupSearchedCryptoList(){
        binding.searchedCryptosList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.searchedCryptosList.setHasFixedSize(true);
        binding.searchedCryptosList.setAdapter(cryptosAdapter);
        binding.searchedCryptosList.setEdgeEffectFactory(new BounceEdgeEffectFactory(requireContext()));

        binding.searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim();
                if (query.length() >= 2) {
                    homeViewModel.searchCryptos(query, 20);
                }
            }
        });
    }


    // Update setupObservers
    private void setupObservers() {
        // In HomeTabFragment's setupObservers()
        homeViewModel.getCryptoList().observe(getViewLifecycleOwner(), symbols -> {
            Timber.d("Received %d symbols", symbols.size()); // Log data size
            if (!symbols.isEmpty()) {
                symbolList.clear();
                symbolList.addAll(symbols);
                cryptosAdapter.setData(symbolList);
            }
        });
        homeViewModel.getErrorMessage().observe(getViewLifecycleOwner(), this::showErrorToast);
    }


    private void showErrorToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

}