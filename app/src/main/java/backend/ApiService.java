package backend;

import java.util.List;

import backend.requests.CancelAlertRequest;
import backend.requests.SubscribeRequest;
import backend.requests.CancelSubscriptionRequest;
import backend.results.CancellationResponseSchema;
import backend.requests.CreateAlertRequest;
import backend.results.NativeCheckoutResponse;
import models.PriceAlert;
import models.StripePrice;
import models.UsageData;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {
    @GET("stripe/prices")
    Call<List<StripePrice>> getStripePrices();

    // The paid_plans.py defines this endpoint
    @POST("stripe/initiate-payment")
    Call<NativeCheckoutResponse> initiatePayment(@Body SubscribeRequest subscribeRequest);

    @POST("stripe/cancel-subscription")
    Call<CancellationResponseSchema> cancelSubscription(@Body CancelSubscriptionRequest request);

    @GET("subscriptions/{user_id}/limits")
    Call<UsageData> getSubscriptionLimits(@Path("user_id") String userId);

    @POST("alerts")
    Call<CreateAlertRequest> createAlert(@Body CreateAlertRequest request);

    @GET("alerts/{user_id}")
    Call<List<PriceAlert>> getActiveAlerts(@Path("user_id") String userId);

    @POST("alerts/{alert_id}/cancel")
    Call<Void> cancelAlert(@Path("alert_id") String alertId, @Query("user_id") String userId);

}
