package backend;

import java.util.List;

import backend.requests.SubscribeRequest;
import models.CancelSubscriptionRequest;
import models.CancellationResponseSchema;
import models.NativeCheckoutResponse;
import models.StripePrice;
import models.UsageData;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface ApiService {
    // Assuming your backend is hosted at some BASE_URL
    // The prices.py defines this endpoint
    @GET("stripe/prices")
    Call<List<StripePrice>> getStripePrices();

    // The paid_plans.py defines this endpoint
    @POST("stripe/initiate-payment")
    Call<NativeCheckoutResponse> initiatePayment(@Body SubscribeRequest subscribeRequest);

    @POST("stripe/cancel-subscription")
    Call<CancellationResponseSchema> cancelSubscription(@Body CancelSubscriptionRequest request);

    @GET("subscriptions/{user_id}/limits")
    Call<UsageData> getSubscriptionLimits(@Path("user_id") String userId);
}
