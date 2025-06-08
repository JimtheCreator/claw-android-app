package fragments.alerts;

import android.os.Bundle;
import android.util.Log;
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
import com.claw.ai.databinding.FragmentPriceAlertsBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import adapters.PriceAlertsAdapter;
import models.PriceAlert;
import timber.log.Timber;
import viewmodels.alerts.PriceAlertsViewModel;
import viewmodels.google_login.AuthViewModel;

public class PriceAlertsFragment extends Fragment {
    private FragmentPriceAlertsBinding binding;
    private PriceAlertsViewModel viewModel;
    private PriceAlertsAdapter adapter;
    private String userId;
    private boolean isInitialized = false;
    private AuthViewModel authViewModel;

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

        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        // Ensure web_client_id is correctly defined in your strings.xml
        authViewModel.initialize(requireActivity(), getString(R.string.web_client_id));

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

        authViewModel.getAuthState().observe(getViewLifecycleOwner(), authState -> {
            if (binding == null) return;
            String currentUid = getCurrentUserId();
            switch (authState) {
                case AUTHENTICATED:
                    if (currentUid != null) {
                        Timber.d("Auth state: AUTHENTICATED, User: %s", currentUid);
                        // After auth state change, watchlist observer will update visibility based on new data & auth status
                        updateUIVisibility(viewModel.getAlerts().getValue());
                        // Visibility is handled by watchlist observer based on content and auth state
                    } else { // Should ideally not happen if AuthState is AUTHENTICATED
                        Timber.w("Auth state: AUTHENTICATED, but UID is null!");
                        adjustWidgets();
                    }
                    break;
                case UNAUTHENTICATED:
                    Timber.d("Auth state: UNAUTHENTICATED");
                    adjustWidgets();
                    break;
                case ERROR: // Treat ERROR as UNAUTHENTICATED for watchlist display
                    Timber.d("Auth state: %s", authState.toString());
                    Toast.makeText(requireContext(), authState.toString(), Toast.LENGTH_SHORT).show();
                    break;
            }

            // After auth state change, watchlist observer will update visibility based on new data & auth status
            updateUIVisibility(viewModel.getAlerts().getValue());
        });
    }

    private void updateUIVisibility(List<PriceAlert> value) {
        if (binding == null) return;
        boolean isSignedIn = (authViewModel.getAuthState().getValue() == AuthViewModel.AuthState.AUTHENTICATED && getCurrentUserId() != null);
        Log.d("HomeTabFragment", "updateWatchlistVisibility: isSignedIn=" + isSignedIn + ", watchlistSymbols=" + (value != null ? value.size() : "null"));

        if (value == null) {
            // Data is still loading, do not update empty state or RecyclerView visibility here
            return;
        }

        if (isSignedIn) {
            binding.signInLayout.setVisibility(View.GONE);
            if (!value.isEmpty()) {
                binding.noAlertsLayout.setVisibility(View.GONE);
                binding.alertsRecyclerView.setVisibility(View.VISIBLE);
            } else {
                binding.noAlertsLayout.setVisibility(View.VISIBLE);
                binding.alertsRecyclerView.setVisibility(View.GONE);
            }
        } else {
            adjustWidgets();
        }
    }

    private void adjustWidgets() {
        binding.signInLayout.setVisibility(View.VISIBLE);
        binding.noAlertsLayout.setVisibility(View.GONE);
        binding.alertsRecyclerView.setVisibility(View.GONE);
        binding.progressBar.setVisibility(View.GONE);
    }

    private String getCurrentUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return (user != null) ? user.getUid() : null;
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