package org.example.model;

import java.time.LocalDateTime;

/**
 * Model class representing a single intraday candle (1-min, 5-min, etc.)
 */
public class IntradayCandle {

    private String symbol;
    private LocalDateTime timestamp;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
    private int intervalMinutes;  // 1, 5, 15, etc.

    public IntradayCandle() {}

    public IntradayCandle(String symbol, LocalDateTime timestamp, double open, double high,
                          double low, double close, long volume, int intervalMinutes) {
        this.symbol = symbol;
        this.timestamp = timestamp;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.intervalMinutes = intervalMinutes;
    }

    // Getters and Setters
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public double getOpen() { return open; }
    public void setOpen(double open) { this.open = open; }

    public double getHigh() { return high; }
    public void setHigh(double high) { this.high = high; }

    public double getLow() { return low; }
    public void setLow(double low) { this.low = low; }

    public double getClose() { return close; }
    public void setClose(double close) { this.close = close; }

    public long getVolume() { return volume; }
    public void setVolume(long volume) { this.volume = volume; }

    public int getIntervalMinutes() { return intervalMinutes; }
    public void setIntervalMinutes(int intervalMinutes) { this.intervalMinutes = intervalMinutes; }

    // Calculated properties
    public double getRange() {
        return high - low;
    }

    public double getBody() {
        return Math.abs(close - open);
    }

    public boolean isBullish() {
        return close > open;
    }

    public boolean isBearish() {
        return close < open;
    }

    public double getChangePercent(double previousClose) {
        if (previousClose > 0) {
            return ((close - previousClose) / previousClose) * 100;
        }
        return 0;
    }

    public double getTypicalPrice() {
        return (high + low + close) / 3.0;
    }

    @Override
    public String toString() {
        return String.format("%s | O:%.2f H:%.2f L:%.2f C:%.2f V:%d",
            timestamp, open, high, low, close, volume);
    }
}

