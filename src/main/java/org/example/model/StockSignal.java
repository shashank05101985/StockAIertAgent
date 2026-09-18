package org.example.model;

/**
 * Model class representing a trading signal with entry, stop-loss and targets
 */
public class StockSignal {



    public enum SignalType {
        STRONG_BUY("Strong Buy - High Probability"),
        BUY("Buy - Good Potential"),
        WEAK_BUY("Weak Buy - Monitor Closely"),
        STRONG_SELL("Strong Sell - High Probability"),
        SELL("Sell - Good Potential"),
        WEAK_SELL("Weak Sell - Monitor Closely"),
        HOLD("Hold - No Clear Direction"),
        AVOID("Avoid - High Risk");

        private final String description;

        SignalType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    private String symbol;
    private SignalType signalType;
    private double currentPrice;
    private double entryPrice;
    private double stopLoss;
    private double target1;
    private double target2;
    private double target3;
    private double riskRewardRatio;
    private int score;                  // Overall score out of 100
    private double deliveryPercent;
    private double volumeChange;        // Volume change vs average
    private String reason;
    private boolean volumeBreakout;
    private boolean priceBreakout;
    private boolean priceBreakdown;
    private boolean deliveryBreakout;
    private double rsi;
    private double bbPosition;
    private double momentum;

    // News sentiment fields
    private double newsSentimentScore;  // -1 to +1
    private int newsArticleCount;
    private String newsSentimentLabel;


    public StockSignal() {}

    public boolean isPriceBreakdown() {
        return priceBreakdown;
    }

    public void setPriceBreakdown(boolean priceBreakdown) {
        this.priceBreakdown = priceBreakdown;
    }

    public double getRsi() {
        return rsi;
    }

    public void setRsi(double rsi) {
        this.rsi = rsi;
    }

    public double getBbPosition() {
        return bbPosition;
    }

    public void setBbPosition(double bbPosition) {
        this.bbPosition = bbPosition;
    }

    public double getMomentum() {
        return momentum;
    }

    public void setMomentum(double momentum) {
        this.momentum = momentum;
    }

    // Getters and Setters
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public SignalType getSignalType() { return signalType; }
    public void setSignalType(SignalType signalType) { this.signalType = signalType; }

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

    public double getRiskRewardRatio() { return riskRewardRatio; }
    public void setRiskRewardRatio(double riskRewardRatio) { this.riskRewardRatio = riskRewardRatio; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public double getDeliveryPercent() { return deliveryPercent; }
    public void setDeliveryPercent(double deliveryPercent) { this.deliveryPercent = deliveryPercent; }

    public double getVolumeChange() { return volumeChange; }
    public void setVolumeChange(double volumeChange) { this.volumeChange = volumeChange; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public boolean isVolumeBreakout() { return volumeBreakout; }
    public void setVolumeBreakout(boolean volumeBreakout) { this.volumeBreakout = volumeBreakout; }

    public boolean isPriceBreakout() { return priceBreakout; }
    public void setPriceBreakout(boolean priceBreakout) { this.priceBreakout = priceBreakout; }

    public boolean isDeliveryBreakout() { return deliveryBreakout; }
    public void setDeliveryBreakout(boolean deliveryBreakout) { this.deliveryBreakout = deliveryBreakout; }

    // News sentiment getters/setters
    public double getNewsSentimentScore() { return newsSentimentScore; }
    public void setNewsSentimentScore(double newsSentimentScore) { this.newsSentimentScore = newsSentimentScore; }

    public int getNewsArticleCount() { return newsArticleCount; }
    public void setNewsArticleCount(int newsArticleCount) { this.newsArticleCount = newsArticleCount; }

    public String getNewsSentimentLabel() { return newsSentimentLabel; }
    public void setNewsSentimentLabel(String newsSentimentLabel) { this.newsSentimentLabel = newsSentimentLabel; }

    public double getRiskPercent() {
        if (currentPrice > 0) {
            return ((currentPrice - stopLoss) / currentPrice) * 100;
        }
        return 0;
    }

    public double getTarget1Percent() {
        if (currentPrice > 0) {
            return ((target1 - currentPrice) / currentPrice) * 100;
        }
        return 0;
    }

    public double getTarget2Percent() {
        if (currentPrice > 0) {
            return ((target2 - currentPrice) / currentPrice) * 100;
        }
        return 0;
    }

    public double getTarget3Percent() {
        if (currentPrice > 0) {
            return ((target3 - currentPrice) / currentPrice) * 100;
        }
        return 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔══════════════════════════════════════════════════════════════╗\n");
        sb.append(String.format("║  %s  -  Score: %d/100\n", symbol, score));
        sb.append(String.format("║  Signal: %s\n", signalType.getDescription()));
        sb.append("╠══════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  Current Price : ₹%.2f\n", currentPrice));
        sb.append(String.format("║  Entry Price   : ₹%.2f\n", entryPrice));
        sb.append(String.format("║  Stop Loss     : ₹%.2f (Risk: %.2f%%)\n", stopLoss, getRiskPercent()));
        sb.append("╠══════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  Target 1      : ₹%.2f (+%.2f%%)\n", target1, getTarget1Percent()));
        sb.append(String.format("║  Target 2      : ₹%.2f (+%.2f%%)\n", target2, getTarget2Percent()));
        sb.append(String.format("║  Target 3      : ₹%.2f (+%.2f%%)\n", target3, getTarget3Percent()));
        sb.append(String.format("║  Risk:Reward   : 1:%.2f\n", riskRewardRatio));
        sb.append("╠══════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  Delivery %%    : %.2f%%\n", deliveryPercent));
        sb.append(String.format("║  Volume Change : %.2f%% vs Avg\n", volumeChange));
        sb.append(String.format("║  Breakouts     : Vol:%s | Price:%s | Del:%s\n",
                volumeBreakout ? "✓" : "✗", priceBreakout ? "✓" : "✗", deliveryBreakout ? "✓" : "✗"));
        sb.append("╠══════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  Reason: %s\n", reason));
        sb.append("╚══════════════════════════════════════════════════════════════╝");
        return sb.toString();
    }
}

