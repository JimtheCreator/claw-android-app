package backend.requests;


import com.google.gson.annotations.SerializedName;

public class SubscribeRequest {
    @SerializedName("user_id")
    private final String userId;

    @SerializedName("plan_id")
    private final String planId; // This is the Stripe Price ID

    public SubscribeRequest(String userId, String planId) {
        this.userId = userId;
        this.planId = planId;
    }

    // Getters (and setters if needed)
    public String getUserId() {
        return userId;
    }

    public String getPlanId() {
        return planId;
    }
}
