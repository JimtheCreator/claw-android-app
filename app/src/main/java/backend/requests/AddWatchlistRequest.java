package backend.requests;

import com.google.gson.annotations.SerializedName;

public class AddWatchlistRequest {
    @SerializedName("user_id")
    public String userId;
    @SerializedName("symbol")
    public String symbol;
    @SerializedName("base_asset")
    public String baseAsset;
    @SerializedName("quote_asset")
    public String quoteAsset;
    public String source;

    public AddWatchlistRequest(String userId, String symbol, String baseAsset, String quoteAsset, String source) {
        this.userId = userId;
        this.symbol = symbol;
        this.baseAsset = baseAsset;
        this.quoteAsset = quoteAsset;
        this.source = source;
    }
}
