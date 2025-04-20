package models;

import java.util.Date;

public class MarketDataEntity {
    private Date timestamp;

    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;

    // Add getters
    public Date getTimestamp() { return timestamp; }
    public double getOpen() { return open; }
    public double getHigh() { return high; }
    public double getLow() { return low; }
    public double getClose() { return close; }
    public double getVolume() { return volume; }
}
