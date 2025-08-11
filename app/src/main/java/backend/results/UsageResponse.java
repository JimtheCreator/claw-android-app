package backend.results;

import com.google.gson.annotations.SerializedName;

import models.UsageData;

public class UsageResponse {

    @SerializedName("usage")
    private UsageData usage;

    // While we don't use the limits from the API in the fragment currently,
    // it's good practice to include it for future use.
    // @SerializedName("limits")
    // private Map<String, Object> limits; // Or a strongly-typed LimitData class

    public UsageData getUsage() {
        return usage;
    }

    public void setUsage(UsageData usage) {
        this.usage = usage;
    }
}