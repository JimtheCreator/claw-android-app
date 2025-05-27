// data/remote/WebSocketServiceImpl.java
package data.remote;

import backend.WebSocketService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebSocketServiceImpl implements WebSocketService {

    // WebSocketServiceImpl.java
    private static final String WS_BASE_URL = "wss://stable-wholly-crappie.ngrok-free.app/api/v1/ws/market/cryptos/stream-market-data/";
    private final OkHttpClient client;
    private final OkHttpClient watchlistClient;
    private WebSocket webSocket, watchlistSocket;

    public WebSocketServiceImpl(OkHttpClient client, OkHttpClient watchlistClient) {
        this.client = client;
        this.watchlistClient = watchlistClient;
    }

    @Override
    public void connectToStream(String symbol, String interval, boolean include_ohlcv, WebSocketListener listener) {
        String url = WS_BASE_URL + symbol + "?interval=" + interval + "&include_ohlcv=" + include_ohlcv;
        Request request = new Request.Builder().url(url).build();

        webSocket = client.newWebSocket(request, listener);
    }

    @Override
    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "Normal closure");
            webSocket = null;
        }
    }
}
