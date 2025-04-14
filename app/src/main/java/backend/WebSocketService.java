// backend/WebSocketService.java
package backend;

import okhttp3.WebSocketListener;

public interface WebSocketService {
    void connectToStream(String symbol, String interval, boolean include_ohclv, WebSocketListener listener);
    void disconnect();
}
