package viewmodels.alerts;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import backend.requests.CreateAlertRequest;
import models.PriceAlert;
import repositories.alerts.PriceAlertsRepository;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PriceAlertsViewModel extends ViewModel {
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final PriceAlertsRepository repository;
    private final MutableLiveData<List<PriceAlert>> alertsLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> messageLiveData = new MutableLiveData<>();

    public PriceAlertsViewModel() {
        repository = new PriceAlertsRepository();
    }

    public void createAlert(String userId, String symbol, String conditionType, double conditionValue) {
        isLoading.postValue(true);
        repository.createAlert(userId, symbol, conditionType, conditionValue, new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<CreateAlertRequest> call, @NonNull Response<CreateAlertRequest> response) {
                isLoading.postValue(false);
                if (response.isSuccessful()) {
                    messageLiveData.postValue("Alert created successfully");
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
        repository.getActiveAlerts(userId, new Callback<List<PriceAlert>>() {
            @Override
            public void onResponse(@NonNull Call<List<PriceAlert>> call, @NonNull Response<List<PriceAlert>> response) {
                isLoading.postValue(false);
                if (response.isSuccessful()) {
                    List<PriceAlert> alerts = response.body();
                    if (alerts != null) {
                        // Sort alerts by creation time (newest first)
                        alerts.sort((a1, a2) ->
                                a2.getCreatedAt().compareTo(a1.getCreatedAt()));
                    }
                    alertsLiveData.postValue(alerts != null ? alerts : new ArrayList<>());
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

    public void cancelAlert(String userId, String alertId) {
        // Optimistic UI Update
        List<PriceAlert> currentAlerts = alertsLiveData.getValue();
        if (currentAlerts != null) {
            List<PriceAlert> updatedAlerts = new ArrayList<>(currentAlerts);
            updatedAlerts.removeIf(alert -> alert.getId().equals(alertId));
            alertsLiveData.postValue(updatedAlerts);
        }

        repository.cancelAlert(userId, alertId, new Callback<Void>() {
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
                            // Sort alerts by creation time (newest first)
                            alerts.sort((a1, a2) ->
                                    a2.getCreatedAt().compareTo(a1.getCreatedAt()));
                        }
                        alertsLiveData.postValue(alerts != null ? alerts : new ArrayList<>());
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
}