package backend.requests;

import com.google.gson.annotations.SerializedName;

public class CreatePatternAlertRequest {

    @SerializedName("symbol")
    private String symbol;

    @SerializedName("pattern_name")
    private String patternName;

    @SerializedName("time_interval")
    private String timeInterval;

    @SerializedName("pattern_state")
    private String patternState;

    @SerializedName("notification_method")
    private String notificationMethod;

    public CreatePatternAlertRequest(String symbol, String patternName, String timeInterval, String patternState, String notificationMethod) {
        this.symbol = symbol;
        this.patternName = patternName;
        this.timeInterval = timeInterval;
        this.patternState = patternState;
        this.notificationMethod = notificationMethod;

    }
}
