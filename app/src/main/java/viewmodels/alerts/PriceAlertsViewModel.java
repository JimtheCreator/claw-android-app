package viewmodels.alerts;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;


import backend.requests.CancelAlertRequest;
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

    public LiveData<List<PriceAlert>> getAlerts(String userId) {
        repository.getAlerts(userId, new Callback<List<PriceAlert>>() {
            @Override
            public void onResponse(@NonNull Call<List<PriceAlert>> call, @NonNull Response<List<PriceAlert>> response) {
                if (response.isSuccessful()) {
                    alertsLiveData.setValue(response.body());
                } else {
                    messageLiveData.setValue("Failed to fetch alerts: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<PriceAlert>> call, @NonNull Throwable t) {
                messageLiveData.setValue("Error: " + t.getMessage());
            }
        });

        return alertsLiveData;
    }

    public void createAlert(String userId, String symbol, String conditionType, double conditionValue) {
        isLoading.postValue(true);
        repository.createAlert(userId, symbol, conditionType, conditionValue, new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<CreateAlertRequest> call, @NonNull Response<CreateAlertRequest> response) {
                if (response.isSuccessful()) {
                    isLoading.postValue(false);
                    messageLiveData.setValue("Alert created successfully");
                    getAlerts(userId); // Refresh alerts
                } else if (response.code() == 403) {
                    isLoading.postValue(false);
                    messageLiveData.setValue("Price alert limit reached");
                } else {
                    isLoading.postValue(false);
                    messageLiveData.setValue("Failed to create alert: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<CreateAlertRequest> call, @NonNull Throwable t) {
                isLoading.postValue(false);
                messageLiveData.setValue("Error: " + t.getMessage());
            }
        });
    }

    public void cancelAlert(String userId, int alertId) {
        repository.cancelAlert(userId, alertId, new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) {
                    messageLiveData.setValue("Alert cancelled");
                    getAlerts(userId); // Refresh alerts
                } else {
                    messageLiveData.setValue("Failed to cancel alert: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                messageLiveData.setValue("Error: " + t.getMessage());
            }
        });
    }

    public LiveData<String> getMessages() {
        return messageLiveData;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
}
