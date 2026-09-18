package org.example.analysis;

import org.example.model.IntradayCandle;
import org.example.model.IntradaySignal;
import org.example.model.IntradaySignal.SignalType;
import org.example.model.IntradaySignal.TrendDirection;
import org.example.model.StockSignal;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Intraday Analyzer for precise entry/exit timing
 *
 * Workflow:
 * 1. Morning: Get watchlist from daily analyzer (stocks with potential)
 * 2. During day: Feed intraday data for watchlist stocks
 * 3. Get real-time entry/exit signals
 */
public class IntradayAnalyzer {

    // Configuration
    private int openingRangeMinutes = 15;    // First 15 minutes for opening range
    private double minRiskReward = 2.0;
    private double maxStopLossPercent = 1.5; // Tighter SL for intraday
    private LocalTime marketOpen = LocalTime.of(9, 15);
    private LocalTime marketClose = LocalTime.of(15, 30);

    // Store intraday data for each symbol
    private Map<String, List<IntradayCandle>> intradayDataMap = new HashMap<>();
    private Map<String, IntradaySignal> activeSignals = new HashMap<>();
    private Map<String, Double> previousDayClose = new HashMap<>();

    /**
     * Set previous day close prices (from daily analysis)
     */
    public void setPreviousDayClose(Map<String, Double> closes) {
        this.previousDayClose = closes;
    }

    /**
     * Add a new intraday candle and analyze
     */
    public IntradaySignal processCandle(IntradayCandle candle) {
        String symbol = candle.getSymbol();

        // Store candle data
        intradayDataMap.computeIfAbsent(symbol, k -> new ArrayList<>()).add(candle);

        List<IntradayCandle> candles = intradayDataMap.get(symbol);

        // Need minimum candles for analysis
        if (candles.size() < 3) {
            return null;
        }

        // Analyze and generate signal
        return analyzeIntraday(symbol, candles);
    }

    /**
     * Main intraday analysis
     */
    public IntradaySignal analyzeIntraday(String symbol, List<IntradayCandle> candles) {
        IntradaySignal signal = new IntradaySignal();
        signal.setSymbol(symbol);
        signal.setSignalTime(candles.get(candles.size() - 1).getTimestamp());

        IntradayCandle latest = candles.get(candles.size() - 1);
        signal.setCurrentPrice(latest.getClose());

        // Calculate key levels
        calculateDayLevels(candles, signal);
        calculateVWAP(candles, signal);
        calculateOpeningRange(candles, signal);

        // Calculate intraday RSI
        signal.setIntradayRsi(calculateIntradayRSI(candles, 14));

        // Calculate volume ratio
        signal.setVolumeRatio(calculateVolumeRatio(candles));

        // Determine trend
        signal.setTrend(determineTrend(candles, signal));

        // Calculate score and generate signal
        int score = calculateScore(candles, signal);
        signal.setScore(score);

        // Determine signal type and levels
        determineSignalAndLevels(candles, signal);

        // Store active signal
        activeSignals.put(symbol, signal);

        return signal;
    }

    /**
     * Calculate day high/low
     */
    private void calculateDayLevels(List<IntradayCandle> candles, IntradaySignal signal) {
        double dayHigh = Double.MIN_VALUE;
        double dayLow = Double.MAX_VALUE;

        for (IntradayCandle candle : candles) {
            dayHigh = Math.max(dayHigh, candle.getHigh());
            dayLow = Math.min(dayLow, candle.getLow());
        }

        signal.setDayHigh(dayHigh);
        signal.setDayLow(dayLow);
    }

    /**
     * Calculate VWAP (Volume Weighted Average Price)
     */
    private void calculateVWAP(List<IntradayCandle> candles, IntradaySignal signal) {
        double cumulativeTPV = 0;
        long cumulativeVolume = 0;

        for (IntradayCandle candle : candles) {
            double typicalPrice = candle.getTypicalPrice();
            cumulativeTPV += typicalPrice * candle.getVolume();
            cumulativeVolume += candle.getVolume();
        }

        double vwap = cumulativeVolume > 0 ? cumulativeTPV / cumulativeVolume : 0;
        signal.setVwap(Math.round(vwap * 100.0) / 100.0);
    }

    /**
     * Calculate Opening Range (first N minutes high/low)
     */
    private void calculateOpeningRange(List<IntradayCandle> candles, IntradaySignal signal) {
        double orHigh = Double.MIN_VALUE;
        double orLow = Double.MAX_VALUE;

        LocalTime openTime = marketOpen;
        LocalTime orEndTime = openTime.plusMinutes(openingRangeMinutes);

        for (IntradayCandle candle : candles) {
            LocalTime candleTime = candle.getTimestamp().toLocalTime();
            if (candleTime.isBefore(orEndTime) || candleTime.equals(orEndTime)) {
                orHigh = Math.max(orHigh, candle.getHigh());
                orLow = Math.min(orLow, candle.getLow());
            }
        }

        // If no candles in opening range yet, use first candle
        if (orHigh == Double.MIN_VALUE && !candles.isEmpty()) {
            orHigh = candles.get(0).getHigh();
            orLow = candles.get(0).getLow();
        }

        signal.setOpeningRangeHigh(orHigh);
        signal.setOpeningRangeLow(orLow);
    }

    /**
     * Calculate intraday RSI with shorter period
     */
    private double calculateIntradayRSI(List<IntradayCandle> candles, int period) {
        if (candles.size() < period + 1) {
            return 50; // Default neutral
        }

        double avgGain = 0, avgLoss = 0;

        // Calculate initial average gain/loss
        for (int i = 1; i <= period && i < candles.size(); i++) {
            double change = candles.get(i).getClose() - candles.get(i - 1).getClose();
            if (change > 0) avgGain += change;
            else avgLoss += Math.abs(change);
        }
        avgGain /= period;
        avgLoss /= period;

        // Calculate smoothed RSI
        for (int i = period + 1; i < candles.size(); i++) {
            double change = candles.get(i).getClose() - candles.get(i - 1).getClose();
            if (change > 0) {
                avgGain = (avgGain * (period - 1) + change) / period;
                avgLoss = (avgLoss * (period - 1)) / period;
            } else {
                avgGain = (avgGain * (period - 1)) / period;
                avgLoss = (avgLoss * (period - 1) + Math.abs(change)) / period;
            }
        }

        if (avgLoss == 0) return 100;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    /**
     * Calculate current volume vs average
     */
    private double calculateVolumeRatio(List<IntradayCandle> candles) {
        if (candles.size() < 5) return 1.0;

        // Average volume of previous candles
        long avgVolume = 0;
        int count = Math.min(10, candles.size() - 1);
        for (int i = candles.size() - count - 1; i < candles.size() - 1; i++) {
            avgVolume += candles.get(i).getVolume();
        }
        avgVolume /= count;

        long currentVolume = candles.get(candles.size() - 1).getVolume();

        return avgVolume > 0 ? (double) currentVolume / avgVolume : 1.0;
    }

    /**
     * Determine current trend direction
     */
    private TrendDirection determineTrend(List<IntradayCandle> candles, IntradaySignal signal) {
        if (candles.size() < 5) return TrendDirection.SIDEWAYS;

        IntradayCandle latest = candles.get(candles.size() - 1);
        double currentPrice = latest.getClose();
        double vwap = signal.getVwap();
        double orHigh = signal.getOpeningRangeHigh();
        double orLow = signal.getOpeningRangeLow();

        // Calculate short-term momentum
        double momentum = 0;
        int lookback = Math.min(5, candles.size());
        if (lookback > 1) {
            momentum = ((currentPrice - candles.get(candles.size() - lookback).getClose())
                       / candles.get(candles.size() - lookback).getClose()) * 100;
        }

        // Strong up: Above VWAP + Above OR + Positive momentum
        if (currentPrice > vwap && currentPrice > orHigh && momentum > 0.5) {
            return momentum > 1.5 ? TrendDirection.STRONG_UP : TrendDirection.UP;
        }

        // Strong down: Below VWAP + Below OR + Negative momentum
        if (currentPrice < vwap && currentPrice < orLow && momentum < -0.5) {
            return momentum < -1.5 ? TrendDirection.STRONG_DOWN : TrendDirection.DOWN;
        }

        // Mild up/down
        if (currentPrice > vwap && momentum > 0) {
            return TrendDirection.UP;
        }
        if (currentPrice < vwap && momentum < 0) {
            return TrendDirection.DOWN;
        }

        return TrendDirection.SIDEWAYS;
    }

    /**
     * Calculate signal score
     */
    private int calculateScore(List<IntradayCandle> candles, IntradaySignal signal) {
        int score = 0;
        StringBuilder reason = new StringBuilder();

        IntradayCandle latest = candles.get(candles.size() - 1);
        double currentPrice = latest.getClose();

        // 1. VWAP Position (Max 20 points)
        if (currentPrice > signal.getVwap()) {
            double aboveVwapPct = ((currentPrice - signal.getVwap()) / signal.getVwap()) * 100;
            if (aboveVwapPct > 0.5) {
                score += 15;
                reason.append("Above VWAP. ");
            } else {
                score += 10;
                reason.append("Just above VWAP. ");
            }
        } else {
            score -= 5;
            reason.append("Below VWAP. ");
        }

        // 2. Opening Range Breakout (Max 25 points)
        if (currentPrice > signal.getOpeningRangeHigh()) {
            score += 25;
            reason.append("ORB breakout! ");
        } else if (currentPrice < signal.getOpeningRangeLow()) {
            score -= 10;
            reason.append("ORB breakdown. ");
        } else {
            score += 5;
            reason.append("Within opening range. ");
        }

        // 3. Trend (Max 20 points)
        switch (signal.getTrend()) {
            case STRONG_UP:
                score += 20;
                reason.append("Strong uptrend. ");
                break;
            case UP:
                score += 15;
                reason.append("Uptrend. ");
                break;
            case SIDEWAYS:
                score += 5;
                reason.append("Sideways. ");
                break;
            case DOWN:
                score -= 10;
                reason.append("Downtrend. ");
                break;
            case STRONG_DOWN:
                score -= 15;
                reason.append("Strong downtrend. ");
                break;
        }

        // 4. RSI (Max 15 points)
        double rsi = signal.getIntradayRsi();
        if (rsi >= 50 && rsi <= 70) {
            score += 15;
            reason.append(String.format("RSI bullish (%.0f). ", rsi));
        } else if (rsi > 70 && rsi <= 80) {
            score += 5;
            reason.append(String.format("RSI overbought (%.0f). ", rsi));
        } else if (rsi > 80) {
            score -= 5;
            reason.append(String.format("RSI extreme (%.0f). ", rsi));
        } else if (rsi >= 40 && rsi < 50) {
            score += 5;
            reason.append(String.format("RSI neutral (%.0f). ", rsi));
        } else {
            score -= 5;
            reason.append(String.format("RSI weak (%.0f). ", rsi));
        }

        // 5. Volume (Max 20 points)
        double volRatio = signal.getVolumeRatio();
        if (volRatio >= 2.0) {
            score += 20;
            reason.append(String.format("High volume (%.1fx). ", volRatio));
        } else if (volRatio >= 1.5) {
            score += 15;
            reason.append(String.format("Good volume (%.1fx). ", volRatio));
        } else if (volRatio >= 1.0) {
            score += 10;
            reason.append("Normal volume. ");
        } else {
            score -= 5;
            reason.append("Low volume. ");
        }

        // 6. Candle Pattern
        if (latest.isBullish() && latest.getBody() > latest.getRange() * 0.6) {
            score += 10;
            reason.append("Strong bullish candle. ");
        } else if (latest.isBearish() && latest.getBody() > latest.getRange() * 0.6) {
            score -= 5;
            reason.append("Strong bearish candle. ");
        }

        signal.setReason(reason.toString().trim());
        return Math.max(0, Math.min(100, score));
    }

    /**
     * Determine signal type and calculate entry/exit levels
     */
    private void determineSignalAndLevels(List<IntradayCandle> candles, IntradaySignal signal) {
        IntradayCandle latest = candles.get(candles.size() - 1);
        double currentPrice = latest.getClose();
        int score = signal.getScore();

        // Find swing low for stop loss
        double swingLow = findSwingLow(candles, 5);
        double orLow = signal.getOpeningRangeLow();

        // Stop loss: Higher of swing low or just below OR low
        double stopLoss = Math.max(swingLow * 0.998, orLow * 0.998);

        // Ensure stop loss is within acceptable range
        double maxSL = currentPrice * (1 - maxStopLossPercent / 100);
        stopLoss = Math.max(stopLoss, maxSL);

        signal.setStopLoss(Math.round(stopLoss * 100.0) / 100.0);

        // Calculate risk
        double risk = currentPrice - stopLoss;

        // Targets based on risk:reward
        signal.setTarget1(Math.round((currentPrice + risk * 1.5) * 100.0) / 100.0);
        signal.setTarget2(Math.round((currentPrice + risk * 2.5) * 100.0) / 100.0);
        signal.setTarget3(Math.round((currentPrice + risk * 4.0) * 100.0) / 100.0);

        // Adjust targets based on day high/resistance
        if (signal.getDayHigh() > currentPrice && signal.getDayHigh() < signal.getTarget2()) {
            signal.setTarget1(Math.round(signal.getDayHigh() * 0.998 * 100.0) / 100.0);
        }

        // Entry price (slight buffer above current for breakout confirmation)
        signal.setEntryPrice(Math.round(currentPrice * 1.001 * 100.0) / 100.0);

        // Determine signal type
        TrendDirection trend = signal.getTrend();
        boolean aboveVwap = signal.isAboveVwap();
        boolean orbBreakout = signal.isBreakoutAboveOpeningRange();

        if (score >= 75 && trend == TrendDirection.STRONG_UP && orbBreakout && aboveVwap) {
            signal.setSignalType(SignalType.STRONG_BUY);
        } else if (score >= 60 && (trend == TrendDirection.STRONG_UP || trend == TrendDirection.UP)
                   && aboveVwap) {
            signal.setSignalType(SignalType.BUY);
        } else if (score >= 50 && aboveVwap && !orbBreakout) {
            signal.setSignalType(SignalType.WAIT_PULLBACK);
            // Adjust entry to VWAP for pullback entry
            signal.setEntryPrice(Math.round(signal.getVwap() * 1.002 * 100.0) / 100.0);
        } else if (score >= 40 && trend != TrendDirection.DOWN && trend != TrendDirection.STRONG_DOWN) {
            signal.setSignalType(SignalType.HOLD);
        } else if (score < 30 || trend == TrendDirection.STRONG_DOWN) {
            signal.setSignalType(SignalType.AVOID);
        } else {
            signal.setSignalType(SignalType.HOLD);
        }

        // Check if already in position and needs exit
        IntradaySignal existingSignal = activeSignals.get(signal.getSymbol());
        if (existingSignal != null && existingSignal.isEntryTaken()) {
            checkExitConditions(signal, existingSignal);
        }
    }

    /**
     * Find swing low from recent candles
     */
    private double findSwingLow(List<IntradayCandle> candles, int lookback) {
        double swingLow = Double.MAX_VALUE;
        int start = Math.max(0, candles.size() - lookback);

        for (int i = start; i < candles.size(); i++) {
            swingLow = Math.min(swingLow, candles.get(i).getLow());
        }

        return swingLow;
    }

    /**
     * Check exit conditions for existing position
     */
    private void checkExitConditions(IntradaySignal newSignal, IntradaySignal existingSignal) {
        double currentPrice = newSignal.getCurrentPrice();
        double entryPrice = existingSignal.getEntryFilledPrice();

        // Check stop loss hit
        if (currentPrice <= existingSignal.getStopLoss()) {
            newSignal.setSignalType(SignalType.STOP_HIT);
            newSignal.setReason(newSignal.getReason() + " STOP LOSS HIT!");
            return;
        }

        // Check targets
        if (currentPrice >= existingSignal.getTarget2()) {
            newSignal.setSignalType(SignalType.EXIT);
            newSignal.setReason(newSignal.getReason() + " Target 2 reached - EXIT!");
        } else if (currentPrice >= existingSignal.getTarget1()) {
            newSignal.setSignalType(SignalType.PARTIAL_EXIT);
            newSignal.setReason(newSignal.getReason() + " Target 1 reached - book partial!");
        }

        // Check trend reversal
        if (newSignal.getTrend() == TrendDirection.STRONG_DOWN && currentPrice > entryPrice) {
            newSignal.setSignalType(SignalType.EXIT);
            newSignal.setReason(newSignal.getReason() + " Trend reversed - protect profits!");
        }
    }

    /**
     * Mark entry taken for a symbol
     */
    public void markEntryTaken(String symbol, double filledPrice) {
        IntradaySignal signal = activeSignals.get(symbol);
        if (signal != null) {
            signal.setEntryTaken(true);
            signal.setEntryFilledPrice(filledPrice);
        }
    }

    /**
     * Get current signal for a symbol
     */
    public IntradaySignal getSignal(String symbol) {
        return activeSignals.get(symbol);
    }

    /**
     * Get all active buy signals
     */
    public List<IntradaySignal> getActiveBuySignals() {
        List<IntradaySignal> buySignals = new ArrayList<>();

        for (IntradaySignal signal : activeSignals.values()) {
            if (signal.getSignalType() == SignalType.STRONG_BUY ||
                signal.getSignalType() == SignalType.BUY) {
                buySignals.add(signal);
            }
        }

        // Sort by score
        buySignals.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));

        return buySignals;
    }

    /**
     * Clear data for new day
     */
    public void clearDayData() {
        intradayDataMap.clear();
        activeSignals.clear();
    }

    /**
     * Print summary of all signals
     */
    public void printIntradaySummary() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("                  INTRADAY SIGNALS SUMMARY");
        System.out.println("=".repeat(70));

        List<IntradaySignal> buySignals = getActiveBuySignals();

        System.out.printf("\nActive Buy Signals: %d\n", buySignals.size());

        if (!buySignals.isEmpty()) {
            System.out.println("\n" + "-".repeat(70));
            for (IntradaySignal signal : buySignals) {
                System.out.println(signal);
            }
        }

        // Count by signal type
        Map<SignalType, Long> signalCounts = new HashMap<>();
        for (IntradaySignal signal : activeSignals.values()) {
            signalCounts.merge(signal.getSignalType(), 1L, Long::sum);
        }

        System.out.println("\n" + "-".repeat(70));
        System.out.println("Signal Distribution:");
        for (SignalType type : SignalType.values()) {
            long count = signalCounts.getOrDefault(type, 0L);
            if (count > 0) {
                System.out.printf("  %s: %d\n", type.getDescription(), count);
            }
        }
    }

    // Configuration setters
    public void setOpeningRangeMinutes(int minutes) {
        this.openingRangeMinutes = minutes;
    }

    public void setMaxStopLossPercent(double percent) {
        this.maxStopLossPercent = percent;
    }

    public void setMinRiskReward(double ratio) {
        this.minRiskReward = ratio;
    }
}

