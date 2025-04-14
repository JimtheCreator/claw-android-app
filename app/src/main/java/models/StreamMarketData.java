// model/StreamMarketData.java
package models;

import com.google.gson.annotations.SerializedName;

import java.util.List;


// models/StreamMarketData.java
public class StreamMarketData {
    @SerializedName("price")
    private double price;

    @SerializedName("change")
    private double change;

    @SerializedName("timestamp")
    private long timestamp;
    private Ohlcv ohlcv;

    // Add nested Ohlcv class
    public static class Ohlcv {
        private long open_time;
        private double open;
        private double high;
        private double low;
        private double close;
        private double volume;

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
    public long getTimestamp() { return timestamp; }
}
