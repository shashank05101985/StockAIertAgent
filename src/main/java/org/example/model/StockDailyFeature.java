package org.example.model;

import java.time.LocalDate;

public class StockDailyFeature {

    public String symbol;
    public LocalDate tradeDate;

    public double open;
    public double high;
    public double low;
    public double close;
    public long volume;

    // Returns
    public Double return1d;
    public Double return3d;
    public Double return5d;
    public Double return10d;
    public Double return20d;

    // SMA
    public Double sma20;
    public Double sma50;
    public Double sma100;
    public Double sma200;

    // EMA
    public Double ema9;
    public Double ema20;
    public Double ema50;
    public Double ema100;
    public Double ema200;

    // Trend flags
    public Boolean closeAboveEma20;
    public Boolean ema20AboveEma50;
    public Boolean ema50AboveEma200;

    // RSI
    public Double rsi14;

    // MACD
    public Double macd;
    public Double macdSignal;
    public Double macdHistogram;

    // ATR / ADX
    public Double tr14;
    public Double atr14;
    public Double atrPercent;

    public Double plusDm14;
    public Double minusDm14;

    public Double plusDi14;
    public Double minusDi14;
    public Double adx14;

    // Stochastic
    public Double stochasticK;
    public Double stochasticD;

    // CCI
    public Double cci20;

    // ROC
    public Double roc5;
    public Double roc10;
    public Double roc20;

    // Bollinger
    public Double bbMiddle;
    public Double bbUpper;
    public Double bbLower;
    public Double bbWidth;
    public Double bbPercentB;

    // Volume
    public Double avgVolume5;
    public Double avgVolume10;
    public Double avgVolume20;
    public Double avgVolume50;

    public Double volumeRatio5;
    public Double volumeRatio20;
    public Double volumeRatio50;

    public Double obv;

    // High / low
    public Double high5d;
    public Double high10d;
    public Double high20d;
    public Double high50d;

    public Double low5d;
    public Double low10d;
    public Double low20d;
    public Double low50d;

    public Double distanceFrom5dHigh;
    public Double distanceFrom10dHigh;
    public Double distanceFrom20dHigh;
    public Double distanceFrom52wHigh;

    // Volatility
    public Double dailyRangePercent;
    public Double avgRange5Percent;
    public Double avgRange20Percent;

    public Double volatility5;
    public Double volatility20;

    // Structure
    public Boolean higherHigh;
    public Boolean higherLow;
    public Boolean lowerHigh;
    public Boolean lowerLow;

    // Breakout
    public Boolean breakout5d;
    public Boolean breakout10d;
    public Boolean breakout20d;

    public Boolean breakdown5d;
    public Boolean breakdown10d;
    public Boolean breakdown20d;

    public Double consolidationWidth;
    public Double consolidationScore;
    public Double breakoutStrength;

    // NIFTY
    public Double niftyReturn5d;
    public Double niftyReturn10d;
    public Double niftyReturn20d;

    public Double relativeStrength5d;
    public Double relativeStrength10d;
    public Double relativeStrength20d;

    // Scores - leave NULL initially
    public Double trendScore;
    public Double momentumScore;
    public Double volumeScore;
    public Double volatilityScore;
    public Double relativeStrengthScore;
    public Double priceStructureScore;
    public Double breakoutScore;

    public Double bullishScore;

    public String marketBias;

    // =========================================================
    // PULLBACK / RECOVERY
    // =========================================================

    public Double previous5dHigh;
    public Double previous5dLow;

    public Double pullbackDepth5d;
    public Double distanceFromPullbackLow;

    public Integer pullbackDays5d;

    public Double recoveryFromPullback;
    public Double recoveryVolumeRatio;

    public Boolean pullbackDetected;
    public Boolean recoveryDetected;
    public Double distanceToPrevious5dHigh;

    public Double deliveryPercent;
    public Double deliveryChange;
    public Double avgDeliveryPercent5;
    public boolean deliveryConfirmation;
    public Double resistancePrice;
    public Double supportPrice;
    public Double resistanceStrength;
    public Double supportStrength;
    public Double distanceToResistance;
    public Double distanceToSupport;
}
