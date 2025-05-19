package backend;

import java.util.List;

import backend.requests.SubscribeRequest;
import backend.results.CheckoutResponse;
import models.NativeCheckoutResponse;
import models.StripePrice;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface ApiService {
    // Assuming your backend is hosted at some BASE_URL
    // The prices.py defines this endpoint
    @GET("stripe/prices")
    Call<List<StripePrice>> getStripePrices();

    // The paid_plans.py defines this endpoint
    @POST("stripe/initiate-payment")
    Call<NativeCheckoutResponse> initiatePayment(@Body SubscribeRequest subscribeRequest);
}
