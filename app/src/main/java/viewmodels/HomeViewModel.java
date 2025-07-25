package viewmodels;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

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
import kotlinx.coroutines.CoroutineScope;
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
public class HomeViewModel extends ViewModel implements LifecycleEventObserver {
    public static final String TAG = "HomeViewModel";
    private final MutableLiveData<WatchlistUpdateResult> watchlistUpdateResult = new MutableLiveData<>();
    private final MutableLiveData<List<Symbol>> watchlist = new MutableLiveData<>();
    private final Set<String> watchlistSymbolTickers = new HashSet<>(); // For quick lookup
    private final MutableLiveData<List<Symbol>> cryptoList = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isWatchlistLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    // For top cryptos pagination
    private int currentPage = 1;
    private final int PAGE_SIZE = 20;
    private final List<Symbol> allCryptos = new ArrayList<>();
    private Call<List<Symbol>> currentSearchCall;
    private final SymbolRepository repository;
    private boolean inBackground = false;
    private final MutableLiveData<List<Symbol>> searchResults = new MutableLiveData<>();
    private final CompositeDisposable disposables = new CompositeDisposable(); // For RxJava
    private WebSocket webSocketClient;
    private final OkHttpClient okHttpClient = new OkHttpClient();

    // Added for subscription type and limit checking
    private String subscriptionType;

    public HomeViewModel(@NonNull Application application) {
        repository = new SymbolRepository(application);
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

    public boolean hasMoreData() {
        return currentPage * PAGE_SIZE < allCryptos.size();
    }

    /**
     * Searches cryptocurrencies based on user query.
     */
    public void searchCryptos(String query, int limit) {
        isLoading.postValue(true);
        LiveData<List<Symbol>> liveData = repository.searchCrypto(query, limit);
        Observer<List<Symbol>> observer = new Observer<List<Symbol>>() {
            @Override
            public void onChanged(List<Symbol> results) {
                if (results != null) {
                    for (Symbol symbol : results) {
                        symbol.setInWatchlist(watchlistSymbolTickers.contains(symbol.getSymbol()));
                    }
                    searchResults.postValue(results);
                } else {
                    searchResults.postValue(null);
                }
                isLoading.postValue(false);
                liveData.removeObserver(this);
                if (results == null) {
                    errorMessage.postValue("Failed to fetch results");
                }
            }
        };
        liveData.observeForever(observer);
    }

    public void loadWatchlist(String userId) {
        isWatchlistLoading.postValue(true);
        LiveData<List<Symbol>> liveData = repository.getWatchlist(userId);
        liveData.observeForever(new Observer<>() {
            @Override
            public void onChanged(List<Symbol> symbolsFromApi) {
                isWatchlistLoading.postValue(false);
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
                fetchSubscriptionType(userId); // Fetch subscription type after loading watchlist
                liveData.removeObserver(this);
            }
        });
    }

    /**
     * Fetches the user's subscription type from Firebase Realtime Database.
     */
    private void fetchSubscriptionType(String userId) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    subscriptionType = snapshot.child("subscriptionType").getValue(String.class);
                    Timber.d("Subscription type fetched: %s", subscriptionType);
                } else {
                    subscriptionType = "free"; // Default to free plan if not found
                    Timber.w("No subscription type found for user %s, defaulting to free", userId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                subscriptionType = "free"; // Default to free plan on error
                Timber.e(error.toException(), "Failed to fetch subscription type for user %s", userId);
            }
        });
    }

    /**
     * Returns the watchlist limit based on the user's subscription type.
     */
    private int getWatchlistLimit(String subscriptionType) {
        if (subscriptionType == null) return 0; // Default to no additions if unknown
        switch (subscriptionType) {
            case "test_drive":
                return 1;
            case "starter_weekly":
                return 3;
            case "starter_monthly":
                return 6;
            case "pro_weekly":
                return -1; // Unlimited
            case "pro_monthly":
                return -1; // Unlimited
            case "free":
                return 1;
            default:
                return 0; // Unknown plans get no watchlist
        }
    }

    public void addToWatchlist(String userID, Symbol symbolToAdd, String source) {
        // Check subscription type and watchlist limit first
        if (subscriptionType == null) {
            errorMessage.postValue("Subscription type not loaded yet.");
            Timber.w("Attempted to add to watchlist before subscription type loaded");
            return;
        }

        int limit = getWatchlistLimit(subscriptionType);
        List<Symbol> currentWatchlist = watchlist.getValue();
        int currentSize = currentWatchlist != null ? currentWatchlist.size() : 0;

        // If limit is not unlimited (-1) and current size meets or exceeds limit
        if (limit != -1 && currentSize >= limit) {
            errorMessage.postValue("Watchlist limit reached for your plan.");
            Timber.d("Watchlist limit reached: %d/%d for plan %s", currentSize, limit, subscriptionType);
            return;
        }

        // Proceed with adding if limit not reached
        if (symbolToAdd.getSymbol() == null || symbolToAdd.getBaseCurrency() == null || symbolToAdd.getAsset() == null) {
            Timber.e("Cannot add to watchlist, symbol details missing: %s", symbolToAdd.getSymbol());
            watchlistUpdateResult.postValue(new WatchlistUpdateResult(symbolToAdd, false, true, new IllegalArgumentException("Symbol details missing")));
            return;
        }

        AddWatchlistRequest request = new AddWatchlistRequest(userID, symbolToAdd.getSymbol(), symbolToAdd.getBaseCurrency(), symbolToAdd.getAsset(), source);

        disposables.add(repository.addToWatchlist(request)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    Timber.d("API: Added %s to watchlist", symbolToAdd.getSymbol());
                    symbolToAdd.setInWatchlist(true);
                    watchlistSymbolTickers.add(symbolToAdd.getSymbol());

                    ArrayList<Symbol> updatedWatchlist = new ArrayList<>(currentWatchlist != null ? currentWatchlist : Collections.emptyList());

                    if (updatedWatchlist.stream().noneMatch(s -> s.getSymbol().equals(symbolToAdd.getSymbol()))) {
                        updatedWatchlist.add(0, symbolToAdd);
                        watchlist.postValue(updatedWatchlist);
                    }

                    updateIsInWatchlistFlagForList(searchResults.getValue(), searchResults);
                    watchlistUpdateResult.postValue(new WatchlistUpdateResult(symbolToAdd, true, true, null));

                    // Disconnect and reconnect WebSocket to refresh the watchlist
                    disconnectWebSocket();
                    connectToWatchlistWebSocket(userID);
                }, throwable -> {
                    Timber.e(throwable, "API: Failed to add %s to watchlist", symbolToAdd.getSymbol());
                    watchlistUpdateResult.postValue(new WatchlistUpdateResult(symbolToAdd, false, true, throwable));
                }));
    }

    public void removeFromWatchlist(String userID, String symbolTickerToRemove) {
        RemoveWatchlistRequest request = new RemoveWatchlistRequest(userID, symbolTickerToRemove);

        disposables.add(repository.removeFromWatchlist(request)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
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
                    watchlistUpdateResult.postValue(new WatchlistUpdateResult(symbolTickerToRemove, true, false, null));

                    // Disconnect and reconnect WebSocket to refresh the watchlist
                    disconnectWebSocket();
                    connectToWatchlistWebSocket(userID);
                }, throwable -> {
                    Timber.e(throwable, "API: Failed to remove %s from watchlist", symbolTickerToRemove);
                    watchlistUpdateResult.postValue(new WatchlistUpdateResult(symbolTickerToRemove, false, false, throwable));
                }));
    }

    public void connectToWatchlistWebSocket(String userId) {
        if (webSocketClient == null) {
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
                    try {
                        JSONObject json = new JSONObject(text);
                        String type = json.optString("type");
                        switch (type) {
                            case "init":
                                handleInitMessage(json);
                                break;
                            case "update":
                                String symbol = json.getString("symbol");
                                double price = json.getDouble("price");
                                double change = json.getDouble("change");
                                updateSymbolDataInLists(symbol, price, change, null); // No sparkline updates
                                break;
                            case "error":
                                errorMessage.postValue(json.optString("message"));
                                break;
                        }
                    } catch (Exception e) {
                        Log.e("WebSocketDebug", "Error parsing message", e);
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

    }

    private void handleInitMessage(JSONObject json) {
        try {
            JSONArray watchlistArray = json.optJSONArray("watchlist");
            if (watchlistArray != null) {
                List<Symbol> newWatchlist = new ArrayList<>();
                // Loop from the end to the start
                for (int i = watchlistArray.length() - 1; i >= 0; i--) {
                    JSONObject stockData = watchlistArray.getJSONObject(i);
                    String symbol = stockData.getString("symbol");
                    String asset = stockData.optString("asset", "");
                    String baseCurrency = stockData.optString("baseCurrency", "");
                    double price = stockData.getDouble("price");
                    double change = stockData.getDouble("change");
                    List<Double> sparklineData = parseSparklineFromObject(stockData);
                    Symbol s = new Symbol(symbol, asset, "", baseCurrency, price, change, price, change, 0.0, sparklineData, true);
                    newWatchlist.add(s);
                }
                watchlist.postValue(newWatchlist);
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
    private void updateIsInWatchlistFlagForList(List<Symbol> listToUpdate, MutableLiveData<List<Symbol>> liveDataToPostTo) {
        if (listToUpdate == null || listToUpdate.isEmpty()) {
            if (listToUpdate != null && liveDataToPostTo.getValue() != listToUpdate) {
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
        if (changed) {
            liveDataToPostTo.postValue(new ArrayList<>(listToUpdate));
        } else if (liveDataToPostTo.getValue() != listToUpdate) {
            liveDataToPostTo.postValue(listToUpdate);
        }
    }

    private void updateSymbolDataInLists(String symbolTicker, double newPrice, double newChange, @Nullable List<Double> newSparkline) {
        Log.d("WebSocketDebug", "Updating symbol " + symbolTicker + " in lists");
        updateSingleList(watchlist.getValue(), watchlist, symbolTicker, newPrice, newChange, newSparkline);
        updateSingleList(searchResults.getValue(), searchResults, symbolTicker, newPrice, newChange, newSparkline);
    }

    private void updateSingleList(List<Symbol> list, MutableLiveData<List<Symbol>> liveData, String symbolTicker, double newPrice, double newChange, @Nullable List<Double> newSparkline) {
        if (list == null) return;
        for (Symbol s : list) {
            if (s.getSymbol().equals(symbolTicker)) {
                s.setPrice(newPrice);
                s.setChange(newChange);
                if (newSparkline != null) {
                    s.setSparkline(newSparkline);
                }
                break;
            }
        }
        liveData.postValue(list);
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

    public LiveData<Boolean> getIsWatchlistLoading() {
        return isWatchlistLoading;
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
    }

    /**
     * @param lifecycleOwner
     * @param event
     */
    @Override
    public void onStateChanged(@NonNull LifecycleOwner lifecycleOwner, @NonNull Lifecycle.Event event) {
        if (event == Lifecycle.Event.ON_PAUSE) {
            handleBackgroundState();
        } else if (event == Lifecycle.Event.ON_RESUME) {
            handleForegroundState();
        }
    }

    private void handleBackgroundState() {
        // Pause WebSocket updates
        setInBackground(true);
        Timber.d("App moved to background");
    }

    private void handleForegroundState() {
        // Resume WebSocket updates
        setInBackground(false);
        Timber.d("App returned to foreground");
    }

    public void setInBackground(boolean inBackground) {
        this.inBackground = inBackground;
    }

}