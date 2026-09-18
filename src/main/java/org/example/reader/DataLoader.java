package org.example.reader;

import org.example.model.StockData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        Map<String, List<StockData>> stockDataMap = new HashMap<>();

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, numberOfDays);

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
        }

        return stockDataMap;
    }
}
