package org.example.analysis;

import org.example.model.CorporateAction;
import org.example.model.StockData;
import org.example.model.StockDailyFeature;
import org.example.repository.CorporateActionRepository;
import org.example.repository.DB;
import org.example.repository.StockFeatureRepository;
import org.example.reader.DataLoader;

import java.time.LocalDate;
import java.util.*;

public class StockFeatureCalculator {

    private final StockFeatureRepository featureRepository;
    private final CorporateActionRepository corporateActionRepository;
    private final String niftySymbol;

    public StockFeatureCalculator(StockFeatureRepository featureRepository,
                                  CorporateActionRepository corporateActionRepository,
                                  String niftySymbol) {

        this.featureRepository = featureRepository;
        this.corporateActionRepository = corporateActionRepository;
        this.niftySymbol = niftySymbol;
    }

    /**
     * Older candles loaded before the first date being calculated, so that
     * EMA200, 52-week high, ADX, MACD etc. have enough warm-up history.
     * EMAs depend on where the series starts; 1000 rows makes that effect
     * negligible, so a one-day run gives the same values as a full rebuild.
     */
    private static final int WARMUP_ROWS = 1000;

    /**
     * Calculates feature rows for:
     *
     * 1. Every (symbol, date) in stock_data from fromDate onward that has
     *    no row in stock_daily_features yet (backfills missed days).
     * 2. Each symbol's latest candle, always (picks up same-day corrections,
     *    e.g. delivery data written later by the loader project).
     *
     * Each row is built only from candles up to its own date, so a
     * backfilled row never sees future data.
     *
     * recalculateAll = true also rebuilds rows that already exist from
     * fromDate onward (use after changing indicator/score logic).
     *
     * toDate = null means "up to the latest candle" (and the latest candle of
     * every symbol is refreshed). A toDate limits the run to fromDate..toDate.
     *
     * Splits/bonuses from corporate_actions are applied point-in-time (see
     * SplitAdjustedSeries). On open-ended runs (toDate = null), any action
     * not yet applied makes that symbol's rows rebuild from its ex-date
     * onward; the action is then marked applied.
     */
    public void calculate(LocalDate fromDate, LocalDate toDate, boolean recalculateAll) throws Exception {

        System.out.println("Finding missing feature dates from " + fromDate + " to " + (toDate == null ? "latest" : toDate) + "...");

        Map<String, Set<LocalDate>> missing = featureRepository.findMissingDates(fromDate);

        // Holidays saved as copies of the previous day: never build features for them.
        Set<LocalDate> duplicateDays = DataLoader.findDuplicateDays(DB.get());

        if (!duplicateDays.isEmpty()) {
            System.out.println("Skipping " + duplicateDays.size() + " duplicated holiday dates: " + duplicateDays);
            missing.values().forEach(dates -> dates.removeAll(duplicateDays));
            missing.values().removeIf(Set::isEmpty);
        }

        if (toDate != null) {
            missing.values().forEach(dates -> dates.removeIf(d -> d.isAfter(toDate)));
            missing.values().removeIf(Set::isEmpty);
        }

        int missingCount = missing.values().stream().mapToInt(Set::size).sum();

        System.out.println("Missing feature rows: " + missingCount + " across " + missing.size() + " symbols");

        Map<String, List<CorporateAction>> actions = corporateActionRepository.loadAll();

        Map<String, List<CorporateAction>> pending = toDate == null
            ? corporateActionRepository.loadUnapplied()
            : Map.of();

        // Per symbol: earliest ex-date whose rows must be rebuilt.
        Map<String, LocalDate> rebuildFrom = new HashMap<>();
        pending.forEach((symbol, list) -> rebuildFrom.put(symbol, list.get(0).exDate()));

        LocalDate loadFrom = rebuildFrom.values().stream()
            .filter(d -> d.isBefore(fromDate))
            .min(Comparator.naturalOrder())
            .orElse(fromDate);

        System.out.println("Corporate actions: " + actions.values().stream().mapToInt(List::size).sum()
            + " total, " + pending.values().stream().mapToInt(List::size).sum() + " new (rebuilding "
            + rebuildFrom.size() + " symbols from their ex-date)");

        Map<String, List<StockData>> allData = DataLoader.loadSinceWithWarmup(DB.get(), loadFrom, WARMUP_ROWS);

        System.out.println("Symbols loaded: " + allData.size());

        List<StockData> niftyData = allData.containsKey(niftySymbol) ? new ArrayList<>(allData.get(niftySymbol)) : new ArrayList<>();

        niftyData.removeIf(c -> duplicateDays.contains(c.getDate()));

        if (niftyData.isEmpty()) {
            System.out.println("WARNING: NIFTY data not found: " + niftySymbol);
        }

        niftyData.sort(Comparator.comparing(StockData::getDate));

        int written = 0;
        int symbolsDone = 0;

        for (Map.Entry<String, List<StockData>> entry : allData.entrySet()) {

            String symbol = entry.getKey();

            List<StockData> candles = new ArrayList<>(entry.getValue());

            candles.removeIf(c -> duplicateDays.contains(c.getDate()));

            if (candles.isEmpty()) {
                continue;
            }

            // Always oldest -> newest.
            candles.sort(Comparator.comparing(StockData::getDate));

            Set<LocalDate> datesToCalculate = new TreeSet<>(missing.getOrDefault(symbol, Collections.emptySet()));

            // Refresh the latest candle (same-day corrections), but not the
            // old last row of a stock that stopped trading before fromDate.
            LocalDate lastDate = candles.get(candles.size() - 1).getDate();
            if (toDate == null && !lastDate.isBefore(fromDate)) {
                datesToCalculate.add(lastDate);
            }

            SplitAdjustedSeries series = new SplitAdjustedSeries(candles, actions.get(symbol));

            LocalDate rebuildDate = rebuildFrom.get(symbol);

            List<StockDailyFeature> batch = new ArrayList<>();

            for (int i = 0; i < candles.size(); i++) {

                LocalDate date = candles.get(i).getDate();

                boolean inRange = !date.isBefore(fromDate) && (toDate == null || !date.isAfter(toDate));

                boolean wanted = recalculateAll
                    ? inRange
                    : datesToCalculate.contains(date) || (rebuildDate != null && !date.isBefore(rebuildDate));

                if (!wanted) {
                    continue;
                }

                List<StockData> history = series.historyUpTo(i);

                List<StockData> niftyHistory = historyUntil(niftyData, date);

                batch.add(buildFeature(symbol, history, niftyHistory));
            }

            // One batch + one commit per symbol.
            featureRepository.upsertBatch(batch);

            written += batch.size();

            symbolsDone++;

            if (batch.size() > 1 || symbolsDone % 250 == 0) {
                System.out.println("[" + symbolsDone + "/" + allData.size() + "] " + symbol + " -> " + batch.size() + " row(s), total " + written);
            }
        }

        List<CorporateAction> applied = pending.values().stream().flatMap(List::stream).toList();

        corporateActionRepository.markApplied(applied);

        System.out.println("Daily feature calculation completed. Rows written: " + written
            + (applied.isEmpty() ? "" : ", corporate actions applied: " + applied.size()));
    }

    private static class SupportResistance {

        double resistancePrice;
        double supportPrice;

        double resistanceStrength;
        double supportStrength;

        SupportResistance(
            double resistancePrice,
            double supportPrice,
            double resistanceStrength,
            double supportStrength) {

            this.resistancePrice = resistancePrice;
            this.supportPrice = supportPrice;

            this.resistanceStrength =
                resistanceStrength;

            this.supportStrength =
                supportStrength;
        }
    }

    private static SupportResistance calculateSupportResistance(
        List<StockData> data) {

        if (data == null || data.size() < 20) {
            return new SupportResistance(0, 0, 0, 0);
        }

        /*
         * data:
         * oldest -> newest
         *
         * IMPORTANT:
         * Current candle is excluded.
         * Therefore these levels are stable for today's trading.
         */

        int end = data.size() - 1;

        double currentPrice =
            data.get(end).getClose();

        if (currentPrice <= 0) {
            return new SupportResistance(0, 0, 0, 0);
        }

        // Use last 120 COMPLETED candles
        int start = Math.max(2, end - 120);

        List<Double> resistanceCandidates = new ArrayList<>();
        List<Double> supportCandidates = new ArrayList<>();

        // =========================================================
        // FIND SWING HIGH / LOW
        // =========================================================

        for (int i = start; i <= end - 2; i++) {

            StockData c = data.get(i);

            boolean swingHigh =
                c.getHigh() > data.get(i - 1).getHigh()
                    && c.getHigh() > data.get(i - 2).getHigh()
                    && c.getHigh() >= data.get(i + 1).getHigh()
                    && c.getHigh() >= data.get(i + 2).getHigh();

            boolean swingLow =
                c.getLow() < data.get(i - 1).getLow()
                    && c.getLow() < data.get(i - 2).getLow()
                    && c.getLow() <= data.get(i + 1).getLow()
                    && c.getLow() <= data.get(i + 2).getLow();

            if (swingHigh) {
                resistanceCandidates.add(c.getHigh());
            }

            if (swingLow) {
                supportCandidates.add(c.getLow());
            }
        }

        // =========================================================
        // FIND BEST RESISTANCE / SUPPORT ZONE
        // =========================================================

        double resistance =
            findStrongResistance(
                data,
                resistanceCandidates,
                currentPrice,
                start,
                end
            );

        double support =
            findStrongSupport(
                data,
                supportCandidates,
                currentPrice,
                start,
                end
            );

        // =========================================================
        // STRENGTH
        // =========================================================

        double resistanceStrength =
            calculateResistanceStrength(
                data,
                resistance,
                start,
                end
            );

        double supportStrength =
            calculateSupportStrength(
                data,
                support,
                start,
                end
            );

        return new SupportResistance(
            resistance,
            support,
            resistanceStrength,
            supportStrength
        );
    }
    private static double findStrongResistance(
        List<StockData> data,
        List<Double> candidates,
        double currentPrice,
        int start,
        int end) {

        if (candidates.isEmpty()) {
            return 0;
        }

        double bestPrice = 0;
        double bestScore = -1;

        for (double level : candidates) {

            // Resistance must be ABOVE current price
            if (level <= currentPrice) {
                continue;
            }

            double distancePercent =
                ((level - currentPrice) / currentPrice) * 100.0;

            // Ignore extremely distant resistance
            if (distancePercent > 15.0) {
                continue;
            }

            double tolerance =
                Math.max(
                    level * 0.0075,
                    calculateAverageRange(data, start, end) * 0.5
                );

            int touches = 0;
            int rejections = 0;

            double score = 0;

            for (int i = start; i <= end; i++) {

                StockData c = data.get(i);

                double high = c.getHigh();
                double close = c.getClose();

                if (Math.abs(high - level) <= tolerance) {

                    touches++;

                    /*
                     * Rejection:
                     * price tested resistance but closed
                     * meaningfully below it.
                     */
                    if (close < level - tolerance * 0.3) {
                        rejections++;
                    }

                    // Recent candles receive more weight
                    double age = end - i;

                    double recency =
                        Math.exp(-age / 60.0);

                    score += 10.0 * recency;

                    if (close < level - tolerance * 0.3) {
                        score += 8.0 * recency;
                    }
                }
            }

            /*
             * Multiple independent touches are important.
             */
            if (touches >= 2) {
                score += 10;
            }

            if (touches >= 3) {
                score += 10;
            }

            if (touches >= 4) {
                score += 10;
            }

            if (rejections >= 2) {
                score += 10;
            }

            /*
             * Prefer resistance that is reasonably close.
             */
            if (distancePercent <= 3) {
                score += 15;
            } else if (distancePercent <= 6) {
                score += 8;
            }

            if (score > bestScore) {
                bestScore = score;
                bestPrice = level;
            }
        }

        return bestPrice;
    }
    private static double findStrongSupport(
        List<StockData> data,
        List<Double> candidates,
        double currentPrice,
        int start,
        int end) {

        if (candidates.isEmpty()) {
            return 0;
        }

        double bestPrice = 0;
        double bestScore = -1;

        for (double level : candidates) {

            // Support must be BELOW current price
            if (level >= currentPrice) {
                continue;
            }

            double distancePercent =
                ((currentPrice - level) / currentPrice) * 100.0;

            if (distancePercent > 15.0) {
                continue;
            }

            double tolerance =
                Math.max(
                    level * 0.0075,
                    calculateAverageRange(data, start, end) * 0.5
                );

            int touches = 0;
            int reactions = 0;

            double score = 0;

            for (int i = start; i <= end; i++) {

                StockData c = data.get(i);

                double low = c.getLow();
                double close = c.getClose();

                if (Math.abs(low - level) <= tolerance) {

                    touches++;

                    /*
                     * Support reaction:
                     * price tested support but closed
                     * meaningfully above it.
                     */
                    if (close > level + tolerance * 0.3) {
                        reactions++;
                    }

                    double age = end - i;

                    double recency =
                        Math.exp(-age / 60.0);

                    score += 10.0 * recency;

                    if (close > level + tolerance * 0.3) {
                        score += 8.0 * recency;
                    }
                }
            }

            if (touches >= 2) {
                score += 10;
            }

            if (touches >= 3) {
                score += 10;
            }

            if (touches >= 4) {
                score += 10;
            }

            if (reactions >= 2) {
                score += 10;
            }

            if (distancePercent <= 3) {
                score += 15;
            } else if (distancePercent <= 6) {
                score += 8;
            }

            if (score > bestScore) {
                bestScore = score;
                bestPrice = level;
            }
        }

        return bestPrice;
    }
    private static double calculateAverageRange(
        List<StockData> data,
        int start,
        int end) {

        int count = 0;
        double total = 0;

        for (int i = start; i <= end; i++) {

            StockData c = data.get(i);

            if (c.getClose() <= 0) {
                continue;
            }

            total +=
                c.getHigh()
                    - c.getLow();

            count++;
        }

        return count > 0
            ? total / count
            : 0;
    }
    private static double calculateResistanceStrength(
        List<StockData> data,
        double level,
        int start,
        int end) {

        if (level <= 0) {
            return 0;
        }

        double tolerance = level * 0.0075;

        int touches = 0;
        int rejections = 0;

        for (int i = start; i <= end; i++) {

            StockData c = data.get(i);

            if (Math.abs(c.getHigh() - level)
                <= tolerance) {

                touches++;

                if (c.getClose()
                    < level - tolerance * 0.3) {

                    rejections++;
                }
            }
        }

        double strength =
            touches * 12.0
                + rejections * 12.0;

        return Math.min(100.0, strength);
    }
    private static double calculateSupportStrength(
        List<StockData> data,
        double level,
        int start,
        int end) {

        if (level <= 0) {
            return 0;
        }

        double tolerance = level * 0.0075;

        int touches = 0;
        int reactions = 0;

        for (int i = start; i <= end; i++) {

            StockData c = data.get(i);

            if (Math.abs(c.getLow() - level)
                <= tolerance) {

                touches++;

                if (c.getClose()
                    > level + tolerance * 0.3) {

                    reactions++;
                }
            }
        }

        double strength =
            touches * 12.0
                + reactions * 12.0;

        return Math.min(100.0, strength);
    }

    private StockDailyFeature buildFeature(String symbol, List<StockData> data, List<StockData> niftyData) {

        StockData current = data.get(data.size() - 1);

        // Number of candles available up to this date. Indicators that need
        // more history than this are stored as null instead of a fake 0/50.
        int n = data.size();

        StockDailyFeature f = new StockDailyFeature();

        f.symbol = symbol;
        f.tradeDate = current.getDate();

        /*
         * =========================================================
         * RAW PRICE
         * =========================================================
         */

        f.open = current.getOpen();
        f.high = current.getHigh();
        f.low = current.getLow();
        f.close = current.getClose();
        f.volume = current.getTradedQty();

        /*
         * =========================================================
         * RETURNS
         * =========================================================
         */

        f.return1d = TechnicalIndicators.calculateReturn(data, 1);

        f.return3d = TechnicalIndicators.calculateReturn(data, 3);

        f.return5d = TechnicalIndicators.calculateReturn(data, 5);

        f.return10d = TechnicalIndicators.calculateReturn(data, 10);

        f.return20d = TechnicalIndicators.calculateReturn(data, 20);

        /*
         * =========================================================
         * SMA
         * =========================================================
         */

        f.sma20 = enough(n, 20, TechnicalIndicators.calculateSMA(data, 20));

        f.sma50 = enough(n, 50, TechnicalIndicators.calculateSMA(data, 50));

        f.sma100 = enough(n, 100, TechnicalIndicators.calculateSMA(data, 100));

        f.sma200 = enough(n, 200, TechnicalIndicators.calculateSMA(data, 200));

        /*
         * =========================================================
         * EMA
         * =========================================================
         */

        f.ema9 = enough(n, 9, TechnicalIndicators.calculateEMA(data, 9));

        f.ema20 = enough(n, 20, TechnicalIndicators.calculateEMA(data, 20));

        f.ema50 = enough(n, 50, TechnicalIndicators.calculateEMA(data, 50));

        f.ema100 = enough(n, 100, TechnicalIndicators.calculateEMA(data, 100));

        f.ema200 = enough(n, 200, TechnicalIndicators.calculateEMA(data, 200));

        /*
         * Trend relationships.
         */
        f.closeAboveEma20 = f.ema20 == null ? null : f.close > f.ema20;

        f.ema20AboveEma50 = greater(f.ema20, f.ema50);

        f.ema50AboveEma200 = greater(f.ema50, f.ema200);

        /*
         * =========================================================
         * RSI
         * =========================================================
         */

        f.rsi14 = enough(n, 15, TechnicalIndicators.calculateRSI(data, 14));

        /*
         * =========================================================
         * MACD
         * =========================================================
         */

        double[] macd = TechnicalIndicators.calculateMACD(data, 12, 26, 9);

        // MACD needs 26 candles for the slow EMA + 9 for the signal line.
        f.macd = enough(n, 35, macd[0]);
        f.macdSignal = enough(n, 35, macd[1]);
        f.macdHistogram = enough(n, 35, macd[2]);

        /*
         * =========================================================
         * ATR
         * =========================================================
         */

        f.atr14 = enough(n, 15, TechnicalIndicators.calculateATR(data, 14));

        if (f.atr14 != null && f.close > 0) {

            f.atrPercent = (f.atr14 / f.close) * 100.0;
        }

        /*
         * =========================================================
         * ADX / DI
         * =========================================================
         */

        double[] adx = TechnicalIndicators.calculateADX(data, 14);

        f.plusDi14 = enough(n, 15, adx[0]);
        f.minusDi14 = enough(n, 15, adx[1]);
        // ADX averages 14 DX values, each needing 14 candles.
        f.adx14 = enough(n, 28, adx[2]);

        /*
         * =========================================================
         * STOCHASTIC
         * =========================================================
         */

        double[] stochastic = TechnicalIndicators.calculateStochastic(data, 14, 3);

        f.stochasticK = enough(n, 14, stochastic[0]);

        f.stochasticD = enough(n, 16, stochastic[1]);

        /*
         * =========================================================
         * CCI
         * =========================================================
         */

        f.cci20 = enough(n, 20, TechnicalIndicators.calculateCCI(data, 20));

        /*
         * =========================================================
         * ROC
         * =========================================================
         */

        f.roc5 = enough(n, 6, TechnicalIndicators.calculateMomentum(data, 5));

        f.roc10 = enough(n, 11, TechnicalIndicators.calculateMomentum(data, 10));

        f.roc20 = enough(n, 21, TechnicalIndicators.calculateMomentum(data, 20));

        /*
         * =========================================================
         * BOLLINGER BANDS
         * =========================================================
         */

        double[] bb = TechnicalIndicators.calculateBollingerBands(data, 20, 2.0);

        f.bbLower = enough(n, 20, bb[0]);
        f.bbMiddle = enough(n, 20, bb[1]);
        f.bbUpper = enough(n, 20, bb[2]);

        if (f.bbMiddle != null && f.bbMiddle > 0) {

            f.bbWidth = ((f.bbUpper - f.bbLower) / f.bbMiddle) * 100.0;
        }

        if (f.bbMiddle != null && !f.bbUpper.equals(f.bbLower)) {

            f.bbPercentB = (f.close - f.bbLower) / (f.bbUpper - f.bbLower);
        }

        /*
         * =========================================================
         * PRICE HIGHS
         * =========================================================
         */

        f.high5d = TechnicalIndicators.highestHigh(data, 5);

        f.high10d = TechnicalIndicators.highestHigh(data, 10);

        f.high20d = TechnicalIndicators.highestHigh(data, 20);

        f.high50d = TechnicalIndicators.highestHigh(data, 50);

        /*
         * =========================================================
         * PRICE LOWS
         * =========================================================
         */

        f.low5d = TechnicalIndicators.lowestLow(data, 5);

        f.low10d = TechnicalIndicators.lowestLow(data, 10);

        f.low20d = TechnicalIndicators.lowestLow(data, 20);

        f.low50d = TechnicalIndicators.lowestLow(data, 50);

        /*
         * =========================================================
         * DISTANCE FROM HIGH
         * =========================================================
         */

        f.distanceFrom5dHigh = percentageFromHigh(f.close, f.high5d);

        f.distanceFrom10dHigh = percentageFromHigh(f.close, f.high10d);

        f.distanceFrom20dHigh = percentageFromHigh(f.close, f.high20d);

        Double high52w = TechnicalIndicators.highestHigh(data, 252);

        f.distanceFrom52wHigh = percentageFromHigh(f.close, high52w);

        /*
         * =========================================================
         * VOLATILITY
         * =========================================================
         */

        if (f.close > 0) {

            f.dailyRangePercent = ((f.high - f.low) / f.close) * 100.0;
        }

        f.avgRange5Percent = enough(n, 5, TechnicalIndicators.calculateAverageRangePercent(data, 5));

        f.avgRange20Percent = enough(n, 20, TechnicalIndicators.calculateAverageRangePercent(data, 20));

        f.volatility5 = enough(n, 6, TechnicalIndicators.calculateReturnVolatility(data, 5));

        f.volatility20 = enough(n, 21, TechnicalIndicators.calculateReturnVolatility(data, 20));
        f.avgVolume5 = enough(n, 5, TechnicalIndicators.calculateAverageVolume(data, 5));

        f.avgVolume10 = enough(n, 10, TechnicalIndicators.calculateAverageVolume(data, 10));

        f.avgVolume20 = enough(n, 20, TechnicalIndicators.calculateAverageVolume(data, 20));

        f.avgVolume50 = enough(n, 50, TechnicalIndicators.calculateAverageVolume(data, 50));
        // Today's volume vs the average of the previous N days (today
        // excluded, otherwise a spike dampens its own ratio; max was 5).
        f.volumeRatio5 = volumeRatio(data, 5);

        f.volumeRatio20 = volumeRatio(data, 20);

        f.volumeRatio50 = volumeRatio(data, 50);

        f.obv = TechnicalIndicators.calculateOBV(data);

        /*
         * =========================================================
         * PRICE STRUCTURE
         * =========================================================
         */

        calculatePriceStructure(data, f);

        TechnicalIndicators.calculatePullbackRecovery(data, f);

        /*
         * =========================================================
         * BREAKOUT / BREAKDOWN
         * =========================================================
         */

        calculateBreakout(data, f);

        /*
         * =========================================================
         * CANDLE PATTERN
         * =========================================================
         */

        /*
         * Add these fields to StockDailyFeature if desired:
         *
         * f.candlePattern =
         *     TechnicalIndicators.detectCandlePattern(data);
         */

        /*
         * =========================================================
         * CONSOLIDATION
         * =========================================================
         */

        // consolidationScore is set in calculateBreakout() from the width of
        // the previous 20-day range (0..100); not overwritten here.

        /*
         * =========================================================
         * NIFTY RELATIVE STRENGTH
         * =========================================================
         */

        calculateRelativeStrength(data, niftyData, f);

        /*
         * =========================================================
         * DELIVERY
         * =========================================================
         */

        /*
         * =========================================================
         * DELIVERY
         * =========================================================
         */

        f.deliveryPercent = current.getDeliveryPercent();

        double deliverySum = 0.0;
        int deliveryCount = 0;

        int start = Math.max(0, data.size() - 6);

        for (int j = start; j < data.size() - 1; j++) {

            double delivery = data.get(j).getDeliveryPercent();

            if (delivery > 0) {
                deliverySum += delivery;
                deliveryCount++;
            }
        }

        if (deliveryCount > 0) {

            f.avgDeliveryPercent5 = deliverySum / deliveryCount;
        }

        if (f.avgDeliveryPercent5 != null && f.avgDeliveryPercent5 > 0 && f.deliveryPercent != null) {

            f.deliveryChange = f.deliveryPercent - f.avgDeliveryPercent5;
        }

        f.deliveryConfirmation = f.deliveryPercent != null && f.avgDeliveryPercent5 != null && f.deliveryPercent >= 50 && f.deliveryPercent > f.avgDeliveryPercent5;



        /*
         * =========================================================
         * NO FINAL BULLISH SCORE YET
         * =========================================================
         *
         * We will create this after the feature table is populated.
         *
         * This is intentional.
         */
        double trendScore = 0.0;
        double momentumScore = 0.0;
        double volumeScore = 0.0;
        double volatilityScore = 0.0;
        double relativeStrengthScore = 0.0;
        double priceStructureScore = 0.0;
        double breakoutScore = 0.0;


        // =========================================================
        // TREND SCORE - 20
        // =========================================================

        if (Boolean.TRUE.equals(f.closeAboveEma20)) {
            trendScore += 5;
        }

        if (Boolean.TRUE.equals(f.ema20AboveEma50)) {
            trendScore += 5;
        }

        if (Boolean.TRUE.equals(greater(f.ema50, f.ema100))) {
            trendScore += 5;
        }

        if (Boolean.TRUE.equals(greater(f.ema100, f.ema200))) {
            trendScore += 5;
        }


        // =========================================================
        // MOMENTUM SCORE - 20
        // =========================================================

        if (f.return1d != null) {

            if (f.return1d >= 2.0) {
                momentumScore += 10;
            } else if (f.return1d >= 1.0) {
                momentumScore += 7;
            } else if (f.return1d > 0) {
                momentumScore += 4;
            }
        }

        if (f.rsi14 != null) {

            if (f.rsi14 >= 50 && f.rsi14 <= 60) {
                momentumScore += 5;
            } else if (f.rsi14 > 60 && f.rsi14 <= 65) {
                momentumScore += 3;
            }
        }

        if (f.macdHistogram != null && f.macdHistogram > 0) {
            momentumScore += 5;
        }


        // =========================================================
        // VOLUME SCORE - 15
        // =========================================================

        if (f.volumeRatio5 != null) {

            if (f.volumeRatio5 >= 2.0) {
                volumeScore += 8;
            } else if (f.volumeRatio5 >= 1.5) {
                volumeScore += 6;
            } else if (f.volumeRatio5 >= 1.2) {
                volumeScore += 4;
            }
        }

        if (f.recoveryVolumeRatio != null) {

            if (f.recoveryVolumeRatio >= 2.0) {
                volumeScore += 7;
            } else if (f.recoveryVolumeRatio >= 1.5) {
                volumeScore += 5;
            } else if (f.recoveryVolumeRatio >= 1.2) {
                volumeScore += 3;
            }
        }


        // =========================================================
        // VOLATILITY SCORE - 10
        // =========================================================

        if (f.atrPercent != null) {

            if (f.atrPercent >= 1.5 && f.atrPercent <= 5.0) {

                volatilityScore += 5;
            }
        }

        if (f.volatility5 != null && f.volatility20 != null && f.volatility5 <= f.volatility20 * 1.5) {

            volatilityScore += 5;
        }


        // =========================================================
        // RELATIVE STRENGTH SCORE - 15
        // =========================================================

        if (f.relativeStrength5d != null) {

            if (f.relativeStrength5d >= 3) {
                relativeStrengthScore += 8;
            } else if (f.relativeStrength5d >= 1) {
                relativeStrengthScore += 6;
            } else if (f.relativeStrength5d > 0) {
                relativeStrengthScore += 3;
            }
        }

        if (f.relativeStrength10d != null && f.relativeStrength10d > 0) {

            relativeStrengthScore += 4;
        }

        if (f.relativeStrength20d != null && f.relativeStrength20d > 0) {

            relativeStrengthScore += 3;
        }


        // =========================================================
        // PRICE STRUCTURE SCORE - 15
        // =========================================================

        if (f.higherLow!=null && f.higherLow) {
            priceStructureScore += 5;
        }

        if (f.higherHigh!=null &&  f.higherHigh) {
            priceStructureScore += 5;
        }

        if (f.pullbackDetected!=null && f.pullbackDetected) {
            priceStructureScore += 3;
        }

        if (f.recoveryDetected!=null &&  f.recoveryDetected) {
            priceStructureScore += 2;
        }


        // =========================================================
        // BREAKOUT SCORE - 10
        // =========================================================

        if (f.distanceToPrevious5dHigh != null) {

            if (f.distanceToPrevious5dHigh >= -2) {
                breakoutScore += 5;
            } else if (f.distanceToPrevious5dHigh >= -4) {
                breakoutScore += 3;
            }
        }

        if (f.breakoutStrength != null && f.breakoutStrength > 0) {

            breakoutScore += 5;
        }


        // =========================================================
        // DELIVERY CONFIRMATION
        // =========================================================

        if (f.deliveryConfirmation) {

            volumeScore += 2;
        }


        // =========================================================
        // STORE SCORES
        // =========================================================

        f.trendScore = trendScore;

        f.momentumScore = momentumScore;

        f.volumeScore = volumeScore;

        f.volatilityScore = volatilityScore;

        f.relativeStrengthScore = relativeStrengthScore;

        f.priceStructureScore = priceStructureScore;

        f.breakoutScore = breakoutScore;


        // =========================================================
        // BULLISH SCORE
        // =========================================================

        f.bullishScore = trendScore + momentumScore + volumeScore + volatilityScore + relativeStrengthScore + priceStructureScore + breakoutScore;

        // =====================================================
        // SUPPORT / RESISTANCE
        // =====================================================

        SupportResistance sr =
            calculateSupportResistance(data);

        double resistancePrice =
            sr.resistancePrice;

        double supportPrice =
            sr.supportPrice;

        double resistanceStrength =
            sr.resistanceStrength;

        double supportStrength =
            sr.supportStrength;

        double currentPrice =
            current.getClose();

        double distanceToResistance = 0.0;

        double distanceToSupport = 0.0;

        if (resistancePrice > 0) {
            distanceToResistance =
                ((resistancePrice - currentPrice)
                    / currentPrice) * 100.0;
        }

        if (supportPrice > 0) {
            distanceToSupport =
                ((currentPrice - supportPrice)
                    / currentPrice) * 100.0;
        }
        f.resistancePrice=resistancePrice;
        f.supportPrice=supportPrice;

        f.resistanceStrength=resistanceStrength;
        f.supportStrength=supportStrength;
        f.distanceToResistance=distanceToResistance;
        f.distanceToSupport=distanceToSupport;
        return f;
    }

    private void calculatePriceStructure(List<StockData> data, StockDailyFeature f) {

        if (data.size() < 2) {
            return;
        }

        StockData current = data.get(data.size() - 1);

        StockData previous = data.get(data.size() - 2);

        f.higherHigh = current.getHigh() > previous.getHigh();

        f.higherLow = current.getLow() > previous.getLow();

        f.lowerHigh = current.getHigh() < previous.getHigh();

        f.lowerLow = current.getLow() < previous.getLow();
    }

    private void calculateBreakout(List<StockData> data, StockDailyFeature f) {

        // Close above the highest high (below the lowest low) of the
        // previous N days. TechnicalIndicators.isPriceBreakout compares with
        // the AVERAGE high, which flagged ~20% of stocks daily.
        f.breakout5d = isBreakout(data, 5);

        f.breakout10d = isBreakout(data, 10);

        f.breakout20d = isBreakout(data, 20);

        f.breakdown5d = isBreakdown(data, 5);

        f.breakdown10d = isBreakdown(data, 10);

        f.breakdown20d = isBreakdown(data, 20);

        /*
         * Use PREVIOUS candles for breakout strength.
         *
         * This prevents today's high from becoming
         * the resistance against which today's close
         * is measured.
         */
        if (data.size() >= 21) {

            List<StockData> previous = data.subList(0, data.size() - 1);

            double resistance = TechnicalIndicators.highestHigh(previous, 20);

            double support = TechnicalIndicators.lowestLow(previous, 20);

            if (resistance > 0) {

                f.breakoutStrength = ((f.close - resistance) / resistance) * 100.0;
            }

            if (support > 0) {

                f.consolidationWidth = ((resistance - support) / support) * 100.0;

                f.consolidationScore = Math.max(0, 100 - f.consolidationWidth * 10);
            }
        }
    }

    private void calculateRelativeStrength(List<StockData> stockData, List<StockData> niftyData, StockDailyFeature f) {

        if (niftyData == null || niftyData.isEmpty()) {
            return;
        }

        /*
         * Nifty must have a candle for the same day. Otherwise (index data
         * stale or missing that day) comparing returns would be wrong, so
         * leave relative strength as null.
         */
        if (!niftyData.get(niftyData.size() - 1).getDate().equals(f.tradeDate)) {
            return;
        }

        f.niftyReturn5d = TechnicalIndicators.calculateReturn(niftyData, 5);

        f.niftyReturn10d = TechnicalIndicators.calculateReturn(niftyData, 10);

        f.niftyReturn20d = TechnicalIndicators.calculateReturn(niftyData, 20);

        if (f.return5d != null && f.niftyReturn5d != null) {
            f.relativeStrength5d = f.return5d - f.niftyReturn5d;
        }

        if (f.return10d != null && f.niftyReturn10d != null) {
            f.relativeStrength10d = f.return10d - f.niftyReturn10d;
        }

        if (f.return20d != null && f.niftyReturn20d != null) {
            f.relativeStrength20d = f.return20d - f.niftyReturn20d;
        }
    }

    private List<StockData> historyUntil(List<StockData> data, LocalDate date) {

        if (data == null || data.isEmpty()) {

            return Collections.emptyList();
        }

        List<StockData> result = new ArrayList<>();

        for (StockData d : data) {

            if (!d.getDate().isAfter(date)) {
                result.add(d);
            }
        }

        return result;
    }

    private static Double volumeRatio(List<StockData> data, int period) {

        if (data.size() < period + 1) {
            return null;
        }

        double sum = 0;
        for (int i = data.size() - 1 - period; i < data.size() - 1; i++) {
            sum += data.get(i).getTradedQty();
        }

        double average = sum / period;

        return average > 0 ? data.get(data.size() - 1).getTradedQty() / average : null;
    }

    private static Boolean isBreakout(List<StockData> data, int period) {

        if (data.size() < period + 1) {
            return null;
        }

        Double previousHigh = TechnicalIndicators.highestHigh(data.subList(0, data.size() - 1), period);

        return data.get(data.size() - 1).getClose() > previousHigh;
    }

    private static Boolean isBreakdown(List<StockData> data, int period) {

        if (data.size() < period + 1) {
            return null;
        }

        Double previousLow = TechnicalIndicators.lowestLow(data.subList(0, data.size() - 1), period);

        return data.get(data.size() - 1).getClose() < previousLow;
    }

    /** value if at least minCandles of history exist, else null. */
    private static Double enough(int candles, int minCandles, double value) {
        return candles >= minCandles ? value : null;
    }

    /** a > b, or null when either side is unknown. */
    private static Boolean greater(Double a, Double b) {
        return a == null || b == null ? null : a > b;
    }

    private static Double ratio(double value, Double average) {
        return average == null || average <= 0 ? null : value / average;
    }

    private Double percentageFromHigh(double close, Double high) {

        if (high == null || high == 0) {

            return null;
        }

        return ((close / high) - 1.0) * 100.0;
    }
}