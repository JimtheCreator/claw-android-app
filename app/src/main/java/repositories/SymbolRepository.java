package repositories;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import backend.SymbolMarketEndpoints;
import backend.MainClient;
import backend.requests.AddWatchlistRequest;
import backend.requests.RemoveWatchlistRequest;
import io.reactivex.Completable;
import models.Symbol;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

public class SymbolRepository {
    private final SymbolMarketEndpoints api;
    private final Map<String, List<Symbol>> searchCache = new ConcurrentHashMap<>();
    private Call<List<Symbol>> currentSearchCall;

    public SymbolRepository() {
        api = MainClient.getInstance().create(SymbolMarketEndpoints.class);
    }


    // Fixed searchCrypto implementation
    public LiveData<List<Symbol>> searchCrypto(String query, int limit) {
        MutableLiveData<List<Symbol>> liveData = new MutableLiveData<>();

        // 1. Cancel previous request
        if (currentSearchCall != null) {
            currentSearchCall.cancel();
        }

        // 2. Check cache first
        if (searchCache.containsKey(query)) {
            liveData.postValue(searchCache.get(query));
            return liveData;
        }

        // 3. Make new network request
        currentSearchCall = api.searchCrypto(query, limit);
        currentSearchCall.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<Symbol>> call, @NonNull Response<List<Symbol>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    searchCache.put(query, response.body());
                    liveData.postValue(response.body());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Symbol>> call, @NonNull Throwable t) {
                if (!call.isCanceled()) {
                    liveData.postValue(Collections.emptyList());
                }
            }
        });

        return liveData;
    }

    public Completable addToWatchlist(AddWatchlistRequest request) {
        return api.addToWatchlist(request);
    }

    public Completable removeFromWatchlist(RemoveWatchlistRequest request) {
        return api.removeFromWatchlist(request);
    }

    public LiveData<List<Symbol>> getWatchlist(String userId) {
        MutableLiveData<List<Symbol>> watchlistData = new MutableLiveData<>();
        Call<List<Symbol>> call = api.getWatchlist(userId);
        call.enqueue(new Callback<List<Symbol>>() {
            // In SymbolRepository.java
            @Override
            public void onResponse(Call<List<Symbol>> call, Response<List<Symbol>> response) {
                if (response.isSuccessful()) {
                    List<Symbol> symbols = response.body();
                    if (symbols != null) {
                        Log.d("SymbolRepository", "Response successful. Body is NOT null. Size: " + symbols.size());

                        // Optionally, log the first item to see if fields were deserialized:
                        // if (!symbols.isEmpty()) {
                        //     Timber.d("SymbolRepository: First symbol: %s", symbols.get(0).getSymbol()); // Assuming getSymbol() exists
                        // }
                    } else {
                        Log.w("SymbolRepository", "Response successful BUT response.body() IS NULL after deserialization!");
                    }
                    watchlistData.setValue(symbols);
                } else {
                    Log.e("SymbolRepository", "Response not successful. Code: " + response.code());
                    watchlistData.setValue(null);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Symbol>> call, @NonNull Throwable t) {
                watchlistData.setValue(null); // Network error
            }
        });

        return watchlistData;
    }

}

