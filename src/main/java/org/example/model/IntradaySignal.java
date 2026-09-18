package org.example.model;

import java.time.LocalDateTime;

/**
 * Intraday trading signal with precise entry, exit and stop-loss levels
 */
public class IntradaySignal {

    public enum SignalType {
        STRONG_BUY("Strong Buy - Enter Now"),
        BUY("Buy - Good Entry"),
        WAIT_PULLBACK("Wait for Pullback"),
        HOLD("Hold Position"),
        PARTIAL_EXIT("Book Partial Profits"),
        EXIT("Exit Position"),
        STOP_HIT("Stop Loss Hit"),
        AVOID("Avoid - No Entry");

        private final String description;

        SignalType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum TrendDirection {
        STRONG_UP, UP, SIDEWAYS, DOWN, STRONG_DOWN
    }

    private String symbol;
    private LocalDateTime signalTime;
    private SignalType signalType;
    private TrendDirection trend;

    // Price levels
    private double currentPrice;
    private double entryPrice;
    private double stopLoss;
    private double target1;
    private double target2;
    private double target3;

    // Intraday metrics
    private double vwap;
    private double dayHigh;
    private double dayLow;
    private double openingRangeHigh;  // First 15-30 min high
    private double openingRangeLow;   // First 15-30 min low
    private double intradayRsi;
    private double volumeRatio;       // Current volume vs average

    // Signal strength
    private int score;                // 0-100
    private String reason;

    // Position tracking
    private boolean entryTaken;
    private double entryFilledPrice;
    private int quantitySuggested;

    public IntradaySignal() {}

    // Getters and Setters
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public LocalDateTime getSignalTime() { return signalTime; }
    public void setSignalTime(LocalDateTime signalTime) { this.signalTime = signalTime; }

    public SignalType getSignalType() { return signalType; }
    public void setSignalType(SignalType signalType) { this.signalType = signalType; }

    public TrendDirection getTrend() { return trend; }
    public void setTrend(TrendDirection trend) { this.trend = trend; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(double entryPrice) { this.entryPrice = entryPrice; }

    public double getStopLoss() { return stopLoss; }
    public void setStopLoss(double stopLoss) { this.stopLoss = stopLoss; }

    public double getTarget1() { return target1; }
    public void setTarget1(double target1) { this.target1 = target1; }

    public double getTarget2() { return target2; }
    public void setTarget2(double target2) { this.target2 = target2; }

    public double getTarget3() { return target3; }
    public void setTarget3(double target3) { this.target3 = target3; }

    public double getVwap() { return vwap; }
    public void setVwap(double vwap) { this.vwap = vwap; }

    public double getDayHigh() { return dayHigh; }
    public void setDayHigh(double dayHigh) { this.dayHigh = dayHigh; }

    public double getDayLow() { return dayLow; }
    public void setDayLow(double dayLow) { this.dayLow = dayLow; }

    public double getOpeningRangeHigh() { return openingRangeHigh; }
    public void setOpeningRangeHigh(double openingRangeHigh) { this.openingRangeHigh = openingRangeHigh; }

    public double getOpeningRangeLow() { return openingRangeLow; }
    public void setOpeningRangeLow(double openingRangeLow) { this.openingRangeLow = openingRangeLow; }

    public double getIntradayRsi() { return intradayRsi; }
    public void setIntradayRsi(double intradayRsi) { this.intradayRsi = intradayRsi; }

    public double getVolumeRatio() { return volumeRatio; }
    public void setVolumeRatio(double volumeRatio) { this.volumeRatio = volumeRatio; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public boolean isEntryTaken() { return entryTaken; }
    public void setEntryTaken(boolean entryTaken) { this.entryTaken = entryTaken; }

    public double getEntryFilledPrice() { return entryFilledPrice; }
    public void setEntryFilledPrice(double entryFilledPrice) { this.entryFilledPrice = entryFilledPrice; }

    public int getQuantitySuggested() { return quantitySuggested; }
    public void setQuantitySuggested(int quantitySuggested) { this.quantitySuggested = quantitySuggested; }

    // Calculated properties
    public double getRiskPercent() {
        if (entryPrice > 0 && stopLoss > 0) {
            return ((entryPrice - stopLoss) / entryPrice) * 100;
        }
        return 0;
    }

    public double getRiskRewardRatio() {
        double risk = entryPrice - stopLoss;
        double reward = target2 - entryPrice;
        if (risk > 0) {
            return reward / risk;
        }
        return 0;
    }

    public boolean isAboveVwap() {
        return currentPrice > vwap;
    }

    public boolean isBreakoutAboveOpeningRange() {
        return currentPrice > openingRangeHigh;
    }

    public boolean isBreakdownBelowOpeningRange() {
        return currentPrice < openingRangeLow;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n┌──────────────────────────────────────────────────────────────┐\n");
        sb.append(String.format("│ %s | %s | Score: %d/100\n", symbol, signalTime.toLocalTime(), score));
        sb.append(String.format("│ Signal: %s | Trend: %s\n", signalType.getDescription(), trend));
        sb.append("├──────────────────────────────────────────────────────────────┤\n");
        sb.append(String.format("│ Price: ₹%.2f | VWAP: ₹%.2f %s\n",
            currentPrice, vwap, isAboveVwap() ? "▲" : "▼"));
        sb.append(String.format("│ Day Range: ₹%.2f - ₹%.2f\n", dayLow, dayHigh));
        sb.append(String.format("│ Opening Range: ₹%.2f - ₹%.2f %s\n",
            openingRangeLow, openingRangeHigh,
            isBreakoutAboveOpeningRange() ? "⬆ BREAKOUT" :
            isBreakdownBelowOpeningRange() ? "⬇ BREAKDOWN" : ""));
        sb.append("├──────────────────────────────────────────────────────────────┤\n");
        sb.append(String.format("│ Entry: ₹%.2f | SL: ₹%.2f (%.1f%%)\n", entryPrice, stopLoss, getRiskPercent()));
        sb.append(String.format("│ T1: ₹%.2f | T2: ₹%.2f | T3: ₹%.2f\n", target1, target2, target3));
        sb.append(String.format("│ Risk:Reward = 1:%.2f\n", getRiskRewardRatio()));
        sb.append("├──────────────────────────────────────────────────────────────┤\n");
        sb.append(String.format("│ RSI: %.1f | Volume: %.1fx avg\n", intradayRsi, volumeRatio));
        sb.append(String.format("│ Reason: %s\n", reason));
        sb.append("└──────────────────────────────────────────────────────────────┘");
        return sb.toString();
    }
}

