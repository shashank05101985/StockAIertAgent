package org.example.analysis;

import org.example.model.StockDailyFeature;
import org.example.model.StockData;

import java.util.*;

/**
 * Technical Indicators Calculator
 * Calculates various technical indicators for stock analysis
 */
public class TechnicalIndicators {

    /**
     * Calculate Simple Moving Average (SMA)
     */
    public static double calculateSMA(List<StockData> data, int period) {
        if (data.size() < period)
            return 0;

        double sum = 0;
        for (int i = data.size() - period; i < data.size(); i++) {
            sum += data.get(i).getClose();
        }
        return sum / period;
    }


    /**
     * Calculate Exponential Moving Average (EMA)
     */
    public static double calculateEMA(List<StockData> data, int period) {
        if (data.size() < period)
            return 0;

        double multiplier = 2.0 / (period + 1);
        double ema = calculateSMA(data.subList(0, period), period);

        for (int i = period; i < data.size(); i++) {
            ema = (data.get(i).getClose() - ema) * multiplier + ema;
        }
        return ema;
    }

    /**
     * Calculate RSI (Relative Strength Index)
     */
    public static double calculateRSI(List<StockData> data, int period) {
        if (data.size() < period + 1)
            return 50;

        double avgGain = 0, avgLoss = 0;

        // Calculate initial average gain/loss
        for (int i = 1; i <= period; i++) {
            double change = data.get(i).getClose() - data.get(i - 1).getClose();
            if (change > 0)
                avgGain += change;
            else
                avgLoss += Math.abs(change);
        }
        avgGain /= period;
        avgLoss /= period;

        // Calculate RSI using smoothed averages
        for (int i = period + 1; i < data.size(); i++) {
            double change = data.get(i).getClose() - data.get(i - 1).getClose();
            if (change > 0) {
                avgGain = (avgGain * (period - 1) + change) / period;
                avgLoss = (avgLoss * (period - 1)) / period;
            } else {
                avgGain = (avgGain * (period - 1)) / period;
                avgLoss = (avgLoss * (period - 1) + Math.abs(change)) / period;
            }
        }

        if (avgLoss == 0)
            return 100;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    /**
     * Calculate Average True Range (ATR)
     */
    public static double calculateATR(List<StockData> data, int period) {
        if (data.size() < period + 1)
            return 0;

        List<Double> trueRanges = new ArrayList<>();

        for (int i = 1; i < data.size(); i++) {
            double high = data.get(i).getHigh();
            double low = data.get(i).getLow();
            double prevClose = data.get(i - 1).getClose();

            double tr = Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
            trueRanges.add(tr);
        }

        // Calculate ATR as SMA of True Ranges
        double sum = 0;
        int start = Math.max(0, trueRanges.size() - period);
        for (int i = start; i < trueRanges.size(); i++) {
            sum += trueRanges.get(i);
        }
        return sum / period;
    }

    /**
     * Calculate Average Volume
     */
    public static double calculateAverageVolume(List<StockData> data, int period) {
        if (data.isEmpty())
            return 0;

        int count = Math.min(period, data.size());
        long sum = 0;
        for (int i = data.size() - count; i < data.size(); i++) {
            sum += data.get(i).getTradedQty();
        }
        return (double) sum / count;
    }

    /**
     * Calculate Average Delivery Percentage
     */
    public static double calculateAverageDeliveryPercent(List<StockData> data, int period) {
        if (data.isEmpty())
            return 0;

        int count = Math.min(period, data.size());
        double sum = 0;
        for (int i = data.size() - count; i < data.size(); i++) {
            sum += data.get(i).getDeliveryPercent();
        }
        return sum / count;
    }

    /**
     * Calculate VWAP (Volume Weighted Average Price)
     */
    public static double calculateVWAP(List<StockData> data) {
        if (data.isEmpty())
            return 0;

        double cumulativeTPV = 0;  // Typical Price * Volume
        long cumulativeVolume = 0;

        for (StockData stock : data) {
            double typicalPrice = (stock.getHigh() + stock.getLow() + stock.getClose()) / 3;
            cumulativeTPV += typicalPrice * stock.getTradedQty();
            cumulativeVolume += stock.getTradedQty();
        }

        return cumulativeVolume > 0 ? cumulativeTPV / cumulativeVolume : 0;
    }

    /**
     * Calculate Bollinger Bands
     * Returns: [Lower Band, Middle Band (SMA), Upper Band]
     */
    public static double[] calculateBollingerBands(List<StockData> data, int period, double stdDevMultiplier) {
        if (data.size() < period)
            return new double[]{0, 0, 0};

        double sma = calculateSMA(data, period);

        // Calculate Standard Deviation
        double sumSquaredDiff = 0;
        for (int i = data.size() - period; i < data.size(); i++) {
            double diff = data.get(i).getClose() - sma;
            sumSquaredDiff += diff * diff;
        }
        double stdDev = Math.sqrt(sumSquaredDiff / period);

        return new double[]{sma - (stdDevMultiplier * stdDev),  // Lower Band
            sma,                                  // Middle Band
            sma + (stdDevMultiplier * stdDev)    // Upper Band
        };
    }

    /**
     * Calculate Support and Resistance levels
     * Uses recent highs and lows
     */
    public static double[] calculateSupportResistance(List<StockData> data, int period) {
        if (data.isEmpty())
            return new double[]{0, 0, 0, 0};

        int count = Math.min(period, data.size());
        double highestHigh = Double.MIN_VALUE;
        double lowestLow = Double.MAX_VALUE;
        double sumHigh = 0, sumLow = 0;

        for (int i = data.size() - count; i < data.size(); i++) {
            highestHigh = Math.max(highestHigh, data.get(i).getHigh());
            lowestLow = Math.min(lowestLow, data.get(i).getLow());
            sumHigh += data.get(i).getHigh();
            sumLow += data.get(i).getLow();
        }

        // Support 1: Recent low, Support 2: Average low
        // Resistance 1: Average high, Resistance 2: Recent high
        return new double[]{lowestLow,                    // Strong Support
            sumLow / count,               // Average Support
            sumHigh / count,              // Average Resistance
            highestHigh                   // Strong Resistance
        };
    }

    /**
     * Check if there's a volume breakout
     */
    public static boolean isVolumeBreakout(List<StockData> data, int period, double threshold) {
        if (data.size() < period + 1)
            return false;

        double avgVolume = calculateAverageVolume(data.subList(0, data.size() - 1), period);
        long currentVolume = data.get(data.size() - 1).getTradedQty();

        return currentVolume > avgVolume * threshold;
    }

    /**
     * Check if there's a delivery percentage breakout
     */
    public static boolean isDeliveryBreakout(List<StockData> data, int period, double threshold) {
        if (data.size() < period + 1)
            return false;

        double avgDelivery = calculateAverageDeliveryPercent(data.subList(0, data.size() - 1), period);
        double currentDelivery = data.get(data.size() - 1).getDeliveryPercent();

        return currentDelivery > avgDelivery * threshold;
    }

    /**
     * Check if price broke above resistance
     */
    public static boolean isPriceBreakout(List<StockData> data, int period) {
        if (data.size() < period + 1)
            return false;

        double[] sr = calculateSupportResistance(data.subList(0, data.size() - 1), period);
        double currentClose = data.get(data.size() - 1).getClose();

        return currentClose > sr[2]; // Above average resistance
    }

    public static boolean isPriceBreakdown(List<StockData> data, int period) {

        if (data == null || data.size() < period + 1) {
            return false;
        }

        // Exclude today's candle from support calculation
        List<StockData> previousData = data.subList(0, data.size() - 1);

        double[] sr = calculateSupportResistance(previousData, period);

        // sr[0] = lowest low of previous period
        double support = sr[0];

        double currentClose = data.get(data.size() - 1).getClose();

        return currentClose < support;
    }

    /**
     * Detect candlestick patterns
     */
    public static String detectCandlePattern(List<StockData> data) {
        if (data.isEmpty())
            return "None";

        StockData latest = data.get(data.size() - 1);
        double body = latest.getBody();
        double range = latest.getRange();
        double upperWick = latest.getHigh() - Math.max(latest.getOpen(), latest.getClose());
        double lowerWick = Math.min(latest.getOpen(), latest.getClose()) - latest.getLow();

        // Doji
        if (body < range * 0.1) {
            return "Doji - Indecision";
        }

        // Hammer (Bullish)
        if (lowerWick > body * 2 && upperWick < body * 0.3 && latest.isBullish()) {
            return "Hammer - Bullish Reversal";
        }

        // Inverted Hammer
        if (upperWick > body * 2 && lowerWick < body * 0.3 && latest.isBullish()) {
            return "Inverted Hammer - Potential Reversal";
        }

        // Marubozu (Strong trend)
        if (upperWick < body * 0.05 && lowerWick < body * 0.05) {
            return latest.isBullish() ? "Bullish Marubozu - Strong Buy" : "Bearish Marubozu - Strong Sell";
        }

        // Engulfing patterns (need previous candle)
        if (data.size() >= 2) {
            StockData prev = data.get(data.size() - 2);

            // Bullish Engulfing
            if (prev.isBearish() && latest.isBullish() && latest.getOpen() < prev.getClose() && latest.getClose() > prev.getOpen()) {
                return "Bullish Engulfing - Strong Buy Signal";
            }

            // Bearish Engulfing
            if (prev.isBullish() && latest.isBearish() && latest.getOpen() > prev.getClose() && latest.getClose() < prev.getOpen()) {
                return "Bearish Engulfing - Sell Signal";
            }
        }

        return latest.isBullish() ? "Bullish Candle" : "Bearish Candle";
    }

    /**
     * Calculate momentum (Rate of Change)
     */
    public static double calculateMomentum(List<StockData> data, int period) {
        if (data.size() < period + 1)
            return 0;

        double currentPrice = data.get(data.size() - 1).getClose();
        double pastPrice = data.get(data.size() - 1 - period).getClose();

        return ((currentPrice - pastPrice) / pastPrice) * 100;
    }

    /**
     * Check for price consolidation
     */
    public static boolean isConsolidating(List<StockData> data, int period, double rangePercent) {
        if (data.size() < period)
            return false;

        double highestHigh = Double.MIN_VALUE;
        double lowestLow = Double.MAX_VALUE;

        for (int i = data.size() - period; i < data.size(); i++) {
            highestHigh = Math.max(highestHigh, data.get(i).getHigh());
            lowestLow = Math.min(lowestLow, data.get(i).getLow());
        }

        double range = ((highestHigh - lowestLow) / lowestLow) * 100;
        return range < rangePercent;
    }

    public static Double calculateReturn(List<StockData> data, int period) {

        if (data == null || data.size() <= period) {
            return null;
        }

        double current = data.get(data.size() - 1).getClose();

        double previous = data.get(data.size() - 1 - period).getClose();

        if (previous == 0) {
            return null;
        }

        return ((current / previous) - 1.0) * 100.0;
    }


    public static Double highestHigh(List<StockData> data, int period) {

        if (data == null || data.size() < period) {
            return null;
        }

        double highest = Double.NEGATIVE_INFINITY;

        for (int i = data.size() - period; i < data.size(); i++) {

            highest = Math.max(highest, data.get(i).getHigh());
        }

        return highest;
    }

    public static Double lowestLow(List<StockData> data, int period) {

        if (data == null || data.size() < period) {
            return null;
        }

        double lowest = Double.POSITIVE_INFINITY;

        for (int i = data.size() - period; i < data.size(); i++) {

            lowest = Math.min(lowest, data.get(i).getLow());
        }

        return lowest;
    }

    public static double[] calculateMACD(List<StockData> data, int fastPeriod, int slowPeriod, int signalPeriod) {

        if (data == null || data.size() < slowPeriod + signalPeriod) {

            return new double[]{0, 0, 0};
        }

        /*
         * Single pass: same result as calling calculateEMA() on every prefix
         * (each EMA is seeded with the SMA of its first `period` closes),
         * but O(n) instead of O(n^2).
         */
        List<Double> macdValues = new ArrayList<>();

        double fastMultiplier = 2.0 / (fastPeriod + 1);
        double slowMultiplier = 2.0 / (slowPeriod + 1);

        double fastEma = calculateSMA(data.subList(0, fastPeriod), fastPeriod);
        double slowEma = calculateSMA(data.subList(0, slowPeriod), slowPeriod);

        for (int i = fastPeriod; i < data.size(); i++) {

            double close = data.get(i).getClose();

            fastEma = (close - fastEma) * fastMultiplier + fastEma;

            if (i >= slowPeriod) {
                slowEma = (close - slowEma) * slowMultiplier + slowEma;
            }

            if (i >= slowPeriod - 1) {
                macdValues.add(fastEma - slowEma);
            }
        }

        if (macdValues.size() < signalPeriod) {
            return new double[]{0, 0, 0};
        }

        double macd = macdValues.get(macdValues.size() - 1);

        /*
         * Signal EMA directly over MACD values.
         */
        double signal = calculateEMAValues(macdValues, signalPeriod);

        return new double[]{macd, signal, macd - signal};
    }

    private static double calculateEMAValues(List<Double> values, int period) {

        if (values.size() < period) {
            return 0;
        }

        double sum = 0;

        for (int i = 0; i < period; i++) {
            sum += values.get(i);
        }

        double ema = sum / period;

        double multiplier = 2.0 / (period + 1);

        for (int i = period; i < values.size(); i++) {

            ema = (values.get(i) - ema) * multiplier + ema;
        }

        return ema;
    }

    public static double[] calculateStochastic(List<StockData> data, int period, int smoothPeriod) {

        if (data == null || data.size() < period) {

            return new double[]{0, 0};
        }

        List<Double> kValues = new ArrayList<>();

        for (int i = period - 1; i < data.size(); i++) {

            double highest = Double.NEGATIVE_INFINITY;

            double lowest = Double.POSITIVE_INFINITY;

            for (int j = i - period + 1; j <= i; j++) {

                highest = Math.max(highest, data.get(j).getHigh());

                lowest = Math.min(lowest, data.get(j).getLow());
            }

            double close = data.get(i).getClose();

            double k;

            if (highest == lowest) {
                k = 50.0;
            } else {
                k = ((close - lowest) / (highest - lowest)) * 100.0;
            }

            kValues.add(k);
        }

        double k = kValues.get(kValues.size() - 1);

        double d = calculateSimpleAverage(kValues, smoothPeriod);

        return new double[]{k, d};
    }

    private static double calculateSimpleAverage(List<Double> values, int period) {

        if (values.isEmpty()) {
            return 0;
        }

        int start = Math.max(0, values.size() - period);

        double sum = 0;
        int count = 0;

        for (int i = start; i < values.size(); i++) {

            sum += values.get(i);
            count++;
        }

        return count == 0 ? 0 : sum / count;
    }

    public static double calculateCCI(List<StockData> data, int period) {

        if (data == null || data.size() < period) {

            return 0;
        }

        List<Double> typicalPrices = new ArrayList<>();

        for (int i = data.size() - period; i < data.size(); i++) {

            StockData d = data.get(i);

            double tp = (d.getHigh() + d.getLow() + d.getClose()) / 3.0;

            typicalPrices.add(tp);
        }

        double mean = typicalPrices.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        double meanDeviation = 0;

        for (double tp : typicalPrices) {

            meanDeviation += Math.abs(tp - mean);
        }

        meanDeviation /= typicalPrices.size();

        if (meanDeviation == 0) {
            return 0;
        }

        double currentTP = typicalPrices.get(typicalPrices.size() - 1);

        return (currentTP - mean) / (0.015 * meanDeviation);
    }

    public static double[] calculateADX(List<StockData> data, int period) {

        if (data == null || data.size() < period + 1) {

            return new double[]{0, 0, 0};
        }

        List<Double> trValues = new ArrayList<>();

        List<Double> plusDMValues = new ArrayList<>();

        List<Double> minusDMValues = new ArrayList<>();

        for (int i = 1; i < data.size(); i++) {

            StockData current = data.get(i);

            StockData previous = data.get(i - 1);

            double upMove = current.getHigh() - previous.getHigh();

            double downMove = previous.getLow() - current.getLow();

            double plusDM = (upMove > downMove && upMove > 0) ? upMove : 0;

            double minusDM = (downMove > upMove && downMove > 0) ? downMove : 0;

            double tr = Math.max(current.getHigh() - current.getLow(),

                Math.max(Math.abs(current.getHigh() - previous.getClose()),

                    Math.abs(current.getLow() - previous.getClose())));

            trValues.add(tr);
            plusDMValues.add(plusDM);
            minusDMValues.add(minusDM);
        }

        if (trValues.size() < period) {
            return new double[]{0, 0, 0};
        }

        /*
         * Build DI / DX history.
         */
        List<Double> dxValues = new ArrayList<>();

        double plusDI = 0;
        double minusDI = 0;

        for (int i = period - 1; i < trValues.size(); i++) {

            double trSum = 0;
            double plusSum = 0;
            double minusSum = 0;

            int start = i - period + 1;

            for (int j = start; j <= i; j++) {

                trSum += trValues.get(j);
                plusSum += plusDMValues.get(j);
                minusSum += minusDMValues.get(j);
            }

            if (trSum == 0) {
                continue;
            }

            plusDI = (plusSum / trSum) * 100.0;

            minusDI = (minusSum / trSum) * 100.0;

            double denominator = plusDI + minusDI;

            if (denominator == 0) {
                continue;
            }

            double dx = Math.abs(plusDI - minusDI) / denominator * 100.0;

            dxValues.add(dx);
        }

        if (dxValues.isEmpty()) {
            return new double[]{0, 0, 0};
        }

        double adx = calculateSimpleAverage(dxValues, period);

        return new double[]{plusDI, minusDI, adx};
    }

    public static double calculateAverageRangePercent(List<StockData> data, int period) {

        if (data == null || data.size() < period) {
            return 0;
        }

        double sum = 0;

        for (int i = data.size() - period; i < data.size(); i++) {

            StockData d = data.get(i);

            if (d.getClose() == 0) {
                continue;
            }

            sum += ((d.getHigh() - d.getLow()) / d.getClose()) * 100.0;
        }

        return sum / period;
    }

    public static double calculateReturnVolatility(List<StockData> data, int period) {

        if (data == null || data.size() <= period) {

            return 0;
        }

        List<Double> returns = new ArrayList<>();

        int start = Math.max(1, data.size() - period);

        for (int i = start; i < data.size(); i++) {

            double previous = data.get(i - 1).getClose();

            double current = data.get(i).getClose();

            if (previous != 0) {

                returns.add(((current / previous) - 1.0) * 100.0);
            }
        }

        if (returns.size() < 2) {
            return 0;
        }

        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        double sum = 0;

        for (double r : returns) {
            sum += Math.pow(r - mean, 2);
        }

        return Math.sqrt(sum / returns.size());
    }

    public static double calculateVolumeRatio(List<StockData> data, int period) {

        if (data == null || data.size() < period + 1) {

            return 0;
        }

        /*
         * Exclude current candle.
         */
        List<StockData> previousData = data.subList(0, data.size() - 1);

        double avgVolume = calculateAverageVolume(previousData, period);

        if (avgVolume <= 0) {
            return 0;
        }

        long currentVolume = data.get(data.size() - 1).getTradedQty();

        return currentVolume / avgVolume;
    }

    public static double calculateOBV(List<StockData> data) {

        if (data == null || data.size() < 2) {

            return 0;
        }

        double obv = 0;

        for (int i = 1; i < data.size(); i++) {

            StockData current = data.get(i);

            StockData previous = data.get(i - 1);

            double currentClose = current.getClose();

            double previousClose = previous.getClose();

            long volume = current.getTradedQty();

            if (currentClose > previousClose) {

                obv += volume;

            } else if (currentClose < previousClose) {

                obv -= volume;
            }

            /*
             * Equal close:
             * OBV remains unchanged.
             */
        }

        return obv;
    }
    public static double calculateOBVChange(
        List<StockData> data,
        int period) {

        if (data == null ||
            data.size() < period + 2) {

            return 0;
        }

        double currentOBV =
            calculateOBV(data);

        List<StockData> previous =
            data.subList(
                0,
                data.size() - period
            );

        double previousOBV =
            calculateOBV(previous);

        if (previousOBV == 0) {
            return 0;
        }

        return
            ((currentOBV - previousOBV)
                / Math.abs(previousOBV))
                * 100.0;
    }

    public static void calculatePullbackRecovery(
        List<StockData> data,
        StockDailyFeature f) {

        if (data == null || data.size() < 7 || f == null) {
            return;
        }

        int currentIndex = data.size() - 1;

        StockData current = data.get(currentIndex);

        /*
         * =========================================================
         * PREVIOUS 5 DAYS
         *
         * IMPORTANT:
         * Exclude today's candle.
         * =========================================================
         */

        int startIndex = currentIndex - 5;

        List<StockData> previous5 =
            data.subList(startIndex, currentIndex);


        /*
         * =========================================================
         * PREVIOUS 5D HIGH / LOW
         * =========================================================
         */

        double previous5dHigh =
            previous5.stream()
                .mapToDouble(StockData::getHigh)
                .max()
                .orElse(0);

        double previous5dLow =
            previous5.stream()
                .mapToDouble(StockData::getLow)
                .min()
                .orElse(0);


        f.previous5dHigh = previous5dHigh;
        f.previous5dLow = previous5dLow;


        /*
         * =========================================================
         * DISTANCE FROM PREVIOUS 5D HIGH
         *
         * Example:
         *
         * previous high = 100
         * current price  = 98
         *
         * result = -2%
         * =========================================================
         */

        if (previous5dHigh > 0) {

            f.distanceToPrevious5dHigh =
                ((current.getClose() - previous5dHigh)
                    / previous5dHigh) * 100.0;
        }


        /*
         * =========================================================
         * PULLBACK DEPTH
         *
         * Previous high → previous low
         *
         * Example:
         *
         * high = 100
         * low  = 95
         *
         * pullback = 5%
         * =========================================================
         */

        if (previous5dHigh > 0 && previous5dLow > 0) {

            f.pullbackDepth5d =
                ((previous5dHigh - previous5dLow)
                    / previous5dHigh) * 100.0;
        }


        /*
         * =========================================================
         * DISTANCE FROM PULLBACK LOW
         *
         * Example:
         *
         * low     = 95
         * current = 98
         *
         * recovery = +3.16%
         * =========================================================
         */

        if (previous5dLow > 0) {

            f.distanceFromPullbackLow =
                ((current.getClose() - previous5dLow)
                    / previous5dLow) * 100.0;

            f.recoveryFromPullback =
                f.distanceFromPullbackLow;
        }


        /*
         * =========================================================
         * COUNT PULLBACK DAYS
         *
         * Count days where close < previous close
         * within the previous 5 trading days.
         * =========================================================
         */

        int pullbackDays = 0;

        for (int i = startIndex + 1; i < currentIndex; i++) {

            double previousClose =
                data.get(i - 1).getClose();

            double close =
                data.get(i).getClose();

            if (close < previousClose) {
                pullbackDays++;
            }
        }

        f.pullbackDays5d = pullbackDays;


        /*
         * =========================================================
         * VOLUME DURING RECOVERY
         *
         * Today's volume /
         * average volume of previous 5 days
         *
         * IMPORTANT:
         * Today's volume is NOT included in denominator.
         * =========================================================
         */

        double totalVolume = 0;

        for (StockData d : previous5) {
            totalVolume += d.getTradedQty();
        }

        double avgPrevious5Volume =
            totalVolume / previous5.size();

        if (avgPrevious5Volume > 0) {

            f.recoveryVolumeRatio =
                current.getTradedQty()
                    / avgPrevious5Volume;
        }


        /*
         * =========================================================
         * PULLBACK DETECTED
         *
         * We want a meaningful but controlled pullback.
         *
         * 1% - 8% pullback
         * AND at least 2 declining days.
         * =========================================================
         */

        f.pullbackDetected =
            f.pullbackDepth5d != null
                && f.pullbackDepth5d >= 1.0
                && f.pullbackDepth5d <= 8.0
                && pullbackDays >= 2;


        /*
         * =========================================================
         * RECOVERY DETECTED
         *
         * Current day must recover from the pullback.
         * =========================================================
         */

        f.recoveryDetected =
            f.pullbackDetected
                && f.recoveryFromPullback != null
                && f.recoveryFromPullback >= 0.5
                && f.recoveryVolumeRatio != null
                && f.recoveryVolumeRatio >= 1.0;
    }


}

