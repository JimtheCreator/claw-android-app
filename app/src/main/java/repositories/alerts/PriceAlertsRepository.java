package repositories.alerts;

import java.util.List;

import backend.ApiService;
import backend.MainClient;
import backend.requests.CreateAlertRequest;
import models.PriceAlert;
import retrofit2.Call;
import retrofit2.Callback;

public class PriceAlertsRepository {
    private final ApiService apiService;

    public PriceAlertsRepository() {
        apiService = MainClient.getApiService();
    }

    public void createAlert(String userId, String symbol, String conditionType, double conditionValue, Callback<CreateAlertRequest> callback) {
        CreateAlertRequest request = new CreateAlertRequest(userId, symbol, conditionType, conditionValue);
        Call<CreateAlertRequest> call = apiService.createAlert(request);
        call.enqueue(callback);
    }

    public void getActiveAlerts(String userId, Callback<List<PriceAlert>> callback) {
        Call<List<PriceAlert>> call = apiService.getActiveAlerts(userId);
        call.enqueue(callback);
    }

    public void cancelAlert(String userId, String alertId, Callback<Void> callback) {
        Call<Void> call = apiService.cancelAlert(alertId, userId);
        call.enqueue(callback);
    }
}
