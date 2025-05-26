package backend.requests;

// Data Classes
public class WatchlistAddRequestJ {
    public String user_id;
    public String symbol;
    public String base_asset;
    public String quote_asset;
    public String source;

    public WatchlistAddRequestJ(String user_id, String symbol, String base_asset, String quote_asset, String source) {
        this.user_id = user_id;
        this.symbol = symbol;
        this.base_asset = base_asset;
        this.quote_asset = quote_asset;
        this.source = source;
    }
}
