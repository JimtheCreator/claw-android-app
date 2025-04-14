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
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                try {
                    Gson gson = new Gson();
                    StreamMarketData data = gson.fromJson(text, StreamMarketData.class);
                    // Validate data (assuming timestamp is a required field)
                    if (data != null && data.getTimestamp() != 0) {
                        marketData.postValue(data);
                    }
                } catch (Exception e) {
                    error.postValue("Failed to parse market data");
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
