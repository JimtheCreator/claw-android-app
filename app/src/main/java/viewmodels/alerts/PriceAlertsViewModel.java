package viewmodels.alerts;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import backend.requests.CreateAlertRequest;
import models.PriceAlert;
import repositories.alerts.PriceAlertsRepository;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import utils.SingleLiveEvent;

public class PriceAlertsViewModel extends ViewModel {
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final PriceAlertsRepository repository;
    private final MutableLiveData<List<PriceAlert>> alertsLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> messageLiveData = new MutableLiveData<>();
    private final SingleLiveEvent<Boolean> scrollToTopAndShowRefresh = new SingleLiveEvent<>(); // Add this

    public PriceAlertsViewModel() {
        repository = new PriceAlertsRepository();
    }

    /**
     * 🔥 ADD THIS METHOD
     * Clears the alerts LiveData. This is crucial for handling user sign-outs
     * to prevent stale data from being shown to the next user.
     */
    public void clearAlerts() {
        alertsLiveData.postValue(null);
    }

    public void createAlert(String userId, String symbol, String conditionType, double conditionValue) {
        isLoading.postValue(true);
        repository.createAlert(userId, symbol, conditionType, conditionValue, new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<CreateAlertRequest> call, @NonNull Response<CreateAlertRequest> response) {
                isLoading.postValue(false);
                if (response.isSuccessful()) {
                    messageLiveData.postValue("Alert created successfully");
                    // Trigger refresh after creating an alert
                    fetchActiveAlerts(userId);
                } else if (response.code() == 403) {
                    messageLiveData.postValue("Price alert limit reached");
                } else {
                    messageLiveData.postValue("Failed to create alert: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<CreateAlertRequest> call, @NonNull Throwable t) {
                isLoading.postValue(false);
                messageLiveData.postValue("Error: " + t.getMessage());
            }
        });
    }


    public void fetchActiveAlerts(String userId) {
        isLoading.postValue(true);
        repository.getActiveAlerts(userId, new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<PriceAlert>> call, @NonNull Response<List<PriceAlert>> response) {
                isLoading.postValue(false);
                if (response.isSuccessful()) {
                    List<PriceAlert> alerts = response.body();
                    if (alerts != null) {
                        // Filter to keep only "active" alerts and sort them
                        List<PriceAlert> activeAlerts = alerts.stream()
                                .filter(alert -> "active".equalsIgnoreCase(alert.getStatus())) // Assumes getStatus() exists
                                .sorted((a1, a2) -> a2.getCreatedAt().compareTo(a1.getCreatedAt()))
                                .collect(Collectors.toList());
                        alertsLiveData.postValue(activeAlerts);
                        scrollToTopAndShowRefresh.call(); // Trigger UI update
                    } else {
                        alertsLiveData.postValue(new ArrayList<>());
                    }
                } else {
                    messageLiveData.postValue("Failed to fetch alerts: " + response.code());
                    alertsLiveData.postValue(new ArrayList<>());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<PriceAlert>> call, @NonNull Throwable t) {
                isLoading.postValue(false);
                messageLiveData.postValue("Error: " + t.getMessage());
                alertsLiveData.postValue(new ArrayList<>());
            }
        });
    }

    public void cancelAlert(String userId, String alertId, String symbol) {
        // Optimistic UI Update
        List<PriceAlert> currentAlerts = alertsLiveData.getValue();
        if (currentAlerts != null) {
            List<PriceAlert> updatedAlerts = new ArrayList<>(currentAlerts);
            updatedAlerts.removeIf(alert -> alert.getId().equals(alertId));
            alertsLiveData.postValue(updatedAlerts);
            scrollToTopAndShowRefresh.call(); // Trigger UI update
        }

        repository.cancelAlert(userId, alertId, symbol, new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) {
                    messageLiveData.postValue("Alert cancelled successfully");
                } else {
                    messageLiveData.postValue("Failed to cancel alert: " + response.code());
                    fetchActiveAlerts(userId);
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                messageLiveData.postValue("Error: " + t.getMessage());
                fetchActiveAlerts(userId);
            }
        });
    }

    // Silent refresh method that doesn't show loading state
    public void refreshAlertsInBackground(String userId) {
        if (userId != null) {
            repository.getActiveAlerts(userId, new Callback<List<PriceAlert>>() {
                @Override
                public void onResponse(@NonNull Call<List<PriceAlert>> call, @NonNull Response<List<PriceAlert>> response) {
                    if (response.isSuccessful()) {
                        List<PriceAlert> alerts = response.body();
                        if (alerts != null) {
                            // Filter to keep only "active" alerts and sort them
                            List<PriceAlert> activeAlerts = alerts.stream()
                                    .filter(alert -> "active".equalsIgnoreCase(alert.getStatus())) // Assumes getStatus() exists
                                    .sorted((a1, a2) -> a2.getCreatedAt().compareTo(a1.getCreatedAt()))
                                    .collect(Collectors.toList());
                            alertsLiveData.postValue(activeAlerts);
                            scrollToTopAndShowRefresh.call(); // Trigger UI update
                        } else {
                            alertsLiveData.postValue(new ArrayList<>());
                        }
                    }
                    // Don't show error messages for background refresh
                }

                @Override
                public void onFailure(@NonNull Call<List<PriceAlert>> call, @NonNull Throwable t) {
                    // Silent failure for background refresh
                }
            });
        }
    }

    // Refresh method with loading state (for manual refresh like swipe-to-refresh)
    public void refreshAlerts(String userId) {
        if (userId != null) {
            fetchActiveAlerts(userId);
        }
    }

    public LiveData<List<PriceAlert>> getAlerts() {
        return alertsLiveData;
    }

    public LiveData<String> getMessages() {
        return messageLiveData;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public SingleLiveEvent<Boolean> getScrollToTopAndShowRefresh() { // Getter for the SingleLiveEvent
        return scrollToTopAndShowRefresh;
    }
}