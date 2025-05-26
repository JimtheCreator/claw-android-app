package backend.requests;

public class WatchlistRemoveRequest {
    public String user_id;
    public String symbol;

    public WatchlistRemoveRequest(String user_id, String symbol) {
        this.user_id = user_id;
        this.symbol = symbol;
    }
}