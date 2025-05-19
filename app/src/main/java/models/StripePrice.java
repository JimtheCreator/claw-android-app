package models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class StripePrice {
    @SerializedName("id")
    private String id;

    @SerializedName("type") // This is 'plan_type' in your backend metadata
    private String planType;

    @SerializedName("billing_period")
    private String billingPeriod; // "week", "month", "one_time"

    @SerializedName("amount")
    private long amount; // in smallest currency unit (e.g., pence)

    @SerializedName("currency")
    private String currency;

    @SerializedName("name")
    private String name; // Product name from Stripe

    @SerializedName("description")
    private String description; // Product description

    @SerializedName("features")
    private List<String> features;

    // Getters
    public String getId() { return id; }
    public String getPlanType() { return planType; }
    public String getBillingPeriod() { return billingPeriod; }
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<String> getFeatures() { return features; }

    // You might want to add setters if needed, or use a constructor
}