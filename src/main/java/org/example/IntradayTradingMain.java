package org.example;

import org.example.analysis.IntradayAnalyzer;
import org.example.analysis.StockAnalyzer;
import org.example.model.*;
import org.example.reader.BhavCopyReader;
import org.example.reader.IntradayDataReader;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Combined Daily + Intraday Analysis Workflow
 *
 * WORKFLOW:
 * ---------
 * 1. PRE-MARKET (8:30 AM - 9:15 AM):
 *    - Run daily analyzer on bhav copy data
 *    - Get watchlist of potential stocks (Strong Buy, Buy signals)
 *
 * 2. MARKET HOURS (9:15 AM - 3:30 PM):
 *    - Feed intraday data for watchlist stocks only
 *    - Get precise entry signals with tight stop-loss
 *    - Monitor for exit signals
 */
public class IntradayTradingMain {

    public static void main(String[] args) {
        System.out.println("\n╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║       COMBINED DAILY + INTRADAY TRADING SYSTEM                  ║");
        System.out.println("║   Daily Analysis → Stock Selection → Intraday Entry/Exit        ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝\n");

        try {
            // ================================================
            // PHASE 1: PRE-MARKET - Daily Analysis
            // ================================================
            System.out.println("=" .repeat(70));
            System.out.println("PHASE 1: PRE-MARKET ANALYSIS (Daily Data)");
            System.out.println("=".repeat(70));

            BhavCopyReader dailyReader = new BhavCopyReader();
            StockAnalyzer dailyAnalyzer = new StockAnalyzer();

            // Load daily data (use sample for demo)
            Map<String, List<StockData>> dailyData;
            if (args.length > 0 && !args[0].equalsIgnoreCase("demo")) {
                dailyData = dailyReader.readMultipleBhavCopies(args[0]);
            } else {
                System.out.println("Running in DEMO mode with sample data...\n");
                dailyData = dailyReader.createSampleData();
            }

            // Analyze all stocks
            List<StockSignal> dailySignals = dailyAnalyzer.analyzeAllStocks(dailyData);

            // Get watchlist: Only Strong Buy and Buy signals
            List<StockSignal> watchlist = dailySignals.stream()
                .filter(s -> s.getSignalType() == StockSignal.SignalType.STRONG_BUY ||
                            s.getSignalType() == StockSignal.SignalType.BUY ||
                            s.getSignalType() == StockSignal.SignalType.WEAK_BUY)
                .limit(10)  // Top 10 stocks
                .collect(Collectors.toList());

            System.out.println("\n📋 WATCHLIST FOR TODAY (From Daily Analysis):");
            System.out.println("-".repeat(70));

            if (watchlist.isEmpty()) {
                System.out.println("No stocks meeting buy criteria. Expand criteria or check data.");
                return;
            }

            for (StockSignal signal : watchlist) {
                System.out.printf("%-12s | Score: %3d | Signal: %-20s | Del: %.1f%%\n",
                    signal.getSymbol(),
                    signal.getScore(),
                    signal.getSignalType().getDescription(),
                    signal.getDeliveryPercent());
            }

            // Extract previous day closes for watchlist
            Map<String, Double> prevCloses = new HashMap<>();
            for (StockSignal signal : watchlist) {
                prevCloses.put(signal.getSymbol(), signal.getCurrentPrice());
            }

            // ================================================
            // PHASE 2: MARKET HOURS - Intraday Analysis
            // ================================================
            System.out.println("\n" + "=".repeat(70));
            System.out.println("PHASE 2: MARKET HOURS - INTRADAY ANALYSIS");
            System.out.println("=".repeat(70));

            IntradayAnalyzer intradayAnalyzer = new IntradayAnalyzer();
            IntradayDataReader intradayReader = new IntradayDataReader();

            // Set previous day closes
            intradayAnalyzer.setPreviousDayClose(prevCloses);

            // Load intraday data (sample for demo)
            List<String> watchlistSymbols = watchlist.stream()
                .map(StockSignal::getSymbol)
                .collect(Collectors.toList());

            Map<String, List<IntradayCandle>> intradayData;
            if (args.length > 1) {
                intradayData = intradayReader.readMultipleFiles(args[1]);
                // Filter to watchlist only
                intradayData.keySet().retainAll(watchlistSymbols);
            } else {
                intradayData = intradayReader.createSampleData(watchlistSymbols);
            }

            System.out.println("\nProcessing intraday data for watchlist stocks...\n");

            // Process intraday candles
            for (String symbol : intradayData.keySet()) {
                List<IntradayCandle> candles = intradayData.get(symbol);

                System.out.printf("Processing %s: %d candles\n", symbol, candles.size());

                // Process each candle (simulating real-time feed)
                for (IntradayCandle candle : candles) {
                    IntradaySignal signal = intradayAnalyzer.processCandle(candle);

                    // Print only actionable signals
                    if (signal != null &&
                        (signal.getSignalType() == IntradaySignal.SignalType.STRONG_BUY ||
                         signal.getSignalType() == IntradaySignal.SignalType.BUY)) {

                        System.out.println("\n🔔 ENTRY SIGNAL DETECTED!");
                        System.out.println(signal);
                    }
                }
            }

            // Print final summary
            intradayAnalyzer.printIntradaySummary();

            // ================================================
            // Example: Live Trading Simulation
            // ================================================
            System.out.println("\n" + "=".repeat(70));
            System.out.println("CURRENT ENTRY OPPORTUNITIES:");
            System.out.println("=".repeat(70));

            List<IntradaySignal> buySignals = intradayAnalyzer.getActiveBuySignals();

            if (buySignals.isEmpty()) {
                System.out.println("\nNo active buy signals currently. Wait for setup.");
            } else {
                System.out.println("\n📈 READY TO TRADE:");
                for (IntradaySignal signal : buySignals) {
                    System.out.println(signal);
                    System.out.println("\n  ACTION: Place buy order at ₹" + signal.getEntryPrice());
                    System.out.println("  STOP LOSS: ₹" + signal.getStopLoss());
                    System.out.println("  TARGETS: T1=₹" + signal.getTarget1() +
                                     " T2=₹" + signal.getTarget2() +
                                     " T3=₹" + signal.getTarget3());
                }
            }

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get trading instructions for a signal
     */
    public static String getTradingInstructions(IntradaySignal signal) {
        StringBuilder sb = new StringBuilder();

        switch (signal.getSignalType()) {
            case STRONG_BUY:
                sb.append("🟢 STRONG BUY - Enter immediately\n");
                sb.append("   Entry: ₹").append(signal.getEntryPrice()).append("\n");
                sb.append("   Stop Loss: ₹").append(signal.getStopLoss()).append("\n");
                sb.append("   Target 1: ₹").append(signal.getTarget1()).append(" (Book 50%)\n");
                sb.append("   Target 2: ₹").append(signal.getTarget2()).append(" (Book 30%)\n");
                sb.append("   Target 3: ₹").append(signal.getTarget3()).append(" (Trail rest)\n");
                break;

            case BUY:
                sb.append("🟡 BUY - Good entry opportunity\n");
                sb.append("   Entry: ₹").append(signal.getEntryPrice()).append("\n");
                sb.append("   Stop Loss: ₹").append(signal.getStopLoss()).append("\n");
                sb.append("   Target 1: ₹").append(signal.getTarget1()).append("\n");
                break;

            case WAIT_PULLBACK:
                sb.append("⏳ WAIT - Enter on pullback to VWAP\n");
                sb.append("   Wait for price near: ₹").append(signal.getVwap()).append("\n");
                sb.append("   Then enter with SL: ₹").append(signal.getStopLoss()).append("\n");
                break;

            case EXIT:
                sb.append("🔴 EXIT - Close position now\n");
                sb.append("   Current: ₹").append(signal.getCurrentPrice()).append("\n");
                break;

            case STOP_HIT:
                sb.append("⛔ STOP HIT - Exit immediately\n");
                sb.append("   Stop was: ₹").append(signal.getStopLoss()).append("\n");
                break;

            default:
                sb.append("⏸️ HOLD - No action needed\n");
        }

        return sb.toString();
    }
}

