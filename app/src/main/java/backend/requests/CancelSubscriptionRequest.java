package backend.requests;

import com.google.gson.annotations.SerializedName;

public class CancelSubscriptionRequest {
    @SerializedName("user_id")
    private String userId;
    @SerializedName("subscription_id")
    private String subscriptionId;
    @SerializedName("cancel_at_period_end")
    private Boolean cancelAtPeriodEnd;

    public CancelSubscriptionRequest(String userId, String subscriptionId, Boolean cancelAtPeriodEnd) {
        this.userId = userId;
        this.subscriptionId = subscriptionId;
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
    }

    // Getters and setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(String subscriptionId) { this.subscriptionId = subscriptionId; }
    public Boolean getCancelAtPeriodEnd() { return cancelAtPeriodEnd; }
    public void setCancelAtPeriodEnd(Boolean cancelAtPeriodEnd) { this.cancelAtPeriodEnd = cancelAtPeriodEnd; }
}