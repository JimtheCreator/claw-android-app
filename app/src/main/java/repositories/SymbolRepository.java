package repositories;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<Boolean> isSymbolSearchLoading = new MutableLiveData<>(false);

    public SymbolRepository(Application application) {
        api = MainClient.getApiService();
        AppDatabase database = AppDatabase.getInstance(application);
        this.symbolDao = database.symbolDao();
    }

    // A helper method to cache symbols in the background
    public void cacheSymbols(List<Symbol> symbols) {
        if (symbols == null || symbols.isEmpty()) return;
        databaseExecutor.execute(() -> {
            List<CachedSymbol> cachedSymbols = new ArrayList<>();
            for (Symbol symbol : symbols) {
                cachedSymbols.add(new CachedSymbol(symbol));
            }
            symbolDao.insertAll(cachedSymbols); // Assuming insertAll is an upsert
        });
    }

    public LiveData<List<Symbol>> searchCrypto(String query, int limit) {
        MutableLiveData<List<Symbol>> liveData = new MutableLiveData<>();

        if (currentSearchCall != null) {
            currentSearchCall.cancel();
        }

        if (searchCache.containsKey(query)) {
            liveData.postValue(searchCache.get(query));
            return liveData;
        }

        currentSearchCall = api.searchCrypto(query, limit);
        currentSearchCall.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<Symbol>> call, @NonNull Response<List<Symbol>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Symbol> symbols = response.body();
                    searchCache.put(query, symbols);
                    liveData.postValue(symbols);
                    cacheSymbols(symbols); // <-- CACHE THE RESULTS
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
            @Override
            public void onResponse(Call<List<Symbol>> call, Response<List<Symbol>> response) {
                if (response.isSuccessful()) {
                    List<Symbol> symbols = response.body();
                    watchlistData.setValue(symbols);
                } else {
                    Log.e("SymbolRepository", "Response not successful. Code: " + response.code());
                    watchlistData.setValue(null);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Symbol>> call, @NonNull Throwable t) {
                watchlistData.setValue(null);
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

    public LiveData<Symbol> fetchSymbolDetails(String symbolTicker) {
        MutableLiveData<Symbol> symbolData = new MutableLiveData<>();
        Call<List<Symbol>> call = api.searchCrypto(symbolTicker, 1); // Assuming this returns detailed data
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<List<Symbol>> call, Response<List<Symbol>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Symbol symbol = response.body().get(0);
                    symbolData.setValue(symbol);
                    cacheSymbols(Collections.singletonList(symbol)); // Cache the detailed symbol
                } else {
                    symbolData.setValue(null);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Symbol>> call, @NonNull Throwable t) {
                symbolData.setValue(null);
            }
        });
        return symbolData;
    }

    public List<Symbol> getCachedSymbolsByTickers(List<String> tickers) {
        List<CachedSymbol> cachedSymbols = symbolDao.getSymbolsByTickersSync(tickers);
        List<Symbol> symbols = new ArrayList<>();
        for (CachedSymbol cached : cachedSymbols) {
            symbols.add(cached.toSymbol());
        }
        return symbols;
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