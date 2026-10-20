package org.example.dailyjob;

import java.time.LocalDate;

public class BhavcopyRecord {

    public String symbol;

    public LocalDate date;

    // =========================
    // OHLC
    // =========================

    public double openPrice;

    public double highPrice;

    public double lowPrice;

    public double closePrice;

    // =========================
    // VOLUME
    // =========================

    public long tradedQty;

    public long deliveryQty;

    public double deliveryPercent;

    // =========================
    // OPTIONAL EXTRA FIELDS
    // =========================

    public long numberOfTrades;

    public double turnover;

    public String series;

    // =========================
    // CONSTRUCTORS
    // =========================

    public BhavcopyRecord() {
    }

    public BhavcopyRecord(
            String symbol,
            LocalDate date
    ) {
        this.symbol = symbol;
        this.date = date;
    }

    // =========================
    // HELPERS
    // =========================

    public boolean isValid() {

        return symbol != null
                && !symbol.isBlank()
                && date != null;
    }

    @Override
    public String toString() {

        return "BhavcopyRecord{" +
                "symbol='" + symbol + '\'' +
                ", date=" + date +
                ", openPrice=" + openPrice +
                ", highPrice=" + highPrice +
                ", lowPrice=" + lowPrice +
                ", closePrice=" + closePrice +
                ", tradedQty=" + tradedQty +
                ", deliveryQty=" + deliveryQty +
                ", deliveryPercent=" + deliveryPercent +
                '}';
    }
}