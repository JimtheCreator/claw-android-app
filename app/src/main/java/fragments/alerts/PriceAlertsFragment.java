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
import androidx.lifecycle.Observer;
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
    private Observer<List<PriceAlert>> alertsObserver;
    private Observer<String> messageObserver;
    private Observer<Boolean> loadingObserver;

    private String userId;
    private AuthViewModel authViewModel;
    private static final String TAG = "PriceAlertsFragment";

    private BroadcastReceiver refreshReceiver;
    private static final String REFRESH_ACTION_SUFFIX = ".ACTION_REFRESH_ALERTS";
    private String refreshAction;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        refreshAction = requireContext().getPackageName() + REFRESH_ACTION_SUFFIX;

        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
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

        adapter = new PriceAlertsAdapter(alert -> {
            String currentUserId = getCurrentUserId();
            if (currentUserId != null) {
                viewModel.cancelAlert(currentUserId, alert.getId(), alert.getSymbol());
            }
        });

        binding.alertsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.alertsRecyclerView.setAdapter(adapter);

        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            String currentUserId = getCurrentUserId();
            if (currentUserId != null) {
                viewModel.refreshAlerts(currentUserId);
            } else {
                binding.swipeRefreshLayout.setRefreshing(false);
            }
        });

        setupObservers();
        setupBroadcastReceiver();
    }

    private void setupBroadcastReceiver() {
        refreshReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (refreshAction.equals(intent.getAction())) {
                    Timber.d("Received broadcast to refresh alerts. Refreshing...");
                    String currentUserId = getCurrentUserId();
                    if (currentUserId != null) {
                        viewModel.refreshAlertsInBackground(currentUserId);
                    }
                }
            }
        };
    }

    private void setupObservers() {
        authViewModel.getAuthState().observe(getViewLifecycleOwner(), authState -> {
            if (binding == null) return;
            String currentUid = getCurrentUserId();
            switch (authState) {
                case AUTHENTICATED:
                    if (currentUid != null) {
                        Log.d(TAG, "AUTHENTICATED");
                        binding.signInLayout.setVisibility(View.GONE);
                        callPriceViewModel();
                        if (viewModel.getAlerts().getValue() == null) {
                            viewModel.fetchActiveAlerts(currentUid);
                        }
                    } else {
                        Log.d(TAG, "AUTHENTICATED BUT USERID NULL");
                        removeDataObservers();
                        viewModel.clearAlerts();
                        adjustWidgets();
                    }
                    break;
                case UNAUTHENTICATED:
                    Log.d(TAG, "UNAUTHENTICATED");
                    removeDataObservers();
                    viewModel.clearAlerts();
                    adjustWidgets();
                    break; // ✅ FIX: Added missing break statement.
                case ERROR:
                    Log.d(TAG, "ERROR");
                    removeDataObservers();
                    viewModel.clearAlerts();
                    adjustWidgets();
                    break;
            }
        });
    }

    private void callPriceViewModel() {
        binding.signInLayout.setVisibility(View.GONE);

        alertsObserver = alerts -> {
            if (binding == null) return;

            // ✅ FIX: Add a guard clause to check the authentication state.
            // This prevents the observer from updating the UI if the user has logged out.
            if (authViewModel.getAuthState().getValue() != AuthViewModel.AuthState.AUTHENTICATED) {
                return;
            }

            adapter.setAlerts(alerts != null ? alerts : new ArrayList<>());
            if (alerts != null && !alerts.isEmpty()) {
                binding.noAlertsLayout.setVisibility(View.GONE);
                binding.alertsRecyclerView.setVisibility(View.VISIBLE);
            } else {
                binding.noAlertsLayout.setVisibility(View.VISIBLE);
                binding.alertsRecyclerView.setVisibility(View.GONE);
            }
        };
        viewModel.getAlerts().observe(getViewLifecycleOwner(), alertsObserver);

        messageObserver = message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        };
        viewModel.getMessages().observe(getViewLifecycleOwner(), messageObserver);

        loadingObserver = isLoading -> {
            if (binding == null) return;

            if (isLoading) {
                if (!binding.swipeRefreshLayout.isRefreshing()) {
                    binding.progressBar.setVisibility(View.VISIBLE);
                    binding.noAlertsLayout.setVisibility(View.GONE);
                    binding.alertsRecyclerView.setVisibility(View.GONE);
                }
            } else {
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefreshLayout.setRefreshing(false);
            }
        };
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loadingObserver);
    }

    private void adjustWidgets() {
        Log.d(TAG, "NOT SIGNED-IN");
        binding.swipeRefreshLayout.setRefreshing(false);
        binding.signInLayout.setVisibility(View.VISIBLE);
        binding.noAlertsLayout.setVisibility(View.GONE);
        binding.alertsRecyclerView.setVisibility(View.GONE);
        binding.progressBar.setVisibility(View.GONE);
    }

    private String getCurrentUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return (user != null) ? user.getUid() : null;
    }

    public void refreshData() {
        String currentUserId = getCurrentUserId();
        if (currentUserId != null) {
            viewModel.refreshAlertsInBackground(currentUserId);
        }
    }

    private void removeDataObservers() {
        if (adapter != null) {
            adapter.setAlerts(new ArrayList<>());
        }
        if (alertsObserver != null) {
            viewModel.getAlerts().removeObserver(alertsObserver);
        }
        if (messageObserver != null) {
            viewModel.getMessages().removeObserver(messageObserver);
        }
        if (loadingObserver != null) {
            viewModel.getIsLoading().removeObserver(loadingObserver);
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