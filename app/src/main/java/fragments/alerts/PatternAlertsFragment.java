package fragments.alerts;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.claw.ai.R;
import com.claw.ai.databinding.FragmentPatternAlertsBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

import adapters.PatternAlertsAdapter;
import bottomsheets.patterns.PatternAlertsBottomSheetFragment;
import deco.GridSpacingItemDecoration;
import models.PatternAlert;
import viewmodels.alerts.PatternAlertViewModel;
import viewmodels.SharedRefreshViewModel;
import viewmodels.google_login.AuthViewModel;

public class PatternAlertsFragment extends Fragment implements PatternAlertsAdapter.OnDeleteClickListener {

    FragmentPatternAlertsBinding binding;
    private PatternAlertViewModel viewModel;
    private PatternAlertsAdapter adapter;
    private SharedRefreshViewModel sharedRefreshViewModel;
    private AuthViewModel authViewModel;
    private Observer<List<PatternAlert>> patternAlertsObserver;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        authViewModel.initialize(requireActivity(), getString(R.string.web_client_id));

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
        setupObservers();
    }

    private void setupSwipeToRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            viewModel.refreshAlerts(getCurrentUserId());
        });
    }

    private void setupRecyclerView() {
        adapter = new PatternAlertsAdapter(this);
        int spacingInPixels = getResources().getDimensionPixelSize(R.dimen.recycler_item_spacing);

        binding.availablePatternAlertsLayout.recyclerViewPatternAlerts.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.availablePatternAlertsLayout.recyclerViewPatternAlerts.addItemDecoration(
                new GridSpacingItemDecoration(2, spacingInPixels, false)
        );
        binding.availablePatternAlertsLayout.recyclerViewPatternAlerts.setAdapter(adapter);
    }

    private void setupClickListeners() {
        binding.availablePatternAlertsLayout.createPatternAlert.setOnClickListener(v -> openCreateAlertSheet());
        binding.emptyStateLayout.findViewById(R.id.create_new_alert_empty).setOnClickListener(v -> openCreateAlertSheet());
    }

    private void setupObservers() {
        authViewModel.getAuthState().observe(getViewLifecycleOwner(), authState -> {
            switch (authState) {
                case AUTHENTICATED:
                    binding.loginStateLayout.setVisibility(View.GONE);
                    viewModel.refreshAlerts(getCurrentUserId());
                    if (patternAlertsObserver == null) {
                        patternAlertsObserver = this::updateUiWithAlerts;
                        viewModel.getPatternAlerts().observe(getViewLifecycleOwner(), patternAlertsObserver);
                    }
                    break;

                case UNAUTHENTICATED:
                case ERROR:
                    viewModel.clearAlerts();
                    if (patternAlertsObserver != null) {
                        viewModel.getPatternAlerts().removeObserver(patternAlertsObserver);
                        patternAlertsObserver = null;
                    }
                    binding.loginStateLayout.setVisibility(View.VISIBLE);
                    binding.swipeRefreshLayout.setVisibility(View.GONE);
                    binding.emptyStateLayout.setVisibility(View.GONE);
                    binding.progressBar.setVisibility(View.GONE);
                    binding.swipeRefreshLayout.setRefreshing(false);
                    adapter.setAlerts(new ArrayList<>());
                    break;

                case LOADING:
                    binding.progressBar.setVisibility(View.VISIBLE);
                    binding.loginStateLayout.setVisibility(View.GONE);
                    binding.swipeRefreshLayout.setVisibility(View.GONE);
                    binding.emptyStateLayout.setVisibility(View.GONE);
                    break;
            }
        });

        viewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> {
            if (authViewModel.getAuthState().getValue() != AuthViewModel.AuthState.AUTHENTICATED) {
                return;
            }

            if (isLoading) {
                if (adapter.getItemCount() == 0) {
                    binding.progressBar.setVisibility(View.VISIBLE);
                    binding.swipeRefreshLayout.setVisibility(View.GONE);
                    binding.emptyStateLayout.setVisibility(View.GONE);
                } else {
                    binding.swipeRefreshLayout.setRefreshing(true);
                }
            } else {
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefreshLayout.setRefreshing(false);
            }
        });

        viewModel.error.observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
            }
        });

        sharedRefreshViewModel.patternAlertsRefreshRequest.observe(getViewLifecycleOwner(), event -> {
            if (event.getContentIfNotHandled() != null) {
                refreshDataFromNotification();
            }
        });
    }

    private void refreshDataFromNotification() {
        String userId = getCurrentUserId();
        if (userId != null) {
            binding.availablePatternAlertsLayout.recyclerViewPatternAlerts.scrollToPosition(0);
            viewModel.refreshAlerts(userId);
        }
    }

    private void deleteAlert(String alertId) {
        String userId = getCurrentUserId();
        if (userId != null) {
            viewModel.deletePatternAlert(alertId, userId).observe(getViewLifecycleOwner(), success -> {
                if (success) {
                    Toast.makeText(getContext(), "Alert deleted", Toast.LENGTH_SHORT).show();
                    viewModel.refreshAlerts(userId);
                }
            });
        }
    }

    private void updateUiWithAlerts(List<PatternAlert> alerts) {
        boolean hasAlerts = alerts != null && !alerts.isEmpty();

        binding.emptyStateLayout.setVisibility(hasAlerts ? View.GONE : View.VISIBLE);
        binding.swipeRefreshLayout.setVisibility(hasAlerts ? View.VISIBLE : View.GONE);
        binding.availablePatternAlertsLayout.patternAlertCountFrame.setVisibility(hasAlerts ? View.VISIBLE : View.GONE);

        if (hasAlerts) {
            binding.availablePatternAlertsLayout.totalNumberOfActiveAlerts.setText(String.valueOf(alerts.size()));
        }

        adapter.setAlerts(alerts != null ? alerts : new ArrayList<>());
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
                .setPositiveButton("Delete", (dialog, which) -> deleteAlert(alert.getId()))
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