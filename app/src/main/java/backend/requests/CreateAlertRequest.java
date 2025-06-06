package backend.requests;

import com.google.gson.annotations.SerializedName;

public class CreateAlertRequest {
    @SerializedName("user_id")
    public String user_id;
    @SerializedName("symbol")
    public String symbol;
    @SerializedName("condition_type")
    public String condition_type;
    @SerializedName("condition_value")
    public double condition_value;

    public CreateAlertRequest(String user_id, String symbol, String condition_type, double condition_value) {
        this.user_id = user_id;
        this.symbol = symbol;
        this.condition_type = condition_type;
        this.condition_value = condition_value;
    }
}
