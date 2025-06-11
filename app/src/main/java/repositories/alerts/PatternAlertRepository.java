package repositories.alerts;
import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;
import java.util.stream.Collectors;

import backend.ApiService;
import backend.MainClient;
import backend.requests.CreatePatternAlertRequest;
import models.PatternAlert;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PatternAlertRepository {

    private final ApiService apiService;
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>();
    public final LiveData<Boolean> isLoading = _isLoading;

    private final MutableLiveData<String> _error = new MutableLiveData<>();
    public final LiveData<String> error = _error;


    public PatternAlertRepository(Application application) {
        this.apiService = MainClient.getApiService();
    }

    public LiveData<List<PatternAlert>> getPatternAlerts(String userId) {
        _isLoading.setValue(true);
        MutableLiveData<List<PatternAlert>> data = new MutableLiveData<>();

        apiService.getPatternAlerts(userId).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<PatternAlert>> call, @NonNull Response<List<PatternAlert>> response) {
                _isLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    // Filter to keep only "active" alerts and sort them
                    List<PatternAlert> activeAlerts = response.body().stream()
                            .filter(alert -> "active".equalsIgnoreCase(alert.getStatus())) // Assumes getStatus() exists
                            .sorted((a1, a2) -> a2.getCreatedAt().compareTo(a1.getCreatedAt()))
                            .collect(Collectors.toList());

                    data.setValue(activeAlerts);
                } else {
                    _error.setValue("Failed to fetch alerts: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<PatternAlert>> call, @NonNull Throwable t) {
                _isLoading.setValue(false);
                _error.setValue("Network error: " + t.getMessage());
            }
        });
        return data;
    }

    public LiveData<PatternAlert> createPatternAlert(CreatePatternAlertRequest request, String userId) {
        _isLoading.setValue(true);
        MutableLiveData<PatternAlert> data = new MutableLiveData<>();

        apiService.createPatternAlert(userId, request).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<PatternAlert> call, @NonNull Response<PatternAlert> response) {
                _isLoading.setValue(false);
                if (response.isSuccessful()) {
                    data.setValue(response.body());
                } else if (response.code() == 403) {
                    _error.setValue("Pattern alert limit reached");
                } else {
                    _error.setValue("Failed to create alert: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<PatternAlert> call, Throwable t) {
                _isLoading.setValue(false);
                _error.setValue("Network error: " + t.getMessage());
            }
        });

        return data;
    }

    public LiveData<Boolean> deletePatternAlert(String alertId, String userId) {
        _isLoading.setValue(true);
        MutableLiveData<Boolean> success = new MutableLiveData<>();

        apiService.deletePatternAlert(userId, alertId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                _isLoading.setValue(false);
                if (response.isSuccessful()) {
                    success.setValue(true);
                } else {
                    success.setValue(false);
                    _error.setValue("Failed to delete alert: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                _isLoading.setValue(false);
                success.setValue(false);
                _error.setValue("Network error: " + t.getMessage());
            }
        });
        return success;
    }
}

