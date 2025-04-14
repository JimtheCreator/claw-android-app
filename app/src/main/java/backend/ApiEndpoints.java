package backend;

import androidx.annotation.Nullable;

import java.util.List;

import models.MarketDataEntity;
import models.Symbol;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiEndpoints {

    @GET("cryptos/search")
    Call<List<Symbol>> searchCrypto(
            @Query("query") String query,
            @Query("limit") int limit
    );

    @GET("market-data/{symbol}")
    Call<MarketDataResponse> getMarketData(
            @Path("symbol") String symbol,
            @Query("interval") String interval,
            @Query("page") int page,
            @Query("page_size") int page_size,
            @Query("start_time") String startTime,
            @Query("end_time") String endTime
    );
}

