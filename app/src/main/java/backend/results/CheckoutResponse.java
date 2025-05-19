package backend.results;

import com.google.gson.annotations.SerializedName;

public class CheckoutResponse {
    @SerializedName("checkout_url")
    private String checkoutUrl;

    // Getter
    public String getCheckoutUrl() {
        return checkoutUrl;
    }
}
