package fragments;

import android.content.Context;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.transition.TransitionManager;

import com.claw.ai.databinding.FragmentHomeTabBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

public class HomeTabFragment extends Fragment {

    private FragmentHomeTabBinding binding;
    private boolean isSearchExpanded = false;
    private BottomSheetBehavior<NestedScrollView> bottomSheetBehavior;

    DisplayMetrics metrics;

    // Lifecycle Methods
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeTabBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupBottomSheet();
        initializeSearch();
        setupClickListeners();
        setupBackPressHandler();
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
                Log.d("BottomSheet", "State: " + newState);
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                Log.d("BottomSheet", "Slide: " + slideOffset);
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
        showKeyboard();
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
            closeKeyboard();
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
                        closeKeyboard();
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

        binding.searchBar.animate()
                .translationY(translation)
                .setDuration(300)
                .start();

        isSearchExpanded = expand;
    }

    // Keyboard Utilities
    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(binding.searchBox, InputMethodManager.SHOW_IMPLICIT);
    }

    private void closeKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(binding.searchBox.getWindowToken(), 0);
    }

    // Helper Methods
    private DisplayMetrics getDisplayMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        requireActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
        return metrics;
    }
}