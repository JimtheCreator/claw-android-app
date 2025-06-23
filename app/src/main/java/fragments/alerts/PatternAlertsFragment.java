package fragments.alerts;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.claw.ai.R;
import com.claw.ai.databinding.FragmentPatternAlertsBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

import adapters.PatternAlertsAdapter;
import bottomsheets.patterns.PatternAlertsBottomSheetFragment;
import models.PatternAlert;
import viewmodels.alerts.PatternAlertViewModel;
import viewmodels.SharedRefreshViewModel;

public class PatternAlertsFragment extends Fragment implements PatternAlertsAdapter.OnDeleteClickListener {

    FragmentPatternAlertsBinding binding;
    private PatternAlertViewModel viewModel;
    private PatternAlertsAdapter adapter;
    private SharedRefreshViewModel sharedRefreshViewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize the shared ViewModel
        sharedRefreshViewModel = new ViewModelProvider(requireActivity()).get(SharedRefreshViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentPatternAlertsBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(PatternAlertViewModel.class);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupRecyclerView();
        setupClickListeners();
        setupSwipeToRefresh();
        setupObservers(); // It's good practice to set up observers before triggering data loads.

        // Initial data fetch: This triggers the refresh in the ViewModel.
        viewModel.refreshAlerts(getCurrentUserId());
    }

    private void setupSwipeToRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            viewModel.refreshAlerts(getCurrentUserId());
        });
    }

    private void setupRecyclerView() {
        adapter = new PatternAlertsAdapter(this);
        binding.recyclerViewPatternAlerts.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewPatternAlerts.setAdapter(adapter);
    }

    private void setupClickListeners() {
        binding.fabCreateAlert.setOnClickListener(v -> openCreateAlertSheet());
        binding.emptyStateLayout.findViewById(R.id.create_new_alert_empty).setOnClickListener(v -> openCreateAlertSheet());
    }

    private void setupObservers() {
        // This observer now correctly handles both loading states.
        viewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> {
            if (isLoading) {
                // Check if the adapter is empty to determine the load type.
                if (adapter.getItemCount() == 0) {
                    // --- INITIAL LOAD ---
                    // Hide other views and show the central progress bar.
                    binding.progressBar.setVisibility(View.VISIBLE);
                    binding.swipeRefreshLayout.setVisibility(View.GONE);
                    binding.emptyStateLayout.setVisibility(View.GONE);
                } else {
                    // --- REFRESH ---
                    // The list is already visible, so just show the refresh indicator.
                    binding.swipeRefreshLayout.setRefreshing(true);
                }
            } else {
                // --- LOADING FINISHED ---
                // Hide both loading indicators.
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefreshLayout.setRefreshing(false);
            }
        });

        viewModel.getPatternAlerts().observe(getViewLifecycleOwner(), this::updateUiWithAlerts);

        viewModel.error.observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
            }
        });

        // NEW: Listen for pattern alerts refresh requests from FCM notifications
        sharedRefreshViewModel.patternAlertsRefreshRequest.observe(getViewLifecycleOwner(), event -> {
            if (event.getContentIfNotHandled() != null) {
                refreshDataFromNotification();
            }
        });
    }

    /**
     * Called when a pattern alert notification is received
     * This method refreshes the data and scrolls to top if needed
     */
    private void refreshDataFromNotification() {
        String userId = getCurrentUserId();
        if (userId != null) {
            // Show progress indicator and scroll to top if not already there
            LinearLayoutManager layoutManager = (LinearLayoutManager) binding.recyclerViewPatternAlerts.getLayoutManager();
            if (layoutManager != null && layoutManager.findFirstCompletelyVisibleItemPosition() > 0) {
                binding.recyclerViewPatternAlerts.scrollToPosition(0);
            }

            // Refresh the alerts
            viewModel.refreshAlerts(userId);
        }
    }

    private void deleteAlert(String alertId) {
        String userId = getCurrentUserId();
        viewModel.deletePatternAlert(alertId, userId).observe(getViewLifecycleOwner(), success -> {
            if (success) {
                Toast.makeText(getContext(), "Alert deleted", Toast.LENGTH_SHORT).show();
                // This call now correctly triggers the UI update because it updates the trigger LiveData.
                viewModel.refreshAlerts(userId);
            }
        });
    }

    /**
     * Updates the UI based on the list of alerts and triggers animations.
     */
    private void updateUiWithAlerts(List<PatternAlert> alerts) {
        boolean hasAlerts = alerts != null && !alerts.isEmpty();

        // This method is called after loading is complete.
        // The isLoading observer has already hidden the progress indicators.
        // We just decide whether to show the list or the empty state.
        if (hasAlerts) {
            binding.emptyStateLayout.setVisibility(View.GONE);
            binding.swipeRefreshLayout.setVisibility(View.VISIBLE);
            binding.fabCreateAlert.setVisibility(View.VISIBLE);
            // Get the layout manager from the RecyclerView.
            LinearLayoutManager layoutManager = (LinearLayoutManager) binding.recyclerViewPatternAlerts.getLayoutManager();

            // YOUR SUGGESTION: Only scroll if the first item isn't already visible.
            if (layoutManager != null && layoutManager.findFirstVisibleItemPosition() > 0) {
                binding.recyclerViewPatternAlerts.scrollToPosition(0);
            }
        } else {
            binding.emptyStateLayout.setVisibility(View.VISIBLE);
            binding.swipeRefreshLayout.setVisibility(View.GONE);
            binding.fabCreateAlert.setVisibility(View.GONE);
        }

        adapter.setAlerts(alerts != null ? alerts : new java.util.ArrayList<>());
    }

    private void openCreateAlertSheet() {
        PatternAlertsBottomSheetFragment sheet = PatternAlertsBottomSheetFragment.newInstance();
        sheet.show(getParentFragmentManager(), sheet.getTag());
    }

    @Override
    public void onDeleteClick(PatternAlert alert) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Alert")
                .setMessage("Are you sure you want to delete the alert for " + alert.getSymbol() + "?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteAlert(alert.getId());
                })
                .show();
    }

    private String getCurrentUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return (user != null) ? user.getUid() : null;
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
