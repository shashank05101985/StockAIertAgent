package org.example.reader;

import org.example.model.StockData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class DataLoader {

    public static Map<String, List<StockData>> loadLastNDays(
            Connection connection,
            int numberOfDays) throws SQLException {

        String sql = """
        SELECT
            symbol,
            date,
            open_price,
            high_price,
            low_price,
            close_price,
            traded_qty,
            delivery_qty,
            delivery_percent,
            number_of_trades,
            turnover,
            series
        FROM (
            SELECT
                s.*,
                ROW_NUMBER() OVER (
                    PARTITION BY symbol
                    ORDER BY date DESC
                ) AS rn
            FROM stock_data s
        ) x
        WHERE rn <= ?
        ORDER BY symbol, date ASC
        """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, numberOfDays);

            return readRows(ps);
        }
    }

    /**
     * Loads every candle from fromDate onward, plus warmupRows older candles
     * per symbol so indicators (EMA200, 52w high...) have enough history
     * for the earliest dates.
     */
    public static Map<String, List<StockData>> loadSinceWithWarmup(
            Connection connection,
            LocalDate fromDate,
            int warmupRows) throws SQLException {

        String sql = """
        SELECT
            symbol,
            date,
            open_price,
            high_price,
            low_price,
            close_price,
            traded_qty,
            delivery_qty,
            delivery_percent,
            number_of_trades,
            turnover,
            series
        FROM (
            SELECT
                s.*,
                ROW_NUMBER() OVER (
                    PARTITION BY symbol
                    ORDER BY date DESC
                ) AS rn,
                COUNT(*) FILTER (WHERE date >= ?) OVER (
                    PARTITION BY symbol
                ) AS recent_rows
            FROM stock_data s
        ) x
        WHERE rn <= recent_rows + ?
        ORDER BY symbol, date ASC
        """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setDate(1, java.sql.Date.valueOf(fromDate));
            ps.setInt(2, warmupRows);

            return readRows(ps);
        }
    }

    /**
     * Market holidays that the bhavcopy loader saved anyway, as a copy of the
     * previous trading day: dates where more than 90% of symbols have exactly
     * the same OHLC and volume as the previous date. Feature calculation skips
     * these dates. stock_data itself is not changed.
     */
    public static Set<LocalDate> findDuplicateDays(Connection connection) throws SQLException {

        String sql = """
            WITH days AS (
                SELECT date, LAG(date) OVER (ORDER BY date) AS previous_date
                FROM (SELECT DISTINCT date FROM stock_data) d
            )
            SELECT days.date
            FROM days
            JOIN stock_data a ON a.date = days.date
            JOIN stock_data b ON b.symbol = a.symbol AND b.date = days.previous_date
            GROUP BY days.date
            HAVING COUNT(*) FILTER (
                       WHERE a.open_price = b.open_price
                         AND a.high_price = b.high_price
                         AND a.low_price = b.low_price
                         AND a.close_price = b.close_price
                         AND a.traded_qty = b.traded_qty
                   ) > 0.9 * COUNT(*)
            ORDER BY days.date
            """;

        Set<LocalDate> days = new TreeSet<>();

        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                days.add(rs.getDate(1).toLocalDate());
            }
        }

        connection.commit();

        return days;
    }

    /** Most recent date in stock_data, or null if the table is empty. */
    public static LocalDate latestDate(Connection connection) throws SQLException {

        LocalDate latest = null;

        try (PreparedStatement ps = connection.prepareStatement("SELECT MAX(date) FROM stock_data");
             ResultSet rs = ps.executeQuery()) {

            if (rs.next() && rs.getDate(1) != null) {
                latest = rs.getDate(1).toLocalDate();
            }
        }

        connection.commit();

        return latest;
    }

    private static Map<String, List<StockData>> readRows(PreparedStatement ps) throws SQLException {

        Map<String, List<StockData>> stockDataMap = new HashMap<>();

        try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    String symbol = rs.getString("symbol");

                    StockData data = new StockData();

                    data.setSymbol(symbol);
                    data.setDate(
                            rs.getDate("date").toLocalDate()
                    );

                    data.setOpen(rs.getDouble("open_price"));
                    data.setHigh(rs.getDouble("high_price"));
                    data.setLow(rs.getDouble("low_price"));
                    data.setClose(rs.getDouble("close_price"));

                    data.setTradedQty(rs.getLong("traded_qty"));
                    data.setDeliveredQty(rs.getLong("delivery_qty"));

                    data.setDeliveryPercent(
                            rs.getDouble("delivery_percent")
                    );

                    data.setTurnover(
                            rs.getDouble("turnover")
                    );

                    stockDataMap
                            .computeIfAbsent(symbol, k -> new ArrayList<>())
                            .add(data);
                }
        }

        return stockDataMap;
    }
}
