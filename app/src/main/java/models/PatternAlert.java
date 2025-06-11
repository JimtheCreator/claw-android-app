package models;

import com.google.gson.annotations.SerializedName;

public class PatternAlert {
    @SerializedName("id")
    private String id;

    @SerializedName("user_id")
    private String userId;

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

    @SerializedName("status")
    private String status;

    @SerializedName("created_at")
    private String createdAt;

    // Getters
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getSymbol() { return symbol; }
    public String getPatternName() { return patternName; }
    public String getTimeInterval() { return timeInterval; }
    public String getPatternState() { return patternState; }
    public String getNotificationMethod() { return notificationMethod; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
}
