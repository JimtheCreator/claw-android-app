package fragments.alerts;

import android.content.BroadcastReceiver;
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
import androidx.recyclerview.widget.LinearLayoutManager;

import com.claw.ai.R;
import com.claw.ai.databinding.FragmentPriceAlertsBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

import adapters.PriceAlertsAdapter;
import models.PriceAlert;
import viewmodels.SharedRefreshViewModel;
import viewmodels.alerts.PriceAlertsViewModel;
import viewmodels.google_login.AuthViewModel;

public class PriceAlertsFragment extends Fragment {
    private FragmentPriceAlertsBinding binding;
    private PriceAlertsViewModel priceAlertsViewModel;
    private PriceAlertsAdapter adapter;
    private Observer<List<PriceAlert>> alertsObserver;
    private SharedRefreshViewModel sharedRefreshViewModel;
    private Observer<String> messageObserver;
    private Observer<Boolean> loadingObserver;

    private String userId;
    private AuthViewModel authViewModel;
    private static final String TAG = "PriceAlertsFragment";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        authViewModel.initialize(requireActivity(), getString(R.string.web_client_id));

        priceAlertsViewModel = new ViewModelProvider(requireActivity()).get(PriceAlertsViewModel.class);

        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        userId = firebaseUser != null ? firebaseUser.getUid() : null;

        sharedRefreshViewModel = new ViewModelProvider(requireActivity()).get(SharedRefreshViewModel.class);
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
                priceAlertsViewModel.cancelAlert(currentUserId, alert.getId(), alert.getSymbol());
            }
        });

        binding.alertsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.alertsRecyclerView.setAdapter(adapter);

        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            String currentUserId = getCurrentUserId();
            if (currentUserId != null) {
                priceAlertsViewModel.refreshAlerts(currentUserId);
            } else {
                binding.swipeRefreshLayout.setRefreshing(false);
            }
        });

        setupObservers();
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
                        if (priceAlertsViewModel.getAlerts().getValue() == null) {
                            priceAlertsViewModel.fetchActiveAlerts(currentUid);
                        }
                    } else {
                        Log.d(TAG, "AUTHENTICATED BUT USERID NULL");
                        removeDataObservers();
                        priceAlertsViewModel.clearAlerts();
                        adjustWidgets();
                    }
                    break;
                case UNAUTHENTICATED:
                    Log.d(TAG, "UNAUTHENTICATED");
                    removeDataObservers();
                    priceAlertsViewModel.clearAlerts();
                    adjustWidgets();
                    break;
                case ERROR:
                    Log.d(TAG, "ERROR");
                    removeDataObservers();
                    priceAlertsViewModel.clearAlerts();
                    adjustWidgets();
                    break;
            }
        });

        // This observer listens for the refresh request from the shared ViewModel (if still needed for other purposes)
        sharedRefreshViewModel.priceAlertsRefreshRequest.observe(getViewLifecycleOwner(), event -> {
            if (event.getContentIfNotHandled() != null) {
                refreshData();
            }
        });

        // New observer for scrolling to top and showing refresh progress, from PriceAlertsViewModel
        priceAlertsViewModel.getScrollToTopAndShowRefresh().observe(getViewLifecycleOwner(), shouldScrollAndShow -> {
            if (shouldScrollAndShow != null && shouldScrollAndShow) {
                if (binding != null) {
                    binding.progressBar.setVisibility(View.VISIBLE); // Show the progress bar
                    LinearLayoutManager layoutManager = (LinearLayoutManager) binding.alertsRecyclerView.getLayoutManager();
                    if (layoutManager != null && layoutManager.findFirstCompletelyVisibleItemPosition() > 0) {
                        binding.alertsRecyclerView.scrollToPosition(0); // Scroll to top if not already there
                    }
                    // The SingleLiveEvent handles the "reset" internally by only emitting once.
                    // No need to explicitly set it to false here.
                }
            }
        });
    }

    private void callPriceViewModel() {
        binding.signInLayout.setVisibility(View.GONE);

        alertsObserver = alerts -> {
            if (binding == null) return;

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
            binding.progressBar.setVisibility(View.GONE); // Hide progress bar after data is loaded
        };

        priceAlertsViewModel.getAlerts().observe(getViewLifecycleOwner(), alertsObserver);

        messageObserver = message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        };
        priceAlertsViewModel.getMessages().observe(getViewLifecycleOwner(), messageObserver);

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
        priceAlertsViewModel.getIsLoading().observe(getViewLifecycleOwner(), loadingObserver);
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
            priceAlertsViewModel.refreshAlertsInBackground(currentUserId);
        }
    }

    private void removeDataObservers() {
        if (adapter != null) {
            adapter.setAlerts(new ArrayList<>());
        }
        if (alertsObserver != null) {
            priceAlertsViewModel.getAlerts().removeObserver(alertsObserver);
        }
        if (messageObserver != null) {
            priceAlertsViewModel.getMessages().removeObserver(messageObserver);
        }
        if (loadingObserver != null) {
            priceAlertsViewModel.getIsLoading().removeObserver(loadingObserver);
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
    }

    @Override
    public void onPause() {
        super.onPause();
    }
}