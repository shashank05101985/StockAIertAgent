package org.example.model;

public class PriceBand {

    private final String symbol;
    private double lowerBand;
    private double upperBand;
    private double bandPercent;

    public PriceBand(String symbol, double lowerBand, double upperBand, double bandPercent) {

        this.symbol = symbol;
        this.lowerBand = lowerBand;
        this.upperBand = upperBand;
        this.bandPercent = bandPercent;
    }

    public PriceBand(String symbol, double bandPercent) {
        this.symbol = symbol;
        this.bandPercent = bandPercent;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getLowerBand() {
        return lowerBand;
    }

    public double getUpperBand() {
        return upperBand;
    }

    public double getBandPercent() {
        return bandPercent;
    }
}
