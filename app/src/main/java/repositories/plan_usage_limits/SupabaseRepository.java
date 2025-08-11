package repositories.plan_usage_limits;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import backend.ApiService;
import backend.MainClient;
import backend.results.UsageResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SupabaseRepository {

    private static final String TAG = "SupabaseRepository";
    private final ApiService apiService;

    public SupabaseRepository() {
        this.apiService = MainClient.getApiService();
    }

    public LiveData<UsageResponse> getSubscriptionLimits(String userId) {
        if (userId == null || userId.isEmpty()) return null;

        MutableLiveData<UsageResponse> usageDataLiveData = new MutableLiveData<>();

        // This line will now be correct because getSubscriptionLimits returns Call<UsageResponse>
        apiService.getSubscriptionLimits(userId).enqueue(new Callback<UsageResponse>() {
            @Override
            public void onResponse(@NonNull Call<UsageResponse> call, @NonNull Response<UsageResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getUsage() != null) {
                    // Post the entire successful response body, which is a UsageResponse object
                    usageDataLiveData.postValue(response.body());
                } else {
                    Log.e(TAG, "Failed to fetch or parse usage data: " + response.code());
                    usageDataLiveData.postValue(null);
                }
            }

            @Override
            public void onFailure(@NonNull Call<UsageResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "Error fetching usage data: ", t);
                usageDataLiveData.postValue(null);
            }
        });
        return usageDataLiveData;
    }
}