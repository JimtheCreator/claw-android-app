package viewmodels;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import backend.requests.AddWatchlistRequest;
import backend.requests.RemoveWatchlistRequest;
import backend.results.WatchlistUpdateResult;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import models.Symbol;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import repositories.SymbolRepository;
import retrofit2.Call;
import timber.log.Timber;


/**
 * ViewModel for handling cryptocurrency data operations and exposing data to the UI.
 */
public class HomeViewModel extends ViewModel {
    public static final String TAG = "HomeViewModel";
    private final MutableLiveData<WatchlistUpdateResult> watchlistUpdateResult = new MutableLiveData<>();
    private final MutableLiveData<List<Symbol>> watchlist = new MutableLiveData<>();
    private final Set<String> watchlistSymbolTickers = new HashSet<>(); // For quick lookup
    private final MutableLiveData<List<Symbol>> cryptoList = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    // For top cryptos pagination
    private int currentPage = 1;
    private final int PAGE_SIZE = 20;
    private final List<Symbol> allCryptos = new ArrayList<>();
    // Add these class variables
    private Call<List<Symbol>> currentSearchCall;
    private final SymbolRepository repository; // Add this
    private final MutableLiveData<List<Symbol>> searchResults = new MutableLiveData<>();
    private final CompositeDisposable disposables = new CompositeDisposable(); // For RxJava
    private WebSocket webSocketClient;
    private final OkHttpClient okHttpClient = new OkHttpClient();

    public HomeViewModel() {
        repository = new SymbolRepository();
    }

    private List<Symbol> getPaginatedData() {
        int end = Math.min(currentPage * PAGE_SIZE, allCryptos.size());
        return allCryptos.subList(0, end);
    }

    /**
     * Loads the next page of popular cryptocurrencies if available.
     */
    public void loadNextPage() {
        Boolean loading = isLoading.getValue();
        if (Boolean.TRUE.equals(loading) || (currentPage * PAGE_SIZE) >= allCryptos.size()) {
            return;
        }

        currentPage++;
        cryptoList.postValue(getPaginatedData());
    }

    // Add this method to check if more data is available
    public boolean hasMoreData() {
        return currentPage * PAGE_SIZE < allCryptos.size();
    }

    /**
     * Searches cryptocurrencies based on user query.
     *
     * @param query Search string input by user
     * @param limit Maximum number of search results
     */
    public void searchCryptos(String query, int limit) {
        isLoading.postValue(true);

        LiveData<List<Symbol>> liveData = repository.searchCrypto(query, limit);
        Observer<List<Symbol>> observer = new Observer<List<Symbol>>() {
            @Override
            public void onChanged(List<Symbol> results) {
                // Update search results even if null
                searchResults.postValue(results);
                isLoading.postValue(false); // Hide loading regardless

                // Clean up observer
                liveData.removeObserver(this);

                // Optionally handle errors
                if (results == null) {
                    errorMessage.postValue("Failed to fetch results");
                }
            }
        };

        liveData.observeForever(observer);
    }

    // --- Watchlist Operations ---
    public void loadWatchlist(String userId) {
        isLoading.postValue(true);
        LiveData<List<Symbol>> liveData = repository.getWatchlist(userId);
        liveData.observeForever(new Observer<>() {
            @Override
            public void onChanged(List<Symbol> symbolsFromApi) {
                isLoading.postValue(false);
                watchlistSymbolTickers.clear();
                List<Symbol> processedSymbols = new ArrayList<>();
                if (symbolsFromApi != null) {
                    for (Symbol s : symbolsFromApi) {
                        s.setInWatchlist(true);
                        watchlistSymbolTickers.add(s.getSymbol());
                        processedSymbols.add(s);
                    }
                    Collections.reverse(processedSymbols); // Reverse to show latest first
                }
                watchlist.postValue(processedSymbols);
                updateIsInWatchlistFlagForList(searchResults.getValue(), searchResults);
                liveData.removeObserver(this);
            }
        });
    }

    public void addToWatchlist(String userID, Symbol symbolToAdd, String source) {
        if (symbolToAdd.getSymbol() == null || symbolToAdd.getBaseCurrency() == null || symbolToAdd.getAsset() == null) {
            Timber.e("Cannot add to watchlist, symbol details missing: %s", symbolToAdd.getSymbol());
            watchlistUpdateResult.postValue(new WatchlistUpdateResult(symbolToAdd, false, true, new IllegalArgumentException("Symbol details missing")));
            return;
        }

        AddWatchlistRequest request = new AddWatchlistRequest(
                userID,
                symbolToAdd.getSymbol(),
                symbolToAdd.getBaseCurrency(),
                symbolToAdd.getAsset(),
                source
        );

        disposables.add(repository.addToWatchlist(request)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            Timber.d("API: Added %s to watchlist", symbolToAdd.getSymbol());
                            symbolToAdd.setInWatchlist(true);
                            watchlistSymbolTickers.add(symbolToAdd.getSymbol());

                            ArrayList<Symbol> currentWatchlist = new ArrayList<>(watchlist.getValue() != null ? watchlist.getValue() : Collections.emptyList());
                            if (currentWatchlist.stream().noneMatch(s -> s.getSymbol().equals(symbolToAdd.getSymbol()))) {
                                currentWatchlist.add(0, symbolToAdd); // Add to the beginning
                                watchlist.postValue(currentWatchlist);
                            }

                            updateIsInWatchlistFlagForList(searchResults.getValue(), searchResults);
                            watchlistUpdateResult.postValue(new WatchlistUpdateResult(symbolToAdd, true, true, null));
                        },
                        throwable -> {
                            Timber.e(throwable, "API: Failed to add %s to watchlist", symbolToAdd.getSymbol());
                            watchlistUpdateResult.postValue(new WatchlistUpdateResult(symbolToAdd, false, true, throwable));
                        }
                ));
    }

    public void removeFromWatchlist(String userID, String symbolTickerToRemove) {
        RemoveWatchlistRequest request = new RemoveWatchlistRequest(userID, symbolTickerToRemove);

        disposables.add(repository.removeFromWatchlist(request)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            Timber.d("API: Removed %s from watchlist", symbolTickerToRemove);
                            watchlistSymbolTickers.remove(symbolTickerToRemove);

                            List<Symbol> currentWatchlist = watchlist.getValue();
                            if (currentWatchlist != null) {
                                List<Symbol> updatedList = currentWatchlist.stream()
                                        .filter(s -> !s.getSymbol().equals(symbolTickerToRemove))
                                        .collect(Collectors.toList());
                                watchlist.postValue(updatedList);
                            }
                            updateIsInWatchlistFlagForList(searchResults.getValue(), searchResults);

                            // Use the String constructor - no need to create Symbol object
                            watchlistUpdateResult.postValue(new WatchlistUpdateResult(symbolTickerToRemove, true, false, null));
                        },
                        throwable -> {
                            Timber.e(throwable, "API: Failed to remove %s from watchlist", symbolTickerToRemove);

                            // Use the String constructor for error case too
                            watchlistUpdateResult.postValue(new WatchlistUpdateResult(symbolTickerToRemove, false, false, throwable));
                        }
                ));
    }

    public void connectToWatchlistWebSocket(String userId) {
        if (webSocketClient != null) {
            Log.i("WebSocketDebug", "Closing existing WebSocket before reconnecting.");
            webSocketClient.close(1000, "Reconnecting");
        }

        String wsUrl = "wss://stable-wholly-crappie.ngrok-free.app/api/v1/ws/watchlist/" + userId;
        Log.i("WebSocketDebug", "Connecting to WebSocket: " + wsUrl);

        Request request = new Request.Builder().url(wsUrl).build();
        webSocketClient = okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                Log.i("WebSocketDebug", "WebSocket connected: " + response);
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                Log.d("WebSocketDebug", "WebSocket Message: " + text);
                try {
                    JSONObject json = new JSONObject(text);
                    String type = json.optString("type");
                    Log.d("WebSocketDebug", "Message type: " + type);
                    switch (type) {
                        case "update":
                            String symbolTicker = json.getString("symbol");
                            double price = json.getDouble("price");
                            double change = json.getDouble("change");

                            // Extract sparkline data if present
                            List<Double> sparklineData = null;
                            if (json.has("sparkline")) {
                                sparklineData = parseSparklineData(json);
                            }

                            Log.d("WebSocketDebug", "Updating symbol: " + symbolTicker +
                                    " with price: " + price + " and change: " + change);

                            if (sparklineData != null) {
                                Log.d("WebSocketDebug", "Sparkline data points: " + sparklineData.size());
                            }

                            updateSymbolDataInLists(symbolTicker, price, change, sparklineData);
                            break;
                        case "init":
                            Log.i("WebSocketDebug", "WebSocket init message: " + json.optJSONArray("watchlist"));
                            break;
                        case "error":
                            Log.e("WebSocketDebug", "WebSocket server error: " + json.optString("message"));
                            break;
                    }
                } catch (Exception e) {
                    Log.e("WebSocketDebug", "Error parsing WebSocket message", e);
                }
            }

            @Override
            public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                Log.i("WebSocketDebug", "WebSocket Closing: " + code + " / " + reason);
            }

            @Override
            public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                Log.i("WebSocketDebug", "WebSocket Closed: " + code + " / " + reason);
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
                String message = response != null ? "WebSocket Failure: " + response.toString() : "WebSocket Failure";
                Log.e("WebSocketDebug", message, t);
            }
        });
    }

    private List<Double> parseSparklineData(JSONObject json) {
        List<Double> sparklineData = new ArrayList<>();
        try {
            JSONArray sparklineArray = json.getJSONArray("sparkline");
            for (int i = 0; i < sparklineArray.length(); i++) {
                sparklineData.add(sparklineArray.getDouble(i));
            }
        } catch (JSONException e) {
            Log.e("WebSocketDebug", "Error parsing sparkline data", e);
        }
        return sparklineData;
    }

    private void handleInitMessage(JSONObject json) {
        try {
            JSONArray watchlist = json.optJSONArray("watchlist");
            if (watchlist != null) {
                for (int i = 0; i < watchlist.length(); i++) {
                    JSONObject stockData = watchlist.getJSONObject(i);
                    String symbol = stockData.getString("symbol");
                    double price = stockData.getDouble("price");
                    double change = stockData.getDouble("change");

                    // Parse sparkline data for initial load
                    List<Double> sparklineData = null;
                    if (stockData.has("sparkline")) {
                        sparklineData = parseSparklineFromObject(stockData);
                    }

                    updateSymbolDataInLists(symbol, price, change, sparklineData);
                }
            }
        } catch (JSONException e) {
            Log.e("WebSocketDebug", "Error handling init message", e);
        }
    }

    private List<Double> parseSparklineFromObject(JSONObject stockData) {
        List<Double> sparklineData = new ArrayList<>();
        try {
            JSONArray sparklineArray = stockData.getJSONArray("sparkline");
            for (int i = 0; i < sparklineArray.length(); i++) {
                sparklineData.add(sparklineArray.getDouble(i));
            }
        } catch (JSONException e) {
            Log.e("WebSocketDebug", "Error parsing sparkline from stock object", e);
        }
        return sparklineData;
    }

    public void disconnectWebSocket() {
        if (webSocketClient != null) {
            Log.i("WebSocketDebug", "Disconnecting WebSocket.");
            webSocketClient.close(1000, "User initiated disconnect");
            webSocketClient = null;
        }
    }

    // --- Helper Methods ---

    /**
     * Updates the isInWatchlist flag for symbols in a given list and posts the list to its LiveData.
     */
    private void updateIsInWatchlistFlagForList(List<Symbol> listToUpdate, MutableLiveData<List<Symbol>> liveDataToPostTo) {
        if (listToUpdate == null || listToUpdate.isEmpty()) {
            if (listToUpdate != null && liveDataToPostTo.getValue() != listToUpdate) { // Post if it's a new empty list
                liveDataToPostTo.postValue(listToUpdate);
            }
            return;
        }
        boolean changed = false;
        for (Symbol symbol : listToUpdate) {
            boolean oldStatus = symbol.isInWatchlist();
            boolean newStatus = watchlistSymbolTickers.contains(symbol.getSymbol());
            if (oldStatus != newStatus) {
                symbol.setInWatchlist(newStatus);
                changed = true;
            }
        }
        // Post the original list instance if it was modified, or if it's a new list instance.
        // DiffUtil works best if a new list instance is posted when content changes.
        if (changed) {
            liveDataToPostTo.postValue(new ArrayList<>(listToUpdate));
        } else if (liveDataToPostTo.getValue() != listToUpdate) { // If it's a different list instance (e.g. fresh from API)
            liveDataToPostTo.postValue(listToUpdate);
        }
    }

    /**
     * Updates price, change, and optionally sparkline for a symbol across all relevant LiveData lists.
     */
    private void updateSymbolDataInLists(String symbolTicker, double newPrice, double newChange, @Nullable List<Double> newSparkline) {
        Log.d("WebSocketDebug", "Updating symbol " + symbolTicker + " in lists");
        updateSingleList(watchlist.getValue(), watchlist, symbolTicker, newPrice, newChange, newSparkline);
        updateSingleList(searchResults.getValue(), searchResults, symbolTicker, newPrice, newChange, newSparkline);
        // Add for cryptoList if it exists and needs updates
    }

    private void updateSingleList(List<Symbol> list, MutableLiveData<List<Symbol>> liveData, String symbolTicker, double newPrice, double newChange, @Nullable List<Double> newSparkline) {
        if (list == null) return;
        boolean mutated = false;
        for (Symbol s : list) {
            if (s.getSymbol().equals(symbolTicker)) {
                s.setPrice(newPrice);
                s.setChange(newChange);
                if (newSparkline != null) { // Only update sparkline if new data is provided
                    s.setSparkline(newSparkline);
                }
                mutated = true;
                break;
            }
        }
        if (mutated) {
            liveData.postValue(new ArrayList<>(list)); // Post new list to trigger observers with DiffUtil
        }
    }

    // --- LiveData Getters ---
    public LiveData<List<Symbol>> getWatchlist() {
        return watchlist;
    }

    public LiveData<List<Symbol>> getSearchResults() {
        return searchResults;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<WatchlistUpdateResult> getWatchlistUpdateResult() {
        return watchlistUpdateResult;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear();
        disconnectWebSocket();
        // OkHttp's dispatcher has its own lifecycle, but can be shut down if you created a custom one.
        // For the default shared dispatcher, explicit shutdown is not always necessary here.
        // okHttpClient.dispatcher().executorService().shutdown();
    }

}