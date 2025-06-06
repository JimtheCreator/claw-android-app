package models;

// Data classes
public class PriceAlert {
    private int id;
    private String userId;
    private String symbol;
    private String conditionType; // "above" or "below"
    private double conditionValue;
    private String status; // "active", "triggered", "cancelled"
    private String createdAt;
    private String updatedAt;

    // Constructors
    public PriceAlert() {}

    public PriceAlert(int id, String userId, String symbol, String conditionType,
                      double conditionValue, String status, String createdAt, String updatedAt) {
        this.id = id;
        this.userId = userId;
        this.symbol = symbol;
        this.conditionType = conditionType;
        this.conditionValue = conditionValue;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getConditionType() { return conditionType; }
    public void setConditionType(String conditionType) { this.conditionType = conditionType; }

    public double getConditionValue() { return conditionValue; }
    public void setConditionValue(double conditionValue) { this.conditionValue = conditionValue; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
