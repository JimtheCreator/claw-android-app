// data/remote/WebSocketServiceImpl.java
package data.remote;

import androidx.annotation.NonNull;

import backend.WebSocketService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import java.util.concurrent.TimeUnit;

public class WebSocketServiceImpl implements WebSocketService {

    private static final String WS_BASE_URL = "wss://stable-wholly-crappie.ngrok-free.app/api/v1/ws/market/cryptos/stream-market-data/";
    private final OkHttpClient client;
    private final OkHttpClient watchlistClient;
    private WebSocket webSocket, watchlistSocket;

    public WebSocketServiceImpl(OkHttpClient client, OkHttpClient watchlistClient) {
        // Configure OkHttpClient with proper timeouts for WebSocket
        this.client = client.newBuilder()
                .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket reads
                .writeTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(30, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS) // Send ping every 30 seconds
                .build();
        this.watchlistClient = watchlistClient;
    }

    @Override
    public void connectToStream(String symbol, String interval, boolean include_ohlcv, WebSocketListener listener) {
        // Close existing connection first
        disconnect();

        String url = WS_BASE_URL + symbol + "?interval=" + interval + "&include_ohlcv=" + include_ohlcv;
        System.out.println("Connecting to WebSocket: " + url);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull okhttp3.Response response) {
                System.out.println("WebSocket connected successfully to: " + url);
                System.out.println("Response code: " + response.code());
                System.out.println("Response headers: " + response.headers());
                listener.onOpen(webSocket, response);
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                System.out.println("Received WebSocket message: " + text);
                listener.onMessage(webSocket, text);
            }

            @Override
            public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                System.out.println("WebSocket closing: " + code + " - " + reason);
                listener.onClosing(webSocket, code, reason);
            }

            @Override
            public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                System.out.println("WebSocket closed: " + code + " - " + reason);
                listener.onClosed(webSocket, code, reason);
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, okhttp3.Response response) {
                System.out.println("WebSocket connection failed: " + t.getMessage());
                if (response != null) {
                    System.out.println("Response code: " + response.code());
                    System.out.println("Response message: " + response.message());
                    System.out.println("Response headers: " + response.headers());
                    try {
                        String responseBody = response.body() != null ? response.body().string() : "No body";
                        System.out.println("Response body: " + responseBody);
                    } catch (Exception e) {
                        System.out.println("Could not read response body: " + e.getMessage());
                    }
                }
                t.printStackTrace();
                listener.onFailure(webSocket, t, response);
            }
        });
    }

    @Override
    public void disconnect() {
        if (webSocket != null) {
            System.out.println("Disconnecting WebSocket");
            webSocket.close(1000, "Normal closure");
            webSocket = null;
        }
        if (watchlistSocket != null) {
            System.out.println("Disconnecting Watchlist WebSocket");
            watchlistSocket.close(1000, "Normal closure");
            watchlistSocket = null;
        }
    }
}
