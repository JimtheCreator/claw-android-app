package backend.results;

public class CancellationResponseSchema {
    private boolean success;
    private String subscriptionId;
    private String cancellationStatus;
    private String cancellationDate;
    private String message;

    // Getters and setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(String subscriptionId) { this.subscriptionId = subscriptionId; }
    public String getCancellationStatus() { return cancellationStatus; }
    public void setCancellationStatus(String cancellationStatus) { this.cancellationStatus = cancellationStatus; }
    public String getCancellationDate() { return cancellationDate; }
    public void setCancellationDate(String cancellationDate) { this.cancellationDate = cancellationDate; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
