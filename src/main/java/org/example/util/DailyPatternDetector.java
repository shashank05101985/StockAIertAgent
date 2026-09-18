package org.example.util;

import org.example.model.StockData;

import java.util.*;
import java.util.stream.Collectors;

public class DailyPatternDetector {

    // =========================================================
    // PATTERN RESULT
    // =========================================================

    public static class PatternResult {

        public String pattern;

        public double confidence;

        public double support;

        public double resistance;

        public double breakoutPrice;

        public double score;

        public double volumeRatio;

        public int patternAge;

        public boolean bullish;

        public boolean bearish;

        public PatternResult(String pattern, double confidence, double support, double resistance, double breakoutPrice, double score, double volumeRatio, int patternAge, boolean bullish, boolean bearish) {

            this.pattern = pattern;
            this.confidence = confidence;
            this.support = support;
            this.resistance = resistance;
            this.breakoutPrice = breakoutPrice;
            this.score = score;
            this.volumeRatio = volumeRatio;
            this.patternAge = patternAge;
            this.bullish = bullish;
            this.bearish = bearish;
        }

        public static PatternResult none() {

            return new PatternResult("NONE", 0, 0, 0, 0, 0, 1, 0, false, false);
        }

        @Override
        public String toString() {

            return String.format(
                "%s | confidence=%.2f | support=%.2f | " + "resistance=%.2f | breakout=%.2f | " + "score=%.1f | volume=%.2fx | age=%d",
                pattern, confidence, support, resistance, breakoutPrice, score, volumeRatio, patternAge);
        }
    }


    // =========================================================
    // SWING POINT
    // =========================================================

    private static class SwingPoint {

        int index;

        double price;

        boolean high;

        SwingPoint(int index, double price, boolean high) {

            this.index = index;
            this.price = price;
            this.high = high;
        }
    }


    // =========================================================
    // MAIN FUNCTION
    // =========================================================

    public static PatternResult findDailyPattern(List<StockData> stockData) {

        if (stockData == null || stockData.size() < 40) {

            return PatternResult.none();
        }

        /*
         * IMPORTANT:
         * oldest -> newest
         */
        List<StockData> sorted = new ArrayList<>(stockData);

        sorted.sort(Comparator.comparing(StockData::getDate));

        /*
         * Last 120 trading days
         */
        List<StockData> candles = sorted;

        if (candles.size() > 120) {

            candles = new ArrayList<>(candles.subList(candles.size() - 120, candles.size()));
        }

        /*
         * Find swing points
         */
        List<SwingPoint> swings = findDailySwings(candles, 2);

        if (swings.size() < 5) {
            return PatternResult.none();
        }

        /*
         * Find ALL candidates.
         *
         * Don't immediately return the first pattern.
         */
        List<PatternResult> candidates = new ArrayList<>();

        addCandidate(candidates, detectDailyHeadAndShoulders(candles, swings));

        addCandidate(candidates, detectDailyInverseHeadAndShoulders(candles, swings));

        addCandidate(candidates, detectDailyDoubleTop(candles, swings));

        addCandidate(candidates, detectDailyDoubleBottom(candles, swings));

        addCandidate(candidates, detectDailyTriangle(candles, swings));

        addCandidate(candidates, detectDailyFlag(candles, swings));

        if (candidates.isEmpty()) {
            return PatternResult.none();
        }

        /*
         * Return strongest pattern
         */
        return candidates.stream().max(Comparator.comparingDouble(p -> p.score)).orElse(PatternResult.none());
    }


    // =========================================================
    // ADD CANDIDATE
    // =========================================================

    private static void addCandidate(List<PatternResult> candidates, PatternResult result) {

        if (result == null) {
            return;
        }

        if ("NONE".equals(result.pattern)) {
            return;
        }

        candidates.add(result);
    }


    // =========================================================
    // SWING DETECTION
    // =========================================================

    private static List<SwingPoint> findDailySwings(List<StockData> candles, int strength) {

        List<SwingPoint> swings = new ArrayList<>();

        for (int i = strength; i < candles.size() - strength; i++) {

            double high = candles.get(i).getHigh();

            double low = candles.get(i).getLow();

            boolean swingHigh = true;

            boolean swingLow = true;

            for (int j = 1; j <= strength; j++) {

                /*
                 * Swing high
                 */
                if (high <= candles.get(i - j).getHigh() || high <= candles.get(i + j).getHigh()) {

                    swingHigh = false;
                }

                /*
                 * Swing low
                 */
                if (low >= candles.get(i - j).getLow() || low >= candles.get(i + j).getLow()) {

                    swingLow = false;
                }
            }

            if (swingHigh) {

                swings.add(new SwingPoint(i, high, true));
            }

            if (swingLow) {

                swings.add(new SwingPoint(i, low, false));
            }
        }

        return swings;
    }


    // =========================================================
    // DOUBLE TOP
    // =========================================================

    private static PatternResult detectDailyDoubleTop(List<StockData> candles, List<SwingPoint> swings) {

        List<SwingPoint> highs = swings.stream().filter(s -> s.high).sorted(
            Comparator.comparingInt(s -> s.index)).collect(Collectors.toList());

        if (highs.size() < 2) {
            return PatternResult.none();
        }

        /*
         * Search recent combinations
         */
        int start = Math.max(0, highs.size() - 5);

        PatternResult best = PatternResult.none();

        for (int a = start; a < highs.size() - 1; a++) {

            for (int b = a + 1; b < highs.size(); b++) {

                SwingPoint h1 = highs.get(a);
                SwingPoint h2 = highs.get(b);

                if (h2.index - h1.index < 8) {
                    continue;
                }

                double avg = (h1.price + h2.price) / 2.0;

                /*
                 * Daily peak tolerance = 1.5%
                 */
                double difference = Math.abs(h1.price - h2.price) / avg;

                if (difference > 0.015) {
                    continue;
                }

                /*
                 * Neckline
                 */
                double neckline = Double.MAX_VALUE;

                for (int i = h1.index; i <= h2.index; i++) {

                    neckline = Math.min(neckline, candles.get(i).getLow());
                }

                double current = getLastClose(candles);

                boolean breakout = current < neckline;

                int age = candles.size() - 1 - h2.index;

                if (age > 20) {
                    continue;
                }

                double volumeRatio = calculateVolumeRatio(candles);

                double confidence = 0.60;

                if (difference <= 0.01) {
                    confidence += 0.10;
                }

                if (breakout) {
                    confidence += 0.15;
                }

                if (volumeRatio >= 1.5) {
                    confidence += 0.10;
                }

                confidence = Math.min(1.0, confidence);

                double score = confidence * 100;

                if (breakout) {
                    score += 10;
                }

                PatternResult result = new PatternResult(breakout ? "DOUBLE_TOP" : "DOUBLE_TOP_FORMING",

                    confidence,

                    neckline,

                    avg,

                    neckline,

                    score,

                    volumeRatio,

                    age,

                    false,

                    true);

                if (result.score > best.score) {
                    best = result;
                }
            }
        }

        return best;
    }


    // =========================================================
    // DOUBLE BOTTOM
    // =========================================================

    private static PatternResult detectDailyDoubleBottom(List<StockData> candles, List<SwingPoint> swings) {

        List<SwingPoint> lows = swings.stream().filter(s -> !s.high).sorted(
            Comparator.comparingInt(s -> s.index)).collect(Collectors.toList());

        if (lows.size() < 2) {
            return PatternResult.none();
        }

        int start = Math.max(0, lows.size() - 5);

        PatternResult best = PatternResult.none();

        for (int a = start; a < lows.size() - 1; a++) {

            for (int b = a + 1; b < lows.size(); b++) {

                SwingPoint l1 = lows.get(a);
                SwingPoint l2 = lows.get(b);

                if (l2.index - l1.index < 8) {
                    continue;
                }

                double avg = (l1.price + l2.price) / 2.0;

                double difference = Math.abs(l1.price - l2.price) / avg;

                if (difference > 0.015) {
                    continue;
                }

                /*
                 * Neckline
                 */
                double neckline = 0;

                for (int i = l1.index; i <= l2.index; i++) {

                    neckline = Math.max(neckline, candles.get(i).getHigh());
                }

                double current = getLastClose(candles);

                boolean breakout = current > neckline;

                int age = candles.size() - 1 - l2.index;

                if (age > 20) {
                    continue;
                }

                double volumeRatio = calculateVolumeRatio(candles);

                double confidence = 0.60;

                if (difference <= 0.01) {
                    confidence += 0.10;
                }

                if (breakout) {
                    confidence += 0.15;
                }

                if (volumeRatio >= 1.5) {
                    confidence += 0.10;
                }

                confidence = Math.min(1.0, confidence);

                double score = confidence * 100;

                if (breakout) {
                    score += 10;
                }

                PatternResult result = new PatternResult(breakout ? "DOUBLE_BOTTOM" : "DOUBLE_BOTTOM_FORMING",

                    confidence,

                    avg,

                    neckline,

                    neckline,

                    score,

                    volumeRatio,

                    age,

                    true,

                    false);

                if (result.score > best.score) {
                    best = result;
                }
            }
        }

        return best;
    }


    // =========================================================
    // HEAD & SHOULDERS
    // =========================================================

    private static PatternResult detectDailyHeadAndShoulders(List<StockData> candles, List<SwingPoint> swings) {

        List<SwingPoint> highs = swings.stream().filter(s -> s.high).sorted(
            Comparator.comparingInt(s -> s.index)).collect(Collectors.toList());

        if (highs.size() < 3) {
            return PatternResult.none();
        }

        int start = Math.max(0, highs.size() - 6);

        PatternResult best = PatternResult.none();

        for (int i = start; i < highs.size() - 2; i++) {

            SwingPoint left = highs.get(i);

            SwingPoint head = highs.get(i + 1);

            SwingPoint right = highs.get(i + 2);

            /*
             * Head must be higher
             */
            if (!(head.price > left.price && head.price > right.price)) {

                continue;
            }

            /*
             * Head depth
             */
            double avgShoulder = (left.price + right.price) / 2.0;

            double headDepth = (head.price - avgShoulder) / avgShoulder;

            /*
             * At least 3%
             */
            if (headDepth < 0.03) {
                continue;
            }

            /*
             * Shoulders similarity
             */
            double shoulderDifference = Math.abs(left.price - right.price) / avgShoulder;

            if (shoulderDifference > 0.04) {
                continue;
            }

            /*
             * Need spacing
             */
            if (head.index - left.index < 5 || right.index - head.index < 5) {

                continue;
            }

            /*
             * Neckline
             */
            double necklineLeft = Double.MAX_VALUE;

            double necklineRight = Double.MAX_VALUE;

            for (int x = left.index; x <= head.index; x++) {

                necklineLeft = Math.min(necklineLeft, candles.get(x).getLow());
            }

            for (int x = head.index; x <= right.index; x++) {

                necklineRight = Math.min(necklineRight, candles.get(x).getLow());
            }

            double neckline = (necklineLeft + necklineRight) / 2.0;

            double current = getLastClose(candles);

            boolean breakout = current < neckline;

            int age = candles.size() - 1 - right.index;

            if (age > 20) {
                continue;
            }

            double volumeRatio = calculateVolumeRatio(candles);

            double confidence = 0.60;

            if (headDepth >= 0.05) {
                confidence += 0.10;
            }

            if (shoulderDifference <= 0.02) {
                confidence += 0.10;
            }

            if (breakout) {
                confidence += 0.10;
            }

            if (volumeRatio >= 1.5) {
                confidence += 0.10;
            }

            confidence = Math.min(1.0, confidence);

            double score = confidence * 100;

            if (breakout) {
                score += 10;
            }

            PatternResult result = new PatternResult(breakout ? "HEAD_AND_SHOULDERS" : "HEAD_AND_SHOULDERS_FORMING",

                confidence,

                neckline,

                head.price,

                neckline,

                score,

                volumeRatio,

                age,

                false,

                true);

            if (result.score > best.score) {
                best = result;
            }
        }

        return best;
    }


    // =========================================================
    // INVERSE HEAD & SHOULDERS
    // =========================================================

    private static PatternResult detectDailyInverseHeadAndShoulders(List<StockData> candles, List<SwingPoint> swings) {

        List<SwingPoint> lows = swings.stream().filter(s -> !s.high).sorted(
            Comparator.comparingInt(s -> s.index)).collect(Collectors.toList());

        if (lows.size() < 3) {
            return PatternResult.none();
        }

        int start = Math.max(0, lows.size() - 6);

        PatternResult best = PatternResult.none();

        for (int i = start; i < lows.size() - 2; i++) {

            SwingPoint left = lows.get(i);

            SwingPoint head = lows.get(i + 1);

            SwingPoint right = lows.get(i + 2);

            /*
             * Correct sequence
             */
            if (!(left.index < head.index && head.index < right.index)) {

                continue;
            }

            /*
             * Head must be lower
             */
            if (!(head.price < left.price && head.price < right.price)) {

                continue;
            }

            /*
             * Head depth
             */
            double avgShoulder = (left.price + right.price) / 2.0;

            double headDepth = (avgShoulder - head.price) / avgShoulder;

            /*
             * Daily minimum 3%
             */
            if (headDepth < 0.03) {
                continue;
            }

            /*
             * Shoulder similarity
             */
            double shoulderDifference = Math.abs(left.price - right.price) / avgShoulder;

            if (shoulderDifference > 0.04) {
                continue;
            }

            /*
             * Enough spacing
             */
            if (head.index - left.index < 5 || right.index - head.index < 5) {

                continue;
            }

            /*
             * Neckline
             */
            double necklineLeft = 0;

            double necklineRight = 0;

            for (int x = left.index; x <= head.index; x++) {

                necklineLeft = Math.max(necklineLeft, candles.get(x).getHigh());
            }

            for (int x = head.index; x <= right.index; x++) {

                necklineRight = Math.max(necklineRight, candles.get(x).getHigh());
            }

            double neckline = (necklineLeft + necklineRight) / 2.0;

            /*
             * Current price
             */
            double current = getLastClose(candles);

            boolean breakout = current > neckline;

            /*
             * Pattern age
             */
            int age = candles.size() - 1 - right.index;

            /*
             * Don't use stale patterns
             */
            if (age > 20) {
                continue;
            }

            /*
             * Volume
             */
            double volumeRatio = calculateVolumeRatio(candles);

            /*
             * Confidence
             */
            double confidence = 0.60;

            if (headDepth >= 0.05) {
                confidence += 0.10;
            }

            if (headDepth >= 0.08) {
                confidence += 0.05;
            }

            if (shoulderDifference <= 0.02) {
                confidence += 0.10;
            }

            if (breakout) {
                confidence += 0.10;
            }

            if (volumeRatio >= 1.5) {
                confidence += 0.10;
            }

            confidence = Math.min(1.0, confidence);

            double score = confidence * 100;

            if (breakout) {
                score += 10;
            }

            PatternResult result = new PatternResult(
                breakout ? "INVERSE_HEAD_AND_SHOULDERS" : "INVERSE_HEAD_AND_SHOULDERS_FORMING",

                confidence,

                head.price,

                neckline,

                neckline,

                score,

                volumeRatio,

                age,

                true,

                false);

            if (result.score > best.score) {
                best = result;
            }
        }

        return best;
    }


    // =========================================================
    // TRIANGLE
    // =========================================================

    private static PatternResult detectDailyTriangle(List<StockData> candles, List<SwingPoint> swings) {

        List<SwingPoint> highs = swings.stream().filter(s -> s.high).sorted(
            Comparator.comparingInt(s -> s.index)).collect(Collectors.toList());

        List<SwingPoint> lows = swings.stream().filter(s -> !s.high).sorted(
            Comparator.comparingInt(s -> s.index)).collect(Collectors.toList());

        if (highs.size() < 3 || lows.size() < 3) {

            return PatternResult.none();
        }

        SwingPoint h1 = highs.get(highs.size() - 3);

        SwingPoint h2 = highs.get(highs.size() - 2);

        SwingPoint h3 = highs.get(highs.size() - 1);

        SwingPoint l1 = lows.get(lows.size() - 3);

        SwingPoint l2 = lows.get(lows.size() - 2);

        SwingPoint l3 = lows.get(lows.size() - 1);

        /*
         * Resistance
         */
        double resistance = (h1.price + h2.price + h3.price) / 3.0;

        /*
         * Support
         */
        double support = (l1.price + l2.price + l3.price) / 3.0;

        /*
         * High slope
         */
        double highSlope = calculateSlope(h1, h3);

        /*
         * Low slope
         */
        double lowSlope = calculateSlope(l1, l3);

        /*
         * Normalize slopes
         */
        double normalizedHighSlope = highSlope / resistance;

        double normalizedLowSlope = lowSlope / support;

        double current = getLastClose(candles);

        /*
         * Resistance flatness
         */
        double resistanceVariation = Math.abs(h3.price - h1.price) / resistance;

        /*
         * Support flatness
         */
        double supportVariation = Math.abs(l3.price - l1.price) / support;

        double volumeRatio = calculateVolumeRatio(candles);

        int age = candles.size() - 1 - Math.max(h3.index, l3.index);

        /*
         * =====================================================
         * ASCENDING TRIANGLE
         * =====================================================
         */

        if (resistanceVariation <= 0.015 && normalizedLowSlope > 0.002) {

            boolean breakout = current > resistance;

            double confidence = 0.70;

            if (resistanceVariation <= 0.01) {
                confidence += 0.10;
            }

            if (breakout) {
                confidence += 0.10;
            }

            if (volumeRatio >= 1.5) {
                confidence += 0.10;
            }

            confidence = Math.min(1.0, confidence);

            return new PatternResult(breakout ? "ASCENDING_TRIANGLE" : "ASCENDING_TRIANGLE_FORMING",

                confidence,

                support,

                resistance,

                resistance,

                confidence * 100,

                volumeRatio,

                age,

                true,

                false);
        }

        /*
         * =====================================================
         * DESCENDING TRIANGLE
         * =====================================================
         */

        if (supportVariation <= 0.015 && normalizedHighSlope < -0.002) {

            boolean breakdown = current < support;

            double confidence = 0.70;

            if (supportVariation <= 0.01) {
                confidence += 0.10;
            }

            if (breakdown) {
                confidence += 0.10;
            }

            if (volumeRatio >= 1.5) {
                confidence += 0.10;
            }

            confidence = Math.min(1.0, confidence);

            return new PatternResult(breakdown ? "DESCENDING_TRIANGLE" : "DESCENDING_TRIANGLE_FORMING",

                confidence,

                support,

                resistance,

                support,

                confidence * 100,

                volumeRatio,

                age,

                false,

                true);
        }

        /*
         * =====================================================
         * SYMMETRIC TRIANGLE
         * =====================================================
         */

        if (normalizedHighSlope < -0.002 && normalizedLowSlope > 0.002) {

            double initialRange = h1.price - l1.price;

            double currentRange = h3.price - l3.price;

            boolean compressed = currentRange < initialRange * 0.75;

            if (compressed) {

                boolean bullishBreak = current > resistance;

                boolean bearishBreak = current < support;

                if (bullishBreak || bearishBreak) {

                    return new PatternResult(bullishBreak ? "SYMMETRIC_TRIANGLE" : "SYMMETRIC_TRIANGLE",

                        0.85,

                        support,

                        resistance,

                        bullishBreak ? resistance : support,

                        85,

                        volumeRatio,

                        age,

                        bullishBreak, bearishBreak);
                }

                return new PatternResult("SYMMETRIC_TRIANGLE_FORMING", 0.75, support, resistance, resistance, 75,
                    volumeRatio, age, true, true);
            }
        }

        return PatternResult.none();
    }


    // =========================================================
    // FLAG
    // =========================================================

    private static PatternResult detectDailyFlag(List<StockData> candles, List<SwingPoint> swings) {

        int n = candles.size();

        if (n < 30) {
            return PatternResult.none();
        }

        /*
         * Last 25 days
         *
         * First 15 = impulse
         * Last 10 = flag
         */
        int impulseStart = n - 25;

        int flagStart = n - 10;

        /*
         * Find impulse high/low
         */
        double impulseHigh = Double.NEGATIVE_INFINITY;

        double impulseLow = Double.POSITIVE_INFINITY;

        for (int i = impulseStart; i < flagStart; i++) {

            impulseHigh = Math.max(impulseHigh, candles.get(i).getHigh());

            impulseLow = Math.min(impulseLow, candles.get(i).getLow());
        }

        double startPrice = candles.get(impulseStart).getClose();

        double impulseMove = (impulseHigh - impulseLow) / startPrice;

        /*
         * Need strong move
         */
        if (impulseMove < 0.08) {
            return PatternResult.none();
        }

        /*
         * Flag range
         */
        double flagHigh = Double.NEGATIVE_INFINITY;

        double flagLow = Double.POSITIVE_INFINITY;

        for (int i = flagStart; i < n; i++) {

            flagHigh = Math.max(flagHigh, candles.get(i).getHigh());

            flagLow = Math.min(flagLow, candles.get(i).getLow());
        }

        double flagRange = (flagHigh - flagLow) / startPrice;

        /*
         * Flag should be smaller than impulse
         */
        if (flagRange > impulseMove * 0.60) {

            return PatternResult.none();
        }

        double current = getLastClose(candles);

        /*
         * Determine impulse direction
         */
        boolean bullishImpulse = impulseHigh > startPrice * 1.04;

        boolean bearishImpulse = impulseLow < startPrice * 0.96;

        double volumeRatio = calculateVolumeRatio(candles);

        /*
         * =====================================================
         * BULL FLAG
         * =====================================================
         */

        if (bullishImpulse) {

            boolean breakout = current > flagHigh;

            int age = 0;

            double confidence = 0.70;

            if (flagRange < impulseMove * 0.40) {

                confidence += 0.10;
            }

            if (breakout) {
                confidence += 0.10;
            }

            if (volumeRatio >= 1.5) {
                confidence += 0.10;
            }

            confidence = Math.min(1.0, confidence);

            return new PatternResult(breakout ? "BULL_FLAG" : "BULL_FLAG_FORMING",

                confidence,

                flagLow,

                flagHigh,

                flagHigh,

                confidence * 100,

                volumeRatio,

                age,

                true, false);
        }

        /*
         * =====================================================
         * BEAR FLAG
         * =====================================================
         */

        if (bearishImpulse) {

            boolean breakdown = current < flagLow;

            double confidence = 0.70;

            if (flagRange < impulseMove * 0.40) {

                confidence += 0.10;
            }

            if (breakdown) {
                confidence += 0.10;
            }

            if (volumeRatio >= 1.5) {
                confidence += 0.10;
            }

            confidence = Math.min(1.0, confidence);

            return new PatternResult(breakdown ? "BEAR_FLAG" : "BEAR_FLAG_FORMING",

                confidence,

                flagLow,

                flagHigh,

                flagLow,

                confidence * 100,

                volumeRatio,

                0,

                false, true);
        }

        return PatternResult.none();
    }


    // =========================================================
    // SLOPE
    // =========================================================

    private static double calculateSlope(SwingPoint first, SwingPoint last) {

        int distance = last.index - first.index;

        if (distance == 0) {
            return 0;
        }

        return (last.price - first.price) / distance;
    }


    // =========================================================
    // VOLUME RATIO
    // =========================================================

    private static double calculateVolumeRatio(List<StockData> candles) {

        int n = candles.size();

        if (n < 21) {
            return 1.0;
        }

        double total = 0;

        for (int i = n - 21; i < n - 1; i++) {

            total += candles.get(i).getTradedQty();
        }

        double average = total / 20.0;

        if (average <= 0) {
            return 1.0;
        }

        double latest = candles.get(n - 1).getTradedQty();

        return latest / average;
    }


    // =========================================================
    // LAST CLOSE
    // =========================================================

    private static double getLastClose(List<StockData> candles) {

        return candles.get(candles.size() - 1).getClose();
    }
}
