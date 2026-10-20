package org.example.dailyjob;

import org.example.repository.DB;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/** Upserts bhavcopy rows into stock_data on (symbol, date). */
public class StockDataDao {

    private static final String SQL =
            "INSERT INTO stock_data " +
                    "(symbol, date, open_price, high_price, low_price, close_price, " +
                    " traded_qty, delivery_qty, delivery_percent, series) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (symbol, date) DO UPDATE SET " +
                    "open_price = EXCLUDED.open_price, " +
                    "high_price = EXCLUDED.high_price, " +
                    "low_price = EXCLUDED.low_price, " +
                    "close_price = EXCLUDED.close_price, " +
                    "traded_qty = EXCLUDED.traded_qty, " +
                    "delivery_qty = EXCLUDED.delivery_qty, " +
                    "delivery_percent = EXCLUDED.delivery_percent, " +
                    "series = EXCLUDED.series";

    private static final int BATCH_SIZE = 500;

    public static void insertBatch(List<BhavcopyRecord> list) throws Exception {

        if (list == null || list.isEmpty()) {
            return;
        }

        long start = System.currentTimeMillis();

        // DB.get() hands out one shared connection, so it must not be closed here
        Connection conn = DB.get();

        boolean oldAutoCommit = conn.getAutoCommit();

        conn.setAutoCommit(false);

        int count = 0;

        try (PreparedStatement ps = conn.prepareStatement(SQL)) {

            for (BhavcopyRecord s : list) {

                if (s == null || s.date == null || s.symbol == null) {
                    continue;
                }

                ps.setString(1, s.symbol);
                ps.setDate(2, Date.valueOf(s.date));
                ps.setDouble(3, s.openPrice);
                ps.setDouble(4, s.highPrice);
                ps.setDouble(5, s.lowPrice);
                ps.setDouble(6, s.closePrice);
                ps.setLong(7, s.tradedQty);
                ps.setLong(8, s.deliveryQty);
                ps.setDouble(9, s.deliveryPercent);
                ps.setString(10, s.series);

                ps.addBatch();

                count++;

                if (count % BATCH_SIZE == 0) {
                    ps.executeBatch();
                }
            }

            ps.executeBatch();

            // One commit per day, so a failure never leaves half a day saved
            conn.commit();

        } catch (SQLException e) {

            conn.rollback();

            throw e;

        } finally {

            conn.setAutoCommit(oldAutoCommit);
        }

        System.out.println("Inserted records: " + count + " in " + (System.currentTimeMillis() - start) + " ms");
    }
}
