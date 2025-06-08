package fragments.alerts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.claw.ai.R;
import com.claw.ai.databinding.FragmentPriceAlertsBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
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
    private static final String TAG = "PriceAlertsFragment";

    // 1. Declare the BroadcastReceiver
    private BroadcastReceiver refreshReceiver;
    // Define the action suffix as a constant to avoid typos
    private static final String REFRESH_ACTION_SUFFIX = ".ACTION_REFRESH_ALERTS";
    private String refreshAction;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Construct the dynamic action string
        refreshAction = requireContext().getPackageName() + REFRESH_ACTION_SUFFIX;

        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        // Ensure web_client_id is correctly defined in your strings.xml
        authViewModel.initialize(requireActivity(), getString(R.string.web_client_id));

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
        setupBroadcastReceiver();

        // Only fetch data on first initialization
        if (!isInitialized && userId != null && viewModel.getAlerts().getValue() == null) {
            viewModel.fetchActiveAlerts(userId);
            isInitialized = true;
        }
    }

    // 2. Initialize the receiver and define its behavior
    private void setupBroadcastReceiver() {
        refreshReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Check if the received broadcast is the one we want
                if (refreshAction.equals(intent.getAction())) {
                    Timber.d("Received broadcast to refresh alerts. Refreshing...");
                    if (userId != null) {
                        // Use the silent refresh so the user doesn't see a loading spinner
                        viewModel.refreshAlertsInBackground(userId);
                    }
                }
            }
        };
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
                        Log.d(TAG, "AUTHENTICATED");
                        // After auth state change, watchlist observer will update visibility based on new data & auth status
                        updateUIVisibility(viewModel.getAlerts().getValue());
                        // Visibility is handled by watchlist observer based on content and auth state
                    } else { // Should ideally not happen if AuthState is AUTHENTICATED
                        Log.d(TAG, "AUTHENTICATED: But NULL");
                        Timber.w("Auth state: AUTHENTICATED, but UID is null!");
                        adjustWidgets();
                    }
                    break;
                case UNAUTHENTICATED:
                    Log.d(TAG, "UNAUTHENTICATED");
                    adjustWidgets();
                    break;
                case ERROR: // Treat ERROR as UNAUTHENTICATED for watchlist display
                    Log.d(TAG, "ERROR");
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

        if (isSignedIn) {
            binding.signInLayout.setVisibility(View.GONE);

            if (value != null && !value.isEmpty()) {
                binding.noAlertsLayout.setVisibility(View.GONE);
                binding.alertsRecyclerView.setVisibility(View.VISIBLE);
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

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(refreshAction);
        ContextCompat.registerReceiver(requireActivity(), refreshReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onPause() {
        super.onPause();
        requireActivity().unregisterReceiver(refreshReceiver);
    }
}