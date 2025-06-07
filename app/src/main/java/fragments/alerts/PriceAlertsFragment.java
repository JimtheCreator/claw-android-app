package fragments.alerts;

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

import com.claw.ai.databinding.FragmentPriceAlertsBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;

import adapters.PriceAlertsAdapter;
import viewmodels.alerts.PriceAlertsViewModel;

public class PriceAlertsFragment extends Fragment {
    private FragmentPriceAlertsBinding binding;
    private PriceAlertsViewModel viewModel;
    private PriceAlertsAdapter adapter;
    private String userId;
    private boolean isInitialized = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(PriceAlertsViewModel.class);

        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        userId = firebaseUser != null ? firebaseUser.getUid() : null;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentPriceAlertsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new PriceAlertsAdapter(alert -> viewModel.cancelAlert(userId, alert.getId()));
        binding.alertsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.alertsRecyclerView.setAdapter(adapter);

        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            if (userId != null) {
                viewModel.refreshAlerts(userId);
            } else {
                binding.swipeRefreshLayout.setRefreshing(false);
            }
        });

        setupObservers();

        // Only fetch data on first initialization
        if (!isInitialized && userId != null && viewModel.getAlerts().getValue() == null) {
            viewModel.fetchActiveAlerts(userId);
            isInitialized = true;
        }
    }

    private void setupObservers() {
        viewModel.getAlerts().observe(getViewLifecycleOwner(), alerts -> {
            adapter.setAlerts(alerts != null ? alerts : new ArrayList<>());

            if (alerts != null && !alerts.isEmpty()) {
                binding.noAlertsLayout.setVisibility(View.GONE);
                binding.alertsRecyclerView.setVisibility(View.VISIBLE);
            } else {
                binding.noAlertsLayout.setVisibility(View.VISIBLE);
                binding.alertsRecyclerView.setVisibility(View.GONE);
            }
        });

        viewModel.getMessages().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (binding != null && !binding.swipeRefreshLayout.isRefreshing()) {
                binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            }
            if (!isLoading && binding != null) {
                binding.swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        // Fragment is no longer visible
    }

    // Method to refresh data when needed (can be called from parent)
    public void refreshData() {
        if (userId != null) {
            viewModel.refreshAlertsInBackground(userId);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}