package org.example.model;

import java.time.LocalDate;

public class PotentialStock {

    private String symbol;
    private LocalDate tradeDate;

    private double closePrice;
    private double potentialScore;

    private double atr14;
    private double atrPct;

    private double return5d;
    private double return10d;
    private double return20d;

    private double rsi14;
    private double adx14;

    private double ema20;
    private double ema50;
    private double ema200;

    private double macdHistogram;

    private boolean breakout5d;
    private boolean breakout10d;
    private boolean breakout20d;

    private double breakoutStrength;

    private boolean higherHigh;
    private boolean higherLow;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public void setTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    public double getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(double closePrice) {
        this.closePrice = closePrice;
    }

    public double getPotentialScore() {
        return potentialScore;
    }

    public void setPotentialScore(double potentialScore) {
        this.potentialScore = potentialScore;
    }

    public double getAtr14() {
        return atr14;
    }

    public void setAtr14(double atr14) {
        this.atr14 = atr14;
    }

    public double getAtrPct() {
        return atrPct;
    }

    public void setAtrPct(double atrPct) {
        this.atrPct = atrPct;
    }

    public double getReturn5d() {
        return return5d;
    }

    public void setReturn5d(double return5d) {
        this.return5d = return5d;
    }

    public double getReturn10d() {
        return return10d;
    }

    public void setReturn10d(double return10d) {
        this.return10d = return10d;
    }

    public double getReturn20d() {
        return return20d;
    }

    public void setReturn20d(double return20d) {
        this.return20d = return20d;
    }

    public double getRsi14() {
        return rsi14;
    }

    public void setRsi14(double rsi14) {
        this.rsi14 = rsi14;
    }

    public double getAdx14() {
        return adx14;
    }

    public void setAdx14(double adx14) {
        this.adx14 = adx14;
    }

    public double getEma20() {
        return ema20;
    }

    public void setEma20(double ema20) {
        this.ema20 = ema20;
    }

    public double getEma50() {
        return ema50;
    }

    public void setEma50(double ema50) {
        this.ema50 = ema50;
    }

    public double getEma200() {
        return ema200;
    }

    public void setEma200(double ema200) {
        this.ema200 = ema200;
    }

    public double getMacdHistogram() {
        return macdHistogram;
    }

    public void setMacdHistogram(double macdHistogram) {
        this.macdHistogram = macdHistogram;
    }

    public boolean isBreakout5d() {
        return breakout5d;
    }

    public void setBreakout5d(boolean breakout5d) {
        this.breakout5d = breakout5d;
    }

    public boolean isBreakout10d() {
        return breakout10d;
    }

    public void setBreakout10d(boolean breakout10d) {
        this.breakout10d = breakout10d;
    }

    public boolean isBreakout20d() {
        return breakout20d;
    }

    public void setBreakout20d(boolean breakout20d) {
        this.breakout20d = breakout20d;
    }

    public double getBreakoutStrength() {
        return breakoutStrength;
    }

    public void setBreakoutStrength(double breakoutStrength) {
        this.breakoutStrength = breakoutStrength;
    }

    public boolean isHigherHigh() {
        return higherHigh;
    }

    public void setHigherHigh(boolean higherHigh) {
        this.higherHigh = higherHigh;
    }

    public boolean isHigherLow() {
        return higherLow;
    }

    public void setHigherLow(boolean higherLow) {
        this.higherLow = higherLow;
    }

    // getters and setters
}
