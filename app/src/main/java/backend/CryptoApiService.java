package backend;

import androidx.annotation.Nullable;

import java.util.List;

import models.Symbol;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface CryptoApiService {
    @GET("cryptos")
    Call<List<Symbol>> getTopCryptos(
            @Query("limit") int limit,
            @Query("base_currencies") @Nullable List<String> baseCurrencies
    );

    @GET("cryptos/search")
    Call<List<Symbol>> searchCrypto(
            @Query("query") String query,
            @Query("limit") int limit
    );
}

