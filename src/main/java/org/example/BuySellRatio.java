package org.example;

import org.example.analysis.TelegramAlertService;
import org.example.repository.DB;

import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BuySellRatio {
    public static void main(String[] args) throws SQLException {
        startBuySellRatioJob(DB.get());

    }


    public static void startBuySellRatioJob(Connection conn) {

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        ZoneId zone = ZoneId.of("Asia/Kolkata");

        LocalDateTime now = LocalDateTime.now(zone).withSecond(0).withNano(0);

        // Start with previous minute
        LocalDateTime runTime = now.minusMinutes(1);

        scheduler.scheduleAtFixedRate(new Runnable() {

            private LocalDateTime currentRunTime = runTime;

            @Override
            public void run() {

                try {

                    Timestamp candleTime = Timestamp.valueOf(currentRunTime);

                    runBuySellRatioJob(conn, candleTime);

                    System.out.println("Buy/Sell job executed for: " + candleTime);

                    // Next minute
                    currentRunTime = currentRunTime.plusMinutes(1);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    public static void runBuySellRatioJob(Connection conn, Timestamp time) {

        String sql = """
            SELECT
                time,
                close_price,
                volume,
                total_buy_qty,
                total_sell_qty,
                buy_sell_ratio,
                symbol
            FROM minute_candle
            WHERE buy_sell_ratio > 1.2
              AND total_buy_qty > 100000
              AND close_price > 50
              AND time = ?
            ORDER BY volume desc, buy_sell_ratio DESC
            LIMIT 10
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, time);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    String symbol = rs.getString("symbol");

                    double currentRatio = rs.getDouble("buy_sell_ratio");

                    double currentClose = rs.getDouble("close_price");

                    long currentVolume = rs.getLong("volume");

                    long currentBuyQty = rs.getLong("total_buy_qty");

                    long currentSellQty = rs.getLong("total_sell_qty");

                    Timestamp currentTime = rs.getTimestamp("time");


                    BuySellSnapshot current = new BuySellSnapshot(currentRatio, currentClose, currentVolume,
                        currentBuyQty, currentSellQty, currentTime);


                    BuySellSnapshot previous = previousData.get(symbol);


                    if (previous != null) {

                        if (current.ratio > previous.ratio) {

                            double ratioIncrease = ((current.ratio - previous.ratio) / previous.ratio) * 100.0;


                            System.out.printf(
                                "🚨 ALERT %s | %s | " + "Ratio %.2f -> %.2f (+%.2f%%) | " + "Buy %d -> %d | " + "Sell %d -> %d | " + "Close %.2f -> %.2f%n",

                                symbol, current.time,

                                previous.ratio, current.ratio, ratioIncrease,

                                previous.buyQty, current.buyQty,

                                previous.sellQty, current.sellQty,

                                previous.close, current.close);

                            /*TelegramAlertService.send(String.format(
                                "🚨 ALERT %s | %s\n" + "Ratio %.2f → %.2f (+%.2f%%)\n" + "Buy %d → %d\n" + "Sell %d → %d\n" + "Close %.2f → %.2f",

                                symbol, current.time,

                                previous.ratio, current.ratio, ratioIncrease,

                                previous.buyQty, current.buyQty,

                                previous.sellQty, current.sellQty,

                                previous.close, current.close));*/
                        }
                    }


                    // Store current snapshot
                    previousData.put(symbol, current);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static final Map<String, BuySellSnapshot> previousData = new ConcurrentHashMap<>();

    static class BuySellSnapshot {

        double ratio;
        double close;
        long volume;
        long buyQty;
        long sellQty;
        Timestamp time;

        BuySellSnapshot(double ratio, double close, long volume, long buyQty, long sellQty, Timestamp time) {

            this.ratio = ratio;
            this.close = close;
            this.volume = volume;
            this.buyQty = buyQty;
            this.sellQty = sellQty;
            this.time = time;
        }
    }
}
