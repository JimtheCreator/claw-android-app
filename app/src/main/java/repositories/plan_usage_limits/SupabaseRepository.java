package repositories.plan_usage_limits;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import backend.ApiService;
import backend.MainClient;
import models.UsageData;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SupabaseRepository {

    private static final String TAG = "SupabaseRepository";
    private final ApiService apiService;

    public SupabaseRepository() {
        this.apiService = MainClient.getApiService();
    }

    public LiveData<UsageData> getSubscriptionLimits(String userId) {
        if (userId == null || userId.isEmpty()) return null;

        MutableLiveData<UsageData> usageDataLiveData = new MutableLiveData<>();
        apiService.getSubscriptionLimits(userId).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<UsageData> call, @NonNull Response<UsageData> response) {
                if (response.isSuccessful() && response.body() != null) {
                    usageDataLiveData.postValue(response.body());
                } else {
                    Log.e(TAG, "Failed to fetch usage data: " + response.code());
                    try {
                        if (response.errorBody() != null) {
                            Log.e(TAG, "Error body: " + response.errorBody().string());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing error body", e);
                    }

                    usageDataLiveData.postValue(null);
                }

            }

            @Override
            public void onFailure(@NonNull Call<UsageData> call, @NonNull Throwable t) {
                Log.e(TAG, "Error fetching usage data: ", t);
                usageDataLiveData.postValue(null);
            }
        });

        return usageDataLiveData;
    }


}
