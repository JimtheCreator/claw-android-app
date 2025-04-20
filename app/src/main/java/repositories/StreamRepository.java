package repositories;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import backend.WebSocketService;
import models.StreamMarketData;
import network.NetworkUtils;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import timber.log.Timber;

import org.json.JSONObject;
import android.content.Context;

import com.google.gson.Gson;

public class StreamRepository {
    private final WebSocketService webSocketService;
    private final Context context;
    private final MutableLiveData<StreamMarketData> marketData = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public StreamRepository(WebSocketService webSocketService, Context context) {
        this.webSocketService = webSocketService;
        this.context = context;
    }

    public LiveData<StreamMarketData> getMarketDataStream() {
        return marketData;
    }

    public LiveData<String> getErrorStream() {
        return error;
    }

    public void connect(String symbol, String interval, boolean include_ohlcv) {
        if (!NetworkUtils.isOnline(context)) {
            error.postValue("No internet connection");
            return;
        }

        webSocketService.connectToStream(symbol, interval, include_ohlcv, new WebSocketListener() {
            @Override
            // In StreamRepository.java
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                Timber.d("Raw message: %s", text); // Add for debugging
                try {
                    Gson gson = new Gson();
                    StreamMarketData data = gson.fromJson(text, StreamMarketData.class);
                    // Handle price updates
                    if (data.getPrice() != 0) {
                        marketData.postValue(data);
                        Timber.d("Received price update");
                    }
                    // Handle OHLCV updates
                    if (data.getOhlcv() != null) {
                        marketData.postValue(data);
                        Timber.d("Received OHLCV update");
                    }
                } catch (Exception e) {
                    error.postValue("Parse error: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
                error.postValue("Connection error: " + t.getMessage());
            }
        });
    }

    public void disconnect() {
        webSocketService.disconnect();
        marketData.postValue(null);
    }
}
