package org.example;

import org.example.model.BreakoutCandidate;
import org.example.model.StockData;
import org.example.reader.DataLoader;
import org.example.repository.DB;
import org.example.util.DailyPatternDetector;

import java.sql.SQLException;
import java.util.*;

public class PatternData {
    public static void main(String[] args) throws SQLException {

        Map<String, List<StockData>> allData = DataLoader.loadLastNDays(DB.get(), 450);

        List<BreakoutCandidate> readyStocks = new ArrayList<>();

        for (Map.Entry<String, List<StockData>> entry : allData.entrySet()) {

            String symbol = entry.getKey();

            List<StockData> data = entry.getValue();

            DailyPatternDetector.PatternResult pattern = DailyPatternDetector.findDailyPattern(data);

            if (pattern == null) {
                continue;
            }

            BreakoutCandidate candidate = assessBreakoutCandidate(symbol, data, pattern);

            if (candidate != null) {
                readyStocks.add(candidate);
            }
        }

        // Highest score first
        readyStocks.sort(Comparator.comparingDouble((BreakoutCandidate c) -> c.totalScore).reversed());

        int maxCandidates = Math.min(20, readyStocks.size());

        List<BreakoutCandidate> topCandidates = readyStocks.subList(0, maxCandidates);
        System.out.println();
        System.out.println("==============================================================");
        System.out.println("              TOP BREAKOUT CANDIDATES");
        System.out.println("==============================================================");

        for (BreakoutCandidate c : topCandidates) {

            System.out.printf(
                "%-12s %-32s " + "score=%5.1f " + "close=%8.2f " + "support=%8.2f " + "resistance=%8.2f " + "dist=%5.2f%% " + "vol=%5.2fx " + "conf=%.2f " + "T3=%.2f " + "T4=%.2f " + "%s%n",

                c.symbol, c.pattern, c.totalScore, c.close, c.support, c.resistance, c.distanceToResistancePct,
                c.volumeRatio, c.confidence, c.target3, c.target4, c.assessment);
        }
    }

    public static BreakoutCandidate assessBreakoutCandidate(String symbol, List<StockData> data, DailyPatternDetector.PatternResult p) {

        // =====================================================
        // BASIC VALIDATION
        // =====================================================

        if (data == null || data.isEmpty() || p == null) {
            return null;
        }

        if ("NONE".equals(p.pattern)) {
            return null;
        }

        StockData latest = data.get(data.size() - 1);

        double close = latest.getClose();

        if (close <= 0) {
            return null;
        }

        // =====================================================
        // SUPPORT / RESISTANCE VALIDATION
        // =====================================================

        if (p.support <= 0 || p.resistance <= 0) {
            return null;
        }

        if (p.support >= p.resistance) {

            return null;
        }

        // =====================================================
        // BULLISH PATTERN ONLY
        // =====================================================

        if (!isBullishPattern(p.pattern)) {

            return null;
        }

        // =====================================================
        // CONFIDENCE
        // =====================================================

        if (p.confidence < 0.60) {
            return null;
        }

        // =====================================================
        // MUST STILL BE BELOW RESISTANCE
        // =====================================================

        if (close >= p.resistance) {

            return null;
        }

        // =====================================================
        // DISTANCE TO RESISTANCE
        // =====================================================

        double distanceToResistancePct = ((p.resistance - close) / close) * 100.0;

        // Don't consider stocks too far away
        if (distanceToResistancePct > 5.0) {
            return null;
        }

        // =====================================================
        // TARGET CALCULATION
        // =====================================================

        double target3 = p.resistance * 1.03;

        double target4 = p.resistance * 1.04;

        double upsideToTarget3 = ((target3 - close) / close) * 100.0;

        double upsideToTarget4 = ((target4 - close) / close) * 100.0;

        // =====================================================
        // 1. PATTERN SCORE - MAX 30
        // =====================================================

        double patternScore = p.confidence * 20.0;

        if (p.pattern.contains("ASCENDING_TRIANGLE")) {

            patternScore += 10;

        } else if (p.pattern.contains("BULL_FLAG")) {

            patternScore += 9;

        } else if (p.pattern.contains("INVERSE_HEAD")) {

            patternScore += 8;

        } else if (p.pattern.contains("DOUBLE_BOTTOM")) {

            patternScore += 7;

        } else if (p.pattern.contains("SYMMETRIC_TRIANGLE")) {

            patternScore += 6;
        }

        patternScore = Math.min(patternScore, 30.0);

        // =====================================================
        // 2. PROXIMITY SCORE - MAX 20
        // =====================================================

        double proximityScore;

        if (distanceToResistancePct <= 0.25) {

            proximityScore = 20;

        } else if (distanceToResistancePct <= 0.50) {

            proximityScore = 18;

        } else if (distanceToResistancePct <= 0.75) {

            proximityScore = 16;

        } else if (distanceToResistancePct <= 1.00) {

            proximityScore = 14;

        } else if (distanceToResistancePct <= 1.50) {

            proximityScore = 11;

        } else if (distanceToResistancePct <= 2.00) {

            proximityScore = 8;

        } else if (distanceToResistancePct <= 3.00) {

            proximityScore = 5;

        } else {

            proximityScore = 2;
        }

        // =====================================================
        // 3. VOLUME SCORE - MAX 20
        // =====================================================

        double volumeScore;

        if (p.volumeRatio >= 5.0) {

            volumeScore = 20;

        } else if (p.volumeRatio >= 3.0) {

            volumeScore = 18;

        } else if (p.volumeRatio >= 2.0) {

            volumeScore = 15;

        } else if (p.volumeRatio >= 1.5) {

            volumeScore = 12;

        } else if (p.volumeRatio >= 1.0) {

            volumeScore = 8;

        } else {

            volumeScore = 3;
        }

        // =====================================================
        // 4. SUPPORT SCORE - MAX 10
        // =====================================================

        double supportDistancePct = ((close - p.support) / close) * 100.0;

        double supportScore;

        if (supportDistancePct >= 1.0 && supportDistancePct <= 5.0) {

            supportScore = 10;

        } else if (supportDistancePct > 5.0 && supportDistancePct <= 10.0) {

            supportScore = 7;

        } else if (supportDistancePct < 1.0) {

            supportScore = 4;

        } else {

            supportScore = 2;
        }

        // =====================================================
        // 5. TARGET SPACE SCORE - MAX 10
        // =====================================================

        double targetSpaceScore;

        if (upsideToTarget4 >= 4.0) {

            targetSpaceScore = 10;

        } else if (upsideToTarget3 >= 3.0) {

            targetSpaceScore = 8;

        } else if (upsideToTarget3 >= 2.5) {

            targetSpaceScore = 5;

        } else {

            targetSpaceScore = 2;
        }

        // =====================================================
        // TOTAL SCORE
        // =====================================================

        double totalScore = patternScore + proximityScore + volumeScore + supportScore + targetSpaceScore;

        // =====================================================
        // ASSESSMENT
        // =====================================================

        String assessment;

        if (totalScore >= 85) {

            assessment = "EXCELLENT";

        } else if (totalScore >= 75) {

            assessment = "STRONG";

        } else if (totalScore >= 65) {

            assessment = "GOOD";

        } else if (totalScore >= 55) {

            assessment = "WATCH";

        } else {

            assessment = "WEAK";
        }


        // =====================================================
        // RETURN
        // =====================================================

        return new BreakoutCandidate(symbol, p, close, distanceToResistancePct, patternScore, 0.0,
            // resistanceScore
            supportScore, proximityScore, volumeScore, targetSpaceScore, totalScore, target3, target4, assessment);
    }

    private static boolean isBullishPattern(String pattern) {

        return pattern != null && (pattern.contains("ASCENDING_TRIANGLE") || pattern.contains(
            "BULL_FLAG") || pattern.contains("INVERSE_HEAD") || pattern.contains("DOUBLE_BOTTOM") || pattern.contains(
            "SYMMETRIC_TRIANGLE"));
    }

    private static boolean isReadyForAscendingTriangle(DailyPatternDetector.PatternResult p, double currentClose) {

        if (p == null) {
            return false;
        }

        if (!"ASCENDING_TRIANGLE_FORMING".equals(p.pattern) && !"ASCENDING_TRIANGLE_NEAR_BREAKOUT".equals(p.pattern)) {

            return false;
        }

        // Structure must be valid
        if (p.support >= p.resistance) {
            return false;
        }

        // Confidence
        if (p.confidence < 0.80) {
            return false;
        }

        // Distance from resistance
        double distance = (p.resistance - currentClose) / currentClose;

        /*
         * Price must be below resistance,
         * but not too far away.
         */
        if (distance < 0 || distance > 0.02) {
            return false;
        }

        return true;
    }

    private static double calculateTradedQtyRatio(List<StockData> candles) {

        int n = candles.size();

        if (n < 21) {
            return 1.0;
        }

        long totalQty = 0;

        int count = 0;

        /*
         * Previous 20 trading days
         * Exclude today's quantity from average
         */
        for (int i = n - 21; i < n - 1; i++) {

            totalQty += candles.get(i).getTradedQty();

            count++;
        }

        if (count == 0) {
            return 1.0;
        }

        double averageQty = (double) totalQty / count;

        if (averageQty <= 0) {
            return 1.0;
        }

        long latestQty = candles.get(n - 1).getTradedQty();

        return latestQty / averageQty;
    }


    public static class PatternStock {

        public String symbol;

        public DailyPatternDetector.PatternResult pattern;

        public PatternStock(String symbol, DailyPatternDetector.PatternResult pattern) {

            this.symbol = symbol;
            this.pattern = pattern;
        }
    }


}
