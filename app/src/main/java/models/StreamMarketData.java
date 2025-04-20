// model/StreamMarketData.java
package models;

import com.google.gson.annotations.SerializedName;

import java.util.List;


// models/StreamMarketData.java
public class StreamMarketData {


    @SerializedName("type")
    private String type;
    @SerializedName("price")
    private double price;

    @SerializedName("change")
    private double change;

    @SerializedName("ohlcv") // Ensure this matches the JSON key
    private Ohlcv ohlcv;


    // Add nested Ohlcv class
    public static class Ohlcv {
        @SerializedName("open_time")
        private long open_time;

        @SerializedName("open")
        private double open;

        // Add annotations for all fields
        @SerializedName("high")
        private double high;

        @SerializedName("low")
        private double low;

        @SerializedName("close")
        private double close;

        @SerializedName("volume")
        private double volume;

        @SerializedName("is_closed")
        private boolean is_closed;

        // Add getter for is_closed
        public boolean isClosed() {
            return is_closed;
        }

        // Getters and setters
        public long getOpenTime() { return open_time; }
        public double getOpen() { return open; }
        public double getHigh() { return high; }
        public double getLow() { return low; }
        public double getClose() { return close; }
        public double getVolume() { return volume; }
    }

    // Existing getters/setters
    public Ohlcv getOhlcv() { return ohlcv; }
    public double getPrice() { return price; }
    public double getChange() { return change; }

    public String getType() {
        return type;
    }
}
