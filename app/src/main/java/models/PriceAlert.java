package models;

import com.google.gson.annotations.SerializedName;

import java.sql.Timestamp;

// Data classes
public class PriceAlert {
    @SerializedName("id")
    private String id;
    @SerializedName("user_id")
    private String userId;
    @SerializedName("symbol")
    private String symbol;
    @SerializedName("condition_type")
    private String conditionType; // "above" or "below"
    @SerializedName("condition_value")
    private double conditionValue;
    @SerializedName("status")
    private String status; // "active", "triggered", "cancelled"
    @SerializedName("created_at")
    private Timestamp createdAt;
    @SerializedName("updated_at")
    private String updatedAt;

    // Constructors
    public PriceAlert() {}

    public PriceAlert(String id, String userId, String symbol, String conditionType, double conditionValue, String status, Timestamp createdAt, String updatedAt) {
        this.id = id;
        this.userId = userId;
        this.symbol = symbol;
        this.conditionType = conditionType;
        this.conditionValue = conditionValue;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getConditionType() {
        return conditionType;
    }

    public void setConditionType(String conditionType) {
        this.conditionType = conditionType;
    }

    public double getConditionValue() {
        return conditionValue;
    }

    public void setConditionValue(double conditionValue) {
        this.conditionValue = conditionValue;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
