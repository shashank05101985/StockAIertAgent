package org.example.model;

import java.time.LocalDate;

/**
 * Model class representing daily stock data from Bhav Copy
 */
public class StockData {
    private String symbol;
    private LocalDate date;
    private double open;
    private double high;
    private double low;
    private double close;
    private double previousClose;
    private long tradedQty;       // Total traded quantity
    private long deliveredQty;    // Delivered quantity
    private double deliveryPercent; // Delivery percentage
    private double turnover;       // Total turnover

    // Constructors
    public StockData() {}

    public StockData(String symbol, LocalDate date, double open, double high, double low,
                     double close, long tradedQty, long deliveredQty) {
        this.symbol = symbol;
        this.date = date;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.tradedQty = tradedQty;
        this.deliveredQty = deliveredQty;
        this.deliveryPercent = tradedQty > 0 ? (deliveredQty * 100.0 / tradedQty) : 0;
    }

    // Getters and Setters
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public double getOpen() { return open; }
    public void setOpen(double open) { this.open = open; }

    public double getHigh() { return high; }
    public void setHigh(double high) { this.high = high; }

    public double getLow() { return low; }
    public void setLow(double low) { this.low = low; }

    public double getClose() { return close; }
    public void setClose(double close) { this.close = close; }

    public double getPreviousClose() { return previousClose; }
    public void setPreviousClose(double previousClose) { this.previousClose = previousClose; }

    public long getTradedQty() { return tradedQty; }
    public void setTradedQty(long tradedQty) { this.tradedQty = tradedQty; }

    public long getDeliveredQty() { return deliveredQty; }
    public void setDeliveredQty(long deliveredQty) { this.deliveredQty = deliveredQty; }

    public double getDeliveryPercent() {
        if (deliveryPercent == 0 && tradedQty > 0) {
            deliveryPercent = (deliveredQty * 100.0 / tradedQty);
        }
        return deliveryPercent;
    }
    public void setDeliveryPercent(double deliveryPercent) { this.deliveryPercent = deliveryPercent; }

    public double getTurnover() { return turnover; }
    public void setTurnover(double turnover) { this.turnover = turnover; }

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

    public double getChangePercent() {
        if (previousClose > 0) {
            return ((close - previousClose) / previousClose) * 100;
        }
        return ((close - open) / open) * 100;
    }

    @Override
    public String toString() {
        return String.format("StockData{symbol='%s', date=%s, O=%.2f, H=%.2f, L=%.2f, C=%.2f, " +
                        "TradedQty=%d, DeliveredQty=%d, DelPct=%.2f%%}",
                symbol, date, open, high, low, close, tradedQty, deliveredQty, getDeliveryPercent());
    }
}

