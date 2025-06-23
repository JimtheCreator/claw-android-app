package repositories;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import backend.ApiService;
import backend.SymbolDao;
import backend.MainClient;
import backend.requests.AddWatchlistRequest;
import backend.requests.RemoveWatchlistRequest;
import database.roomDB.AppDatabase;
import io.reactivex.Completable;
import models.CachedSymbol;
import models.Symbol;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SymbolRepository {
    private final ApiService api;
    private final Map<String, List<Symbol>> searchCache = new ConcurrentHashMap<>();
    private Call<List<Symbol>> currentSearchCall;
    private final SymbolDao symbolDao;

    // LiveData to hold the loading state for symbol search
    private final MutableLiveData<Boolean> isSymbolSearchLoading = new MutableLiveData<>(false);

    public SymbolRepository(Application application) {
        api = MainClient.getApiService();
        AppDatabase database = AppDatabase.getInstance(application);
        this.symbolDao = database.symbolDao();
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
        call.enqueue(new Callback<>() {
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

    public LiveData<List<CachedSymbol>> searchCachedSymbols(String query) {
        // Immediately fetch from the local database and return as LiveData.
        LiveData<List<CachedSymbol>> localResults = symbolDao.searchSymbols("%" + query + "%");

        // Check if local results exist before fetching from network
        checkLocalResultsAndFetch(query, localResults);

        return localResults;
    }

    private void checkLocalResultsAndFetch(String query, LiveData<List<CachedSymbol>> localResults) {
        // Observe the local results once to check if they exist
        localResults.observeForever(new Observer<>() {
            @Override
            public void onChanged(List<CachedSymbol> symbols) {
                // Remove observer immediately to avoid memory leaks
                localResults.removeObserver(this);

                // Only fetch from network if no local results found
                if (symbols == null || symbols.isEmpty()) {
                    fetchFromNetworkAndCache(query);
                }
            }
        });
    }

    private void fetchFromNetworkAndCache(String query) {
        // Set loading state to true before starting the network call
        isSymbolSearchLoading.postValue(true);

        new Thread(() -> {
            try {
                Response<List<CachedSymbol>> response = api.searchSymbols(query).execute();
                if (response.isSuccessful() && response.body() != null) {
                    symbolDao.insertAll(response.body());
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                // Set loading state to false once the operation is complete
                isSymbolSearchLoading.postValue(false);
            }
        }).start();
    }

    // Expose the loading state LiveData to the ViewModel
    public LiveData<Boolean> isSymbolSearchLoading() {
        return isSymbolSearchLoading;
    }
}

