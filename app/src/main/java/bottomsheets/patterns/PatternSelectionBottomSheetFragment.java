package bottomsheets.patterns;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.claw.ai.databinding.FragmentPatternSelectionBottomSheetBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;

import adapters.PatternSelectionAdapter;
import models.Pattern;
import viewmodels.alerts.PatternAlertViewModel;

public class PatternSelectionBottomSheetFragment extends BottomSheetDialogFragment {

    private FragmentPatternSelectionBottomSheetBinding binding;
    private PatternAlertViewModel viewModel;
    private PatternSelectionAdapter adapter;

    public static PatternSelectionBottomSheetFragment newInstance() {
        Bundle args = new Bundle();
        PatternSelectionBottomSheetFragment fragment = new PatternSelectionBottomSheetFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPatternSelectionBottomSheetBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(PatternAlertViewModel.class);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupRecyclerView();
        setupSearchFunctionality();
        setupClickListeners();
        observePatterns();
    }

    private void setupRecyclerView() {
        adapter = new PatternSelectionAdapter(
                new ArrayList<>(),
                this::onPatternSelected,
                this::onLoadMore
        );

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        binding.patternsRecyclerView.setLayoutManager(layoutManager);
        binding.patternsRecyclerView.setAdapter(adapter);

        // Add scroll listener for pagination
        binding.patternsRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (dy > 0) { // Only trigger on downward scroll
                    LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (layoutManager != null) {
                        int visibleItemCount = layoutManager.getChildCount();
                        int totalItemCount = layoutManager.getItemCount();
                        int pastVisibleItems = layoutManager.findFirstVisibleItemPosition();

                        // Trigger load more when near the end (5 items before end)
                        if ((visibleItemCount + pastVisibleItems) >= (totalItemCount - 5)) {
                            onLoadMore();
                        }
                    }
                }
            }
        });
    }

    private void setupSearchFunctionality() {
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.searchPatterns(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setupClickListeners() {
        binding.closeButton.setOnClickListener(v -> dismiss());
        binding.cancelButton.setOnClickListener(v -> dismiss());
    }

    private void observePatterns() {
        // Show loading initially
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.patternsRecyclerView.setVisibility(View.GONE);

        // Observe paginated patterns
        viewModel.getPaginatedPatterns().observe(getViewLifecycleOwner(), patterns -> {
            binding.progressBar.setVisibility(View.GONE);
            binding.patternsRecyclerView.setVisibility(View.VISIBLE);

            if (patterns != null) {
                adapter.updatePatterns(patterns);
                adapter.setHasMoreData(viewModel.hasMorePages());
            }
        });

        // Observe loading more state
        viewModel.isLoadingMore().observe(getViewLifecycleOwner(), isLoading -> {
            if (isLoading != null) {
                adapter.setLoading(isLoading);
            }
        });
    }

    private void onLoadMore() {
        // Prevent multiple simultaneous load requests
        if (viewModel.isLoadingMore().getValue() == Boolean.TRUE || !viewModel.hasMorePages()) {
            return;
        }
        viewModel.loadMorePatterns();
    }

    private void onPatternSelected(Pattern pattern) {
        // Send result back to parent fragment
        Bundle result = new Bundle();
        result.putString("selected_pattern_name", pattern.displayName);
        getParentFragmentManager().setFragmentResult("pattern_selection_key", result);

        dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        View view = getView();
        if (view != null) {
            View parent = (View) view.getParent();
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(parent);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
            ViewGroup.LayoutParams layoutParams = parent.getLayoutParams();
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            parent.setLayoutParams(layoutParams);
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}