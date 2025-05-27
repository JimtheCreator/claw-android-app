package backend.requests;

import com.google.gson.annotations.SerializedName;

public class RemoveWatchlistRequest {
    @SerializedName("user_id")
    String userID;
    String symbol;

    public RemoveWatchlistRequest(String userID, String symbol) {
        this.userID = userID;
        this.symbol = symbol;
    }
}
