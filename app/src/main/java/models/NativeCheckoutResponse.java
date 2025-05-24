package models;

import com.google.gson.annotations.SerializedName;

public class NativeCheckoutResponse {
    @SerializedName("client_secret")
    private String clientSecret;

    @SerializedName("publishable_key")
    private String publishableKey;

    @SerializedName("intent_type") // ADDED
    private String intentType; // "payment_intent" or "setup_intent"

    @SerializedName("customer_id")
    private String customerId;

    @SerializedName("ephemeral_key_secret")
    private String ephemeralKeySecret;

    @SerializedName("payment_intent_id")
    private String paymentIntentId;

    @SerializedName("setup_intent_id") // ADDED
    private String setupIntentId;

    @SerializedName("subscription_id")
    private String subscriptionId;

    @SerializedName("plan_type")
    private String planType;

    @SerializedName("mode")
    private String mode;
    @SerializedName("payment_required")
    private boolean paymentRequired;
    @SerializedName("message")
    private String message;

    // Getters
    public String getClientSecret() { return clientSecret; }
    public String getPublishableKey() { return publishableKey; }
    public String getIntentType() { return intentType; } // ADDED
    public String getCustomerId() { return customerId; }
    public String getEphemeralKeySecret() { return ephemeralKeySecret; }
    public String getPaymentIntentId() { return paymentIntentId; }
    public String getSetupIntentId() { return setupIntentId; } // ADDED
    public String getSubscriptionId() { return subscriptionId; }
    public String getPlanType() { return planType; }
    public String getMode() { return mode; }
    public boolean isPaymentRequired() { return paymentRequired; }
    public String getMessage() { return message; }
}
