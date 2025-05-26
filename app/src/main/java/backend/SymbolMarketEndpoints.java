package backend;

import androidx.annotation.Nullable;

import java.util.List;

import backend.requests.AddWatchlistRequest;
import io.reactivex.Completable;
import models.MarketDataResponse;
import models.Symbol;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface SymbolMarketEndpoints {

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

    @DELETE("watchlist/remove")
    Completable removeFromWatchlist(
            @Query("user_id") String user_id,
            @Query("symbol") String symbol
    );
}

