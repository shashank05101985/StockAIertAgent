package org.example;

import org.example.analysis.StockAnalyzer;
import org.example.model.StockData;
import org.example.model.StockSignal;
import org.example.reader.BhavCopyReader;
import org.example.reader.DataLoader;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Stock Analysis Tool
 * Analyzes OHLC data with delivery data to find short-term trading opportunities
 *
 * Usage:
 *   - With bhav copy files: java -jar app.jar /path/to/bhav/directory
 *   - Demo mode: java -jar app.jar demo
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("\n╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║           STOCK ANALYSIS TOOL - Short Term Trading              ║");
        System.out.println("║   Analyzes OHLC + Delivery Data to Find Potential Movers        ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝\n");

        String url = "jdbc:postgresql://localhost:5432/stockdb";
        String user = "shashankmishra";
        String pass = "password";
        StockAnalyzer analyzer = new StockAnalyzer();
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            Map<String, List<StockData>> stockDataMap = DataLoader.loadLastNDays(conn,200);
            System.out.println("Analyzing " + stockDataMap.size() + " stocks...\n");
            List<StockSignal> signals = analyzer.analyzeAllStocks(stockDataMap);

            // Print summary
            analyzer.printSummary(signals);

            // Interactive mode
            interactiveMode(signals, stockDataMap, analyzer);

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Interactive mode for exploring specific stocks
     */
    private static void interactiveMode(List<StockSignal> signals,
                                        Map<String, List<StockData>> stockDataMap,
                                        StockAnalyzer analyzer) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("\n" + "=".repeat(70));
        System.out.println("INTERACTIVE MODE - Enter stock symbol to see detailed analysis");
        System.out.println("Commands: 'list' = show all symbols, 'top N' = show top N stocks, 'exit' = quit");
        System.out.println("=".repeat(70));

        while (true) {
            System.out.print("\nEnter symbol or command: ");
            String input = scanner.nextLine().trim().toUpperCase();

            if (input.isEmpty()) {
                continue;
            }

            if (input.equals("EXIT") || input.equals("QUIT") || input.equals("Q")) {
                System.out.println("Thank you for using Stock Analysis Tool!");
                break;
            }

            if (input.equals("LIST")) {
                System.out.println("\nAvailable symbols:");
                stockDataMap.keySet().stream().sorted().forEach(s -> System.out.print(s + " "));
                System.out.println();
                continue;
            }

            if (input.startsWith("TOP")) {
                try {
                    int n = Integer.parseInt(input.replace("TOP", "").trim());
                    System.out.println("\n--- Top " + n + " Stocks ---");
                    signals.stream().limit(n).forEach(System.out::println);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid format. Use: top 5");
                }
                continue;
            }

            // Look up specific stock
            StockSignal found = signals.stream()
                .filter(s -> s.getSymbol().equals(input))
                .findFirst()
                .orElse(null);

            if (found != null) {
                System.out.println(found);

                // Show additional data
                List<StockData> data = stockDataMap.get(input);
                if (data != null && !data.isEmpty()) {
                    System.out.println("\nRecent Price History:");
                    System.out.printf("%-12s %10s %10s %10s %10s %12s %10s\n",
                        "Date", "Open", "High", "Low", "Close", "Volume", "Del%");
                    System.out.println("-".repeat(76));

                    int start = Math.max(0, data.size() - 5);
                    for (int i = start; i < data.size(); i++) {
                        StockData d = data.get(i);
                        System.out.printf("%-12s %10.2f %10.2f %10.2f %10.2f %,12d %9.1f%%\n",
                            d.getDate(), d.getOpen(), d.getHigh(), d.getLow(),
                            d.getClose(), d.getTradedQty(), d.getDeliveryPercent());
                    }
                }
            } else if (stockDataMap.containsKey(input)) {
                // Stock exists but might not have enough data or was filtered out
                List<StockData> data = stockDataMap.get(input);
                StockSignal signal = analyzer.analyzeStock(input, data);
                if (signal != null) {
                    System.out.println(signal);
                } else {
                    System.out.println("Insufficient data for analysis (need at least 20 days)");
                }
            } else {
                System.out.println("Symbol not found: " + input);
            }
        }

        scanner.close();
    }
}