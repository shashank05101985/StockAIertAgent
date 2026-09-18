package org.example.analysis;

import org.example.model.StockData;
import org.example.model.StockSignal;
import org.example.model.StockSignal.SignalType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Main Stock Analyzer class
 * Analyzes OHLC data with delivery data to identify potential short-term movers
 */
public class StockAnalyzer {

    // Configuration parameters
    private int shortPeriod = 5;       // Short-term analysis period
    private int mediumPeriod = 10;     // Medium-term analysis period
    private int longPeriod = 20;       // Long-term analysis period
    private double volumeBreakoutThreshold = 1.5;  // 50% above average
    private double deliveryBreakoutThreshold = 1.2; // 20% above average
    private double minDeliveryPercent = 40.0;      // Minimum delivery percentage
    private double stopLossPercent = 3.0;          // Default stop loss percentage
    private double riskRewardMin = 2.0;            // Minimum risk:reward ratio

    /**
     * Analyze all stocks and return signals
     */
    public List<StockSignal> analyzeAllStocks(Map<String, List<StockData>> stockDataMap) {
        List<StockSignal> signals = new ArrayList<>();

        for (Map.Entry<String, List<StockData>> entry : stockDataMap.entrySet()) {
            String symbol = entry.getKey();
            List<StockData> data = entry.getValue();

            // Need minimum data for analysis
            if (data.size() < longPeriod) {
                continue;
            }

            StockSignal signal = analyzeStock(symbol, data);
            if (signal != null) {
                // Include all analyzed stocks, even those with low/zero scores
                // This ensures AVOID signals are counted in distribution
                signals.add(signal);
            }
        }

        // Sort by score (highest first)
        signals.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));

        return signals;
    }

    /**
     * Analyze a single stock and generate signal
     */
    public StockSignal analyzeStock(String symbol, List<StockData> data) {
        if (data.size() < longPeriod) {
            return null;
        }

        StockData latest = data.get(data.size() - 1);
        StockSignal signal = new StockSignal();
        signal.setSymbol(symbol);
        signal.setCurrentPrice(latest.getClose());

        // Calculate score based on multiple criteria
        int score = 0;
        StringBuilder reasons = new StringBuilder();

        // 1. Volume Analysis (Range: -15 to +20 points)
        int volumeScore = analyzeVolume(data, signal, reasons);
        score += volumeScore;

        // 2. Delivery Analysis (Range: -15 to +25 points)
        int deliveryScore = analyzeDelivery(data, signal, reasons);
        score += deliveryScore;

        // 3. Price Trend Analysis (Range: -10 to +20 points)
        int trendScore = analyzeTrend(data, signal, reasons);
        score += trendScore;

        // 4. Technical Indicators (Range: -15 to +20 points)
        int technicalScore = analyzeTechnicals(data, signal, reasons);
        score += technicalScore;

        // 5. Candlestick Pattern (Range: -10 to +15 points)
        int patternScore = analyzePatterns(data, signal, reasons);
        score += patternScore;

        // Score can be negative for very bearish stocks, cap between 0 and 100
        signal.setScore(Math.max(0, Math.min(100, score)));
        signal.setReason(reasons.toString().trim());

        // Store raw score for signal determination (before capping)
        int rawScore = score;

        // Calculate entry, stop-loss and targets
        calculateLevels(data, signal);

        // Determine signal type based on raw score and indicators
        if(symbol.equals("EMBDL"))
        {
            System.out.printf(
                    "DEBUG %s: score=%d, rsi=%.2f, bb=%.2f, momentum=%.2f, priceBreakout=%s, volumeBreakout=%s, rr=%.2f%n",
                    symbol,
                    score,
                    signal.getRsi(),
                    signal.getBbPosition(),
                    signal.getMomentum(),
                    signal.isPriceBreakout(),
                    signal.isVolumeBreakout(),
                    signal.getRiskRewardRatio()
            );
        }
        signal.setSignalType(determineSignalType(rawScore, signal));




        return signal;
    }

    /**
     * Analyze volume patterns
     */
    private int analyzeVolume(List<StockData> data, StockSignal signal, StringBuilder reasons) {
        int score = 0;
        StockData latest = data.get(data.size() - 1);

        double avgVolume = TechnicalIndicators.calculateAverageVolume(data.subList(0, data.size() - 1), mediumPeriod);
        double volumeRatio = latest.getTradedQty() / avgVolume;

        signal.setVolumeChange((volumeRatio - 1) * 100);

        // Volume breakout detection
        boolean volumeBreakout = TechnicalIndicators.isVolumeBreakout(data, mediumPeriod, volumeBreakoutThreshold);
        signal.setVolumeBreakout(volumeBreakout);

        // Check if price moved up or down
        boolean priceUp = latest.isBullish(); // Close > Open
        double priceChange = latest.getChangePercent();
        double deliveryPct = latest.getDeliveryPercent();

        // =========================================================
        // CONTEXT-AWARE VOLUME ANALYSIS
        // High volume is only bullish if price is rising
        // High volume with falling price = distribution (bearish)
        // =========================================================

        if (volumeBreakout || volumeRatio > 1.5) {
            if (priceUp && priceChange > 1) {
                // High volume + price up = accumulation (bullish)
                score += 15;
                reasons.append(String.format("Volume breakout with price rise (%.1fx avg) - accumulation. ", volumeRatio));
            } else if (!priceUp && priceChange < -1) {
                // High volume + price down = distribution (bearish)
                score -= 10;
                reasons.append(String.format("⚠️ High volume with price drop (%.1fx avg) - distribution/selling. ", volumeRatio));

                // Extra penalty if delivery is also low (speculation)
                if (deliveryPct < 35) {
                    score -= 5;
                    reasons.append("Low delivery on down day - speculative selling. ");
                }
            } else {
                // High volume with small price change - could be churning
                score += 3;
                reasons.append(String.format("High volume (%.1fx avg) with small price change. ", volumeRatio));
            }
        } else if (volumeRatio > 1.2) {
            if (priceUp) {
                score += 8;
                reasons.append("Above avg volume with positive price. ");
            } else if (priceChange < -0.5) {
                // Moderate volume on down day
                score -= 3;
                reasons.append("Above avg volume on down day. ");
            } else {
                score += 4;
                reasons.append("Above avg volume. ");
            }
        } else if (volumeRatio < 0.7) {
            score -= 5;
            reasons.append("Low volume - lack of interest. ");
        }

        // Increasing volume trend (only positive if price also rising)
        if (data.size() >= 3) {
            boolean increasingVolume = true;
            boolean priceRising = true;
            for (int i = data.size() - 3; i < data.size() - 1; i++) {
                if (data.get(i + 1).getTradedQty() < data.get(i).getTradedQty()) {
                    increasingVolume = false;
                }
                if (data.get(i + 1).getClose() < data.get(i).getClose()) {
                    priceRising = false;
                }
            }
            if (increasingVolume && priceRising) {
                score += 5;
                reasons.append("Rising volume with rising price. ");
            } else if (increasingVolume && !priceRising) {
                score -= 3;
                reasons.append("Rising volume with falling price - bearish divergence. ");
            }
        }

        return Math.max(-15, Math.min(20, score));  // Allow negative score for bearish signals
    }

    /**
     * Analyze delivery percentage patterns
     */
    private int analyzeDelivery(List<StockData> data, StockSignal signal, StringBuilder reasons) {
        int score = 0;
        StockData latest = data.get(data.size() - 1);

        double currentDelivery = latest.getDeliveryPercent();
        signal.setDeliveryPercent(currentDelivery);

        double avgDelivery = TechnicalIndicators.calculateAverageDeliveryPercent(data.subList(0, data.size() - 1),
                mediumPeriod);

        // High delivery percentage indicates institutional buying
        boolean deliveryBreakout = TechnicalIndicators.isDeliveryBreakout(data, mediumPeriod,
                deliveryBreakoutThreshold);
        signal.setDeliveryBreakout(deliveryBreakout);

        // Check price direction for context
        double priceChange = latest.getChangePercent();
        boolean priceUp = latest.isBullish();
        double volumeRatio = signal.getVolumeChange() / 100.0 + 1; // Convert back to ratio

        // =========================================================
        // CONTEXT-AWARE DELIVERY ANALYSIS
        // High delivery on up days = accumulation (bullish)
        // Low delivery on down days with high volume = distribution (bearish)
        // =========================================================

        if (currentDelivery >= 60) {
            if (priceUp) {
                score += 20;
                reasons.append(String.format("Very high delivery (%.1f%%) with price up - strong accumulation. ", currentDelivery));
            } else {
                score += 12;
                reasons.append(String.format("High delivery (%.1f%%) despite price drop - possible support. ", currentDelivery));
            }
        } else if (currentDelivery >= 50) {
            if (priceUp) {
                score += 15;
                reasons.append(String.format("High delivery (%.1f%%) - institutional interest. ", currentDelivery));
            } else {
                score += 8;
                reasons.append(String.format("Good delivery (%.1f%%) on down day. ", currentDelivery));
            }
        } else if (currentDelivery >= minDeliveryPercent) {
            score += 10;
            reasons.append(String.format("Good delivery (%.1f%%). ", currentDelivery));
        } else if (currentDelivery < 30) {
            // Low delivery - check if bearish context
            if (!priceUp && priceChange < -0.5 && volumeRatio > 1.3) {
                // Low delivery + down day + high volume = distribution
                score -= 12;
                reasons.append(String.format("⚠️ Low delivery (%.1f%%) with high volume selling - distribution pattern. ", currentDelivery));
            } else if (!priceUp) {
                score -= 8;
                reasons.append(String.format("Low delivery (%.1f%%) on down day - weak hands selling. ", currentDelivery));
            } else {
                score -= 5;
                reasons.append(String.format("Low delivery (%.1f%%) - speculative trading. ", currentDelivery));
            }
        } else if (currentDelivery < minDeliveryPercent) {
            // 30-40% delivery
            if (!priceUp && priceChange < -0.5) {
                score -= 3;
                reasons.append(String.format("Below avg delivery (%.1f%%) on down day. ", currentDelivery));
            }
        }

        // Delivery trend analysis
        if (deliveryBreakout && priceUp) {
            score += 5;
            reasons.append("Delivery breakout with rising price. ");
        } else if (currentDelivery < avgDelivery * 0.8 && !priceUp) {
            // Delivery dropped significantly on down day
            score -= 3;
            reasons.append("Falling delivery trend. ");
        }

        return Math.max(-15, Math.min(25, score));  // Allow negative for bearish signals
    }

    /**
     * Analyze price trend
     */
    private int analyzeTrend(List<StockData> data, StockSignal signal, StringBuilder reasons) {
        int score = 0;
        StockData latest = data.get(data.size() - 1);

        // Check price breakout
        boolean priceBreakout = TechnicalIndicators.isPriceBreakout(data, mediumPeriod);
        signal.setPriceBreakout(priceBreakout);

        boolean priceBreakDown = TechnicalIndicators.isPriceBreakdown(data, mediumPeriod);
        signal.setPriceBreakdown(priceBreakDown);

        // Moving average analysis
        double sma5 = TechnicalIndicators.calculateSMA(data, shortPeriod);
        double sma10 = TechnicalIndicators.calculateSMA(data, mediumPeriod);
        double sma20 = TechnicalIndicators.calculateSMA(data, longPeriod);
        double currentPrice = latest.getClose();

        // Price above all MAs (bullish)
        if (currentPrice > sma5 && sma5 > sma10 && sma10 > sma20) {
            score += 12;
            reasons.append("Strong uptrend (Price > SMA5 > SMA10 > SMA20). ");
        } else if (currentPrice > sma5 && currentPrice > sma10) {
            score += 8;
            reasons.append("Above short-term MAs. ");
        } else if (currentPrice < sma20) {
            score -= 5;
        }

        // Price breakout
        if (priceBreakout) {
            score += 8;
            reasons.append("Price breakout above resistance. ");
        }

        // Momentum
        double momentum = TechnicalIndicators.calculateMomentum(data, shortPeriod);
        signal.setMomentum(momentum);
        if (momentum > 5) {
            score += 5;
            reasons.append(String.format("Strong momentum (+%.1f%%). ", momentum));
        } else if (momentum > 0) {
            score += 2;
        } else if (momentum < -5) {
            score -= 5;
        }

        return Math.max(0, Math.min(20, score));
    }

    /**
     * Analyze technical indicators
     */
    private int analyzeTechnicals(List<StockData> data, StockSignal signal, StringBuilder reasons) {

        if (data == null || data.size() < 20) {
            return 0;
        }

        int score = 0;

        StockData latest = data.get(data.size() - 1);
        double currentPrice = latest.getClose();

        // =========================================================
        // 1. RSI ANALYSIS
        // Maximum: +8
        // =========================================================

        double rsi = TechnicalIndicators.calculateRSI(data, 14);
        signal.setRsi(rsi);

        if (rsi >= 55 && rsi < 65) {

            // Healthy bullish momentum
            score += 8;
            reasons.append(String.format("RSI healthy bullish (%.1f). ", rsi));

        } else if (rsi >= 65 && rsi < 70) {

            // Strong momentum, approaching overbought
            score += 6;

            reasons.append(String.format("RSI strong bullish (%.1f). ", rsi));

        } else if (rsi >= 50 && rsi < 55) {

            // Mild bullish momentum
            score += 4;

            reasons.append(String.format("RSI mildly bullish (%.1f). ", rsi));

        } else if (rsi >= 40 && rsi < 50) {

            // Neutral momentum
            reasons.append(String.format("RSI neutral (%.1f). ", rsi));

        } else if (rsi >= 30 && rsi < 40) {

            // Weak momentum
            score -= 1;

            reasons.append(String.format("RSI weak (%.1f). ", rsi));

        } else if (rsi < 30) {

            // Oversold is not automatically bullish
            score -= 1;

            reasons.append(String.format("RSI oversold (%.1f) - reversal not confirmed. ", rsi));

        } else if (rsi >= 70 && rsi < 75) {

            // Strong momentum but becoming extended
            score += 2;

            reasons.append(String.format("RSI overbought (%.1f) - strong momentum but extended. ", rsi));

        } else if (rsi >= 75 && rsi < 80) {

            // High extension
            score += 0;

            reasons.append(String.format("RSI highly overbought (%.1f) - pullback risk. ", rsi));

        } else {

            // RSI >= 80
            score -= 3;

            reasons.append(String.format("RSI extremely overbought (%.1f) - high pullback risk. ", rsi));
        }


        // =========================================================
        // 2. BOLLINGER BANDS
        // Maximum: +6
        // =========================================================

        double[] bb = TechnicalIndicators.calculateBollingerBands(data, 20, 2.0);

        if (bb[1] > 0 && bb[2] > bb[0]) {

            double lowerBand = bb[0];
            double middleBand = bb[1];
            double upperBand = bb[2];

            double bandWidth = ((upperBand - lowerBand) / middleBand) * 100.0;

            double bbPosition = ((currentPrice - lowerBand) / (upperBand - lowerBand)) * 100.0;
            signal.setBbPosition(bbPosition);

            // -----------------------------------------------------
            // Strong bullish zone
            // -----------------------------------------------------

            if (currentPrice > middleBand && currentPrice < upperBand * 0.90) {

                score += 6;

                reasons.append(String.format("Price above BB middle (%.0f%% position). ", bbPosition));
            }

            // -----------------------------------------------------
            // Bullish but approaching upper band
            // -----------------------------------------------------

            else if (currentPrice >= upperBand * 0.90 && currentPrice < upperBand) {

                score += 4;

                reasons.append(String.format("Price near upper BB (%.0f%% position). ", bbPosition));
            }

            // -----------------------------------------------------
            // Breakout above upper band
            // -----------------------------------------------------

            else if (currentPrice >= upperBand) {

                score += 1;

                reasons.append(String.format("Price above upper BB (%.0f%% position) - extended. ", bbPosition));
            }

            // -----------------------------------------------------
            // Below middle band
            // -----------------------------------------------------

            else if (currentPrice >= lowerBand && currentPrice <= middleBand) {

                score -= 1;

                reasons.append("Price below BB middle - weak technical momentum. ");
            }

            // -----------------------------------------------------
            // Below lower band
            // -----------------------------------------------------

            else {

                score -= 1;

                reasons.append("Price below lower BB - reversal requires confirmation. ");
            }

            // -----------------------------------------------------
            // Bollinger squeeze
            // -----------------------------------------------------

            if (bandWidth < 5.0) {

                reasons.append(String.format("BB squeeze detected (%.1f%% width). ", bandWidth));
            }

            // -----------------------------------------------------
            // COMBINED OVERBOUGHT PENALTY
            // When multiple extreme indicators align, add extra penalty
            // -----------------------------------------------------

            double momentum = TechnicalIndicators.calculateMomentum(data, shortPeriod);

            if (rsi >= 80 && bbPosition >= 95) {
                // Extremely extended - high pullback risk
                score -= 8;
                reasons.append("⚠️ EXTREME EXTENSION (RSI + BB) - high pullback risk. ");
            } else if (rsi >= 75 && bbPosition >= 90 && momentum >= 10) {
                // Very extended with high momentum
                score -= 5;
                reasons.append("⚠️ Extended technicals - consider waiting for pullback. ");
            } else if (rsi >= 70 && bbPosition >= 85) {
                // Moderately extended
                score -= 2;
                reasons.append("Approaching overbought zone. ");
            }
        }


        // =========================================================
        // 3. CONSOLIDATION / BREAKOUT
        // Maximum: +6
        // =========================================================

        boolean consolidating = false;

        if (data.size() > shortPeriod + 1) {

            List<StockData> previousData = data.subList(0, data.size() - 1);

            consolidating = TechnicalIndicators.isConsolidating(previousData, shortPeriod, 5.0);
        }

        double dailyChange = latest.getChangePercent();

        if (consolidating) {

            if (dailyChange >= 4.0) {

                score += 6;

                reasons.append(String.format("Strong breakout from consolidation (+%.1f%%). ", dailyChange));

            } else if (dailyChange >= 3.0) {

                score += 5;

                reasons.append(String.format("Confirmed consolidation breakout (+%.1f%%). ", dailyChange));

            } else if (dailyChange >= 2.0) {

                score += 3;

                reasons.append(String.format("Early consolidation breakout (+%.1f%%). ", dailyChange));

            } else if (dailyChange > 0) {

                score += 1;

                reasons.append("Consolidation with positive price movement. ");
            }

        } else {

            // No consolidation breakout.
            // Do not penalize a normal trending stock.
        }


        // =========================================================
        // 4. TECHNICAL SCORE
        // Maximum = 20
        // =========================================================

        return Math.max(0, Math.min(20, score));
    }

    /**
     * Analyze candlestick patterns
     */
    private int analyzePatterns(List<StockData> data, StockSignal signal, StringBuilder reasons) {
        int score = 0;

        String pattern = TechnicalIndicators.detectCandlePattern(data);
        StockData latest = data.get(data.size() - 1);

        // Calculate candle characteristics
        double bodyPercent = (latest.getBody() / latest.getRange()) * 100;
        boolean strongCandle = bodyPercent > 70;

        if (pattern.contains("Bullish Engulfing") || pattern.contains("Bullish Marubozu")) {
            score += 15;
            reasons.append(pattern).append(". ");
        } else if (pattern.contains("Hammer")) {
            score += 12;
            reasons.append(pattern).append(". ");
        } else if (pattern.contains("Bullish")) {
            if (strongCandle) {
                score += 10;
                reasons.append("Strong Bullish Candle. ");
            } else {
                score += 6;
                reasons.append(pattern).append(". ");
            }
        } else if (pattern.contains("Doji")) {
            score += 2;
            reasons.append(pattern).append(". ");
        } else if (pattern.contains("Bearish Engulfing") || pattern.contains("Bearish Marubozu")) {
            score -= 10;
            reasons.append("⚠️ ").append(pattern).append(" - strong selling. ");
        } else if (pattern.contains("Bearish")) {
            if (strongCandle) {
                score -= 8;
                reasons.append("Strong Bearish Candle - selling pressure. ");
            } else {
                score -= 5;
                reasons.append(pattern).append(". ");
            }
        }

        return Math.max(-10, Math.min(15, score));
    }

    /**
     * Determine signal type based on score
     */
    private SignalType determineSignalType(int score) {
        if (score >= 70) {
            return SignalType.STRONG_BUY;
        } else if (score >= 55) {
            return SignalType.BUY;
        } else if (score >= 40) {
            return SignalType.WEAK_BUY;
        } else if (score >= 25) {
            return SignalType.HOLD;
        } else {
            return SignalType.AVOID;
        }
    }

    private SignalType determineSignalType(int score, StockSignal signal) {

        double rsi = signal.getRsi();
        double bbPosition = signal.getBbPosition();
        double momentum = signal.getMomentum();
        double deliveryPct = signal.getDeliveryPercent();
        double volumeChange = signal.getVolumeChange();

        // =========================================================
        // DISTRIBUTION PATTERN DETECTION (Only clear patterns)
        // High volume + falling price + low delivery = avoid
        // =========================================================

        // Classic distribution: Very high volume spike on down day with very low delivery
        if (volumeChange > 80 && deliveryPct < 30 && momentum < -2) {
            return SignalType.AVOID;
        }

        // Strong distribution pattern with low score
        if (volumeChange > 50 && deliveryPct < 35 && momentum < -1.5 && score <= 20) {
            return SignalType.AVOID;
        }

        // =========================================================
        // EXTREME OVERBOUGHT - AVOID/HOLD
        // =========================================================

        if (rsi >= 85 && bbPosition >= 98) {
            // Extremely overbought on both RSI and BB - avoid buying
            return SignalType.AVOID;
        }

        if (rsi >= 80 && bbPosition >= 95 && momentum >= 15) {
            // Very extended - hold only
            return SignalType.HOLD;
        }

        // =========================================================
        // STRONG BULLISH (Relaxed criteria)
        // =========================================================

        // Option 1: Perfect setup with all breakouts
        if (score >= 75
                && signal.isPriceBreakout()
                && signal.isVolumeBreakout()
                && signal.isDeliveryBreakout()
                && rsi >= 50 && rsi < 75
                && bbPosition < 90) {
            return SignalType.STRONG_BUY;
        }

        // Option 2: Very high score with good technicals
        if (score >= 80
                && signal.isPriceBreakout()
                && (signal.isVolumeBreakout() || signal.isDeliveryBreakout())
                && rsi < 75) {
            return SignalType.STRONG_BUY;
        }

        // =========================================================
        // BULLISH
        // =========================================================

        // Good breakout setup
        if (score >= 65
                && signal.isPriceBreakout()
                && (signal.isVolumeBreakout() || deliveryPct >= 45)
                && rsi < 75
                && rsi >= 45) {
            return SignalType.BUY;
        }

        // High score with momentum
        if (score >= 60
                && momentum > 3
                && rsi >= 50 && rsi < 70
                && deliveryPct >= 40) {
            return SignalType.BUY;
        }

        // =========================================================
        // WEAK BUY
        // =========================================================

        if (score >= 50
                && signal.isPriceBreakout()
                && rsi < 75
                && rsi >= 40) {
            return SignalType.WEAK_BUY;
        }

        if (score >= 55
                && momentum > 0
                && rsi >= 45 && rsi < 70) {
            return SignalType.WEAK_BUY;
        }

        if (score >= 45
                && signal.isVolumeBreakout()
                && signal.isDeliveryBreakout()
                && momentum > 0) {
            return SignalType.WEAK_BUY;
        }

        // =========================================================
        // BEARISH SIGNALS
        // =========================================================

        // Very clear bearish patterns
        if (score <= 10 && momentum < -3) {
            return SignalType.AVOID;
        }

        if (score <= 15
                && signal.isPriceBreakdown()
                && momentum < -2
                && deliveryPct < 35) {
            return SignalType.STRONG_SELL;
        }

        if (score <= 25
                && signal.isPriceBreakdown()
                && momentum < -1
                && rsi < 45) {
            return SignalType.SELL;
        }

        if (score <= 30
                && signal.isPriceBreakdown()) {
            return SignalType.WEAK_SELL;
        }

        // =========================================================
        // DEFAULT: HOLD for neutral/unclear signals
        // =========================================================

        return SignalType.HOLD;
    }

    /**
     * Calculate entry, stop-loss and target levels
     */
    private void calculateLevels(List<StockData> data, StockSignal signal) {

        if (data == null || data.size() < 20) {
            return;
        }

        StockData latest = data.get(data.size() - 1);

        double currentPrice = latest.getClose();

        // =========================================================
        // ATR
        // =========================================================

        double atr = TechnicalIndicators.calculateATR(data, 14);

        // Safety fallback
        if (atr <= 0) {
            atr = currentPrice * 0.02;
        }


        // =========================================================
        // SUPPORT / RESISTANCE
        // =========================================================

        double[] sr = TechnicalIndicators.calculateSupportResistance(data, mediumPeriod);

        double support = sr[0];
        double averageResistance = sr[2];
        double strongResistance = sr[3];


        // =========================================================
        // ENTRY
        // =========================================================

        double entryPrice = currentPrice;

        if (signal.isPriceBreakout()) {

            /*
             * For a breakout trade, don't enter at an arbitrary
             * 1% above resistance if the current price has already
             * broken out.
             *
             * Current price is treated as the entry.
             */

            entryPrice = currentPrice;
        }

        entryPrice = Math.round(entryPrice * 100.0) / 100.0;

        signal.setEntryPrice(entryPrice);


        // =========================================================
        // STOP LOSS
        // =========================================================

        // ATR based stop
        double atrStop = entryPrice - (atr * 1.5);

        // Support based stop
        double supportStop = support > 0 ? support * 0.99 : Double.MIN_VALUE;

        // Percentage based stop
        double percentStop = entryPrice * (1.0 - stopLossPercent / 100.0);


        /*
         * Use the highest valid stop.
         *
         * This gives the tightest stop while keeping it
         * below the entry.
         */
        double stopLoss = Math.max(atrStop, Math.max(supportStop, percentStop));


        // Never allow SL >= entry
        if (stopLoss >= entryPrice) {
            stopLoss = percentStop;
        }


        stopLoss = Math.round(stopLoss * 100.0) / 100.0;

        signal.setStopLoss(stopLoss);


        // =========================================================
        // RISK
        // =========================================================

        double risk = entryPrice - stopLoss;

        if (risk <= 0) {

            signal.setTarget1(0);
            signal.setTarget2(0);
            signal.setTarget3(0);
            signal.setRiskRewardRatio(0);

            return;
        }


        // =========================================================
        // TARGETS
        // =========================================================

        /*
         * Base targets from risk/reward.
         */

        double rrTarget1 = entryPrice + (risk * 1.5);

        double rrTarget2 = entryPrice + (risk * 2.5);

        double rrTarget3 = entryPrice + (risk * 4.0);


        // =========================================================
        // RESISTANCE AWARE TARGETS
        // =========================================================

        /*
         * Only use resistance levels that are ABOVE entry.
         */

        double target1 = rrTarget1;
        double target2 = rrTarget2;
        double target3 = rrTarget3;


        // ---------------------------------------------------------
        // Target 1
        // ---------------------------------------------------------

        if (averageResistance > entryPrice) {

            /*
             * Don't put Target 1 beyond nearby resistance.
             */
            target1 = Math.min(rrTarget1, averageResistance);
        }


        // ---------------------------------------------------------
        // Target 2
        // ---------------------------------------------------------

        if (strongResistance > entryPrice) {

            target2 = Math.min(rrTarget2, strongResistance);
        }


        // ---------------------------------------------------------
        // Target 3
        // ---------------------------------------------------------

        /*
         * Target 3 is primarily risk/reward based.
         *
         * If strong resistance is above entry, allow a breakout
         * continuation target beyond that resistance.
         */

        if (strongResistance > entryPrice) {

            double resistanceBreakoutTarget = strongResistance * 1.05;

            target3 = Math.max(rrTarget3, resistanceBreakoutTarget);
        }


        // =========================================================
        // VALIDATE TARGET ORDER
        // =========================================================

        /*
         * Every target must be above entry.
         */

        target1 = Math.max(target1, entryPrice + risk);

        target2 = Math.max(target2, target1 + risk);

        target3 = Math.max(target3, target2 + risk);


        // =========================================================
        // ROUND TARGETS
        // =========================================================

        target1 = Math.round(target1 * 100.0) / 100.0;

        target2 = Math.round(target2 * 100.0) / 100.0;

        target3 = Math.round(target3 * 100.0) / 100.0;


        signal.setTarget1(target1);
        signal.setTarget2(target2);
        signal.setTarget3(target3);


        // =========================================================
        // FINAL RISK / REWARD
        // =========================================================

        double reward = target2 - entryPrice;

        double riskReward = reward / risk;

        signal.setRiskRewardRatio(Math.round(riskReward * 100.0) / 100.0);
    }

    /**
     * Get top N stocks with highest potential
     */
    public List<StockSignal> getTopPicks(List<StockSignal> allSignals, int topN, int minScore) {
        return allSignals.stream().filter(s -> s.getScore() >= minScore).filter(
                s -> s.getSignalType() == SignalType.STRONG_BUY || s.getSignalType() == SignalType.BUY).filter(
                s -> s.getRiskRewardRatio() >= riskRewardMin).limit(topN).collect(Collectors.toList());
    }

    /**
     * Get stocks with volume and delivery breakout
     */
    public List<StockSignal> getBreakoutStocks(List<StockSignal> allSignals) {
        return allSignals.stream().filter(s -> s.isVolumeBreakout() && s.isDeliveryBreakout()).filter(
                s -> s.getScore() >= 40).collect(Collectors.toList());
    }

    /**
     * Print analysis summary
     */
    public void printSummary(List<StockSignal> signals) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("                    STOCK ANALYSIS SUMMARY");
        System.out.println("=".repeat(70));

        long strongBuy = signals.stream().filter(s -> s.getSignalType() == SignalType.STRONG_BUY).count();
        long buy = signals.stream().filter(s -> s.getSignalType() == SignalType.BUY).count();
        long weakBuy = signals.stream().filter(s -> s.getSignalType() == SignalType.WEAK_BUY).count();
        long hold = signals.stream().filter(s -> s.getSignalType() == SignalType.HOLD).count();
        long weakSell = signals.stream().filter(s -> s.getSignalType() == SignalType.WEAK_SELL).count();
        long sell = signals.stream().filter(s -> s.getSignalType() == SignalType.SELL).count();
        long strongSell = signals.stream().filter(s -> s.getSignalType() == SignalType.STRONG_SELL).count();
        long avoid = signals.stream().filter(s -> s.getSignalType() == SignalType.AVOID).count();

        System.out.println("\nSignal Distribution:");
        System.out.printf("  Strong Buy  : %d stocks\n", strongBuy);
        System.out.printf("  Buy         : %d stocks\n", buy);
        System.out.printf("  Weak Buy    : %d stocks\n", weakBuy);
        System.out.printf("  Hold        : %d stocks\n", hold);
        System.out.printf("  Weak Sell   : %d stocks\n", weakSell);
        System.out.printf("  Sell        : %d stocks\n", sell);
        System.out.printf("  Strong Sell : %d stocks\n", strongSell);
        System.out.printf("  Avoid       : %d stocks\n", avoid);

        // Show distribution warning stocks
        if (avoid > 0) {
            System.out.println("\n" + "-".repeat(70));
            System.out.println("⚠️ AVOID - Distribution/High Risk Stocks:");
            System.out.println("-".repeat(70));

            List<StockSignal> avoidStocks = signals.stream()
                .filter(s -> s.getSignalType() == SignalType.AVOID)
                .limit(5)
                .toList();

            for (StockSignal signal : avoidStocks) {
                System.out.println(signal);
            }
        }

        System.out.println("\n" + "-".repeat(70));
        System.out.println("Top Picks with Volume + Delivery Breakouts:");
        System.out.println("-".repeat(70));

        List<StockSignal> breakouts = getBreakoutStocks(signals);
        if (breakouts.isEmpty()) {
            System.out.println("No stocks with combined volume and delivery breakout found.");
        } else {
            for (StockSignal signal : breakouts.subList(0, Math.min(5, breakouts.size()))) {
                System.out.println(signal);
            }
        }

        System.out.println("\n" + "-".repeat(70));
        System.out.println("Top 10 High Potential Stocks:");
        System.out.println("-".repeat(70));

        List<StockSignal> topPicks = getTopPicks(signals, 10, 50);
        if (topPicks.isEmpty()) {
            System.out.println("No stocks meeting minimum criteria found.");
        } else {
            for (StockSignal signal : topPicks) {
                System.out.println(signal);
            }
        }
    }

    // Getters and setters for configuration
    public void setStopLossPercent(double stopLossPercent) {
        this.stopLossPercent = stopLossPercent;
    }

    public void setMinDeliveryPercent(double minDeliveryPercent) {
        this.minDeliveryPercent = minDeliveryPercent;
    }

    public void setVolumeBreakoutThreshold(double volumeBreakoutThreshold) {
        this.volumeBreakoutThreshold = volumeBreakoutThreshold;
    }

    public void setRiskRewardMin(double riskRewardMin) {
        this.riskRewardMin = riskRewardMin;
    }
}