package models;

public class Plan {
    private String id; // Corresponds to Stripe Price ID
    private String name; // e.g., "Test Drive", "Starter Weekly", "Starter Monthly"
    private String displayName; // e.g., "Test Drive"
    private String priceText; // e.g., "£1.99"
    private String billingCycleText; // e.g., "Billed once", "Billed weekly"
    private String description;
    private String type; // "test_drive", "starter_weekly", "starter_monthly", etc.
    private boolean isSelected;
    private String savePercentageText; // e.g., "Save 10%"

    public Plan(String id, String name, String displayName, String priceText, String billingCycleText, String description, String type, String savePercentageText) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.priceText = priceText;
        this.billingCycleText = billingCycleText;
        this.description = description;
        this.type = type;
        this.savePercentageText = savePercentageText;
        this.isSelected = false;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() { return displayName; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getPriceText() {
        return priceText;
    }

    public void setPriceText(String priceText) {
        this.priceText = priceText;
    }

    public String getBillingCycleText() {
        return billingCycleText;
    }

    public void setBillingCycleText(String billingCycleText) {
        this.billingCycleText = billingCycleText;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    public String getSavePercentageText() { return savePercentageText; }

    public void setSavePercentageText(String savePercentageText) { this.savePercentageText = savePercentageText; }
}
