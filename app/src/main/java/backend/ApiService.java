package backend;

import androidx.annotation.Nullable;

import java.util.List;

import backend.requests.AddWatchlistRequest;
import backend.requests.CancelAlertRequest;
import backend.requests.CreatePatternAlertRequest;
import backend.requests.RemoveWatchlistRequest;
import backend.requests.SubscribeRequest;
import backend.requests.CancelSubscriptionRequest;
import backend.results.CancellationResponseSchema;
import backend.requests.CreateAlertRequest;
import backend.results.MarketDataResponse;
import backend.results.NativeCheckoutResponse;
import io.reactivex.Completable;
import models.CachedSymbol;
import models.Pattern;
import models.PatternAlert;
import models.PriceAlert;
import models.StripePrice;
import models.Symbol;
import models.UsageData;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.Header;
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
    Call<Void> cancelAlert(@Path("alert_id") String alertId, @Query("user_id") String userId, @Query("symbol") String symbol);

    @GET("cache/patterns")
    Call<List<Pattern>> getPatterns();


    @GET("cache/symbols")
    Call<List<CachedSymbol>> getSymbols(); // This will no longer be used by SyncWorker

    @GET("cache/symbols/search")
    Call<List<CachedSymbol>> searchSymbols(@Query("q") String query);


    @GET("cryptos/search")
    Call<List<Symbol>> searchCrypto(
            @Query("query") String query,
            @Query("limit") int limit
    );

    @GET("market-data/{symbol}")
    Call<MarketDataResponse> getMarketData(
            @Path("symbol") String symbol,
            @Query("interval") String interval,
            @Query("start_time") @Nullable String startTime,
            @Query("end_time") @Nullable String endTime,
            @Query("page") int page,
            @Query("page_size") int pageSize
    );


    @POST("watchlist/add")
    Completable addToWatchlist(
            @Body AddWatchlistRequest request
    );

    @HTTP(method = "DELETE", path = "watchlist/remove", hasBody = true)
    Completable removeFromWatchlist(
            @Body RemoveWatchlistRequest request
    );

    @GET("watchlist/{user_id}")
    Call<List<Symbol>> getWatchlist(@Path("user_id") String userId);


    @POST("pattern-alerts/create")
    Call<PatternAlert> createPatternAlert(
            @Header("X-User-Id") String userId,
            @Body CreatePatternAlertRequest request
    );

    @GET("pattern-alerts/read")
    Call<List<PatternAlert>> getPatternAlerts(
            @Header("X-User-Id") String userId
    );

    @DELETE("pattern-alerts/delete/{alert_id}")
    Call<Void> deletePatternAlert(
            @Header("X-User-Id") String userId,
            @Path("alert_id") String alertId
    );
}
