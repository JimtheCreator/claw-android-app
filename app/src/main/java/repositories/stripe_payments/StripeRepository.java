package repositories.stripe_payments;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

import backend.ApiService;
import backend.MainClient;
import backend.requests.SubscribeRequest;
import models.CancelSubscriptionRequest;
import models.CancellationResponseSchema;
import models.NativeCheckoutResponse;
import models.StripePrice;
import models.UsageData;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StripeRepository {
    private static final String TAG = "StripeRepository";
    private final ApiService apiService;

    public StripeRepository() {
        this.apiService = MainClient.getApiService();
    }

    public LiveData<List<StripePrice>> fetchPrices() {
        MutableLiveData<List<StripePrice>> pricesLiveData = new MutableLiveData<>();
        apiService.getStripePrices().enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<List<StripePrice>> call, Response<List<StripePrice>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    pricesLiveData.setValue(response.body());
                } else {
                    Log.e(TAG, "Failed to fetch prices: " + response.code() + " - " + response.message());
                    pricesLiveData.setValue(null); // Or some error state
                }
            }

            @Override
            public void onFailure(Call<List<StripePrice>> call, Throwable t) {
                Log.e(TAG, "Error fetching prices: ", t);
                pricesLiveData.setValue(null); // Or some error state
            }
        });
        return pricesLiveData;
    }

    public LiveData<NativeCheckoutResponse> getPaymentSheetParameters(String userId, String planId) {
        MutableLiveData<NativeCheckoutResponse> paymentSheetParamsLiveData = new MutableLiveData<>();
        SubscribeRequest request = new SubscribeRequest(userId, planId);

        apiService.initiatePayment(request).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<NativeCheckoutResponse> call, @NonNull Response<NativeCheckoutResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    paymentSheetParamsLiveData.setValue(response.body());
                } else {
                    Log.e(TAG, "Failed to get PaymentSheet params: " + response.code());
                    try {
                        if (response.errorBody() != null) {
                            Log.e(TAG, "Error body: " + response.errorBody().string());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing error body", e);
                    }
                    paymentSheetParamsLiveData.setValue(null);
                }
            }

            @Override
            public void onFailure(Call<NativeCheckoutResponse> call, Throwable t) {
                Log.e(TAG, "Error getting PaymentSheet params: ", t);
                paymentSheetParamsLiveData.setValue(null);
            }
        });
        return paymentSheetParamsLiveData;
    }

    public LiveData<CancellationResponseSchema> cancelSubscription(String userId, String subscriptionId, Boolean cancelAtPeriodEnd) {
        MutableLiveData<CancellationResponseSchema> cancellationResponseLiveData = new MutableLiveData<>();
        CancelSubscriptionRequest request = new CancelSubscriptionRequest(userId, subscriptionId, cancelAtPeriodEnd);

        apiService.cancelSubscription(request).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<CancellationResponseSchema> call, @NonNull Response<CancellationResponseSchema> response) {
                if (response.isSuccessful() && response.body() != null) {
                    cancellationResponseLiveData.setValue(response.body());
                } else {
                    Log.e(TAG, "Failed to cancel subscription: " + response.code());
                    try {
                        if (response.errorBody() != null) {
                            Log.e(TAG, "Error body: " + response.errorBody().string());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing error body", e);
                    }
                    cancellationResponseLiveData.setValue(null);
                }
            }

            @Override
            public void onFailure(Call<CancellationResponseSchema> call, Throwable t) {
                Log.e(TAG, "Error canceling subscription: ", t);
                cancellationResponseLiveData.setValue(null);
            }
        });
        return cancellationResponseLiveData;
    }

}
