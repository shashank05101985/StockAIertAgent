package org.example.repository;

import org.example.model.PotentialStock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PotentialStockRepoistory {

    private final Connection connection;
    public PotentialStockRepoistory(Connection connection) {
        this.connection = connection;
    }

    public List<PotentialStock> findPotentialStocks(LocalDate tradeDate)
        throws SQLException {

        String sql = """
        WITH candidates AS (
            SELECT
                f.*,

                -- ATR %
                (f.atr14 / NULLIF(f.close_price, 0)) * 100 AS atr_pct,

                (
                    -- TREND QUALITY: 30
                    CASE WHEN f.close_price > f.ema20 THEN 6 ELSE 0 END +
                    CASE WHEN f.ema20 > f.ema50 THEN 6 ELSE 0 END +
                    CASE WHEN f.ema50 > f.ema200 THEN 6 ELSE 0 END +
                    CASE WHEN f.adx14 >= 25 THEN 6 ELSE 0 END +
                    CASE WHEN f.plus_di14 > f.minus_di14 THEN 6 ELSE 0 END +

                    -- MOMENTUM: 25
                    CASE WHEN f.return_5d >= 3 THEN 5 ELSE 0 END +
                    CASE WHEN f.return_10d >= 5 THEN 5 ELSE 0 END +
                    CASE WHEN f.return_20d > 0 THEN 5 ELSE 0 END +
                    CASE WHEN f.macd > f.macd_signal THEN 5 ELSE 0 END +
                    CASE WHEN f.macd_histogram > 0 THEN 5 ELSE 0 END +

                    -- RSI QUALITY: 10
                    CASE
                        WHEN f.rsi14 BETWEEN 55 AND 72 THEN 10
                        ELSE 0
                    END +

                    -- PRICE STRUCTURE: 15
                    CASE WHEN f.higher_high THEN 5 ELSE 0 END +
                    CASE WHEN f.higher_low THEN 5 ELSE 0 END +
                    CASE WHEN NOT f.lower_low THEN 5 ELSE 0 END +

                    -- BREAKOUT QUALITY: 10
                    CASE
                        WHEN f.breakout_strength >= 3 THEN 10
                        WHEN f.breakout_strength >= 2 THEN 8
                        WHEN f.breakout_strength >= 1.5 THEN 6
                        WHEN f.breakout_strength > 0 THEN 3
                        ELSE 0
                    END +

                    -- ATR / MOVEMENT POTENTIAL: 10
                    CASE
                        WHEN (f.atr14 / NULLIF(f.close_price, 0)) * 100
                             BETWEEN 2.0 AND 4.0
                            THEN 10

                        WHEN (f.atr14 / NULLIF(f.close_price, 0)) * 100
                             BETWEEN 4.0 AND 5.0
                            THEN 8

                        WHEN (f.atr14 / NULLIF(f.close_price, 0)) * 100
                             BETWEEN 1.5 AND 2.0
                            THEN 5

                        ELSE 0
                    END

                ) AS potential_score

            FROM stock_daily_features f

            WHERE f.trade_date = ?

              AND f.close_price > 50

              -- VALID INDICATORS
              AND f.ema20 > 0
              AND f.ema50 > 0
              AND f.ema200 > 0
              AND f.atr14 > 0
              AND f.rsi14 > 0
              AND f.adx14 > 0

              -- BULLISH TREND
              AND f.close_price > f.ema20
              AND f.ema20 > f.ema50
              AND f.ema50 > f.ema200

              -- TREND STRENGTH
              AND f.adx14 >= 20
              AND f.plus_di14 > f.minus_di14

              -- MOMENTUM
              AND f.return_5d > 2
              AND f.return_10d > 2
              AND f.return_20d > 0

              -- RSI
              AND f.rsi14 BETWEEN 55 AND 72

              -- MACD
              AND f.macd > f.macd_signal
              AND f.macd_histogram > 0

              -- PRICE STRUCTURE
              AND f.higher_high = TRUE
              AND f.higher_low = TRUE
              AND f.lower_low = FALSE

              -- AT LEAST ONE BREAKOUT
              AND (
                     f.breakout_5d = TRUE
                  OR f.breakout_10d = TRUE
                  OR f.breakout_20d = TRUE
              )

              -- POSITIVE BREAKOUT
              AND f.breakout_strength > 0
        )

        SELECT
            symbol,
            trade_date,
            close_price,

            potential_score,

            atr14,
            atr_pct,

            return_5d,
            return_10d,
            return_20d,

            rsi14,
            adx14,

            ema20,
            ema50,
            ema200,

            macd_histogram,

            breakout_5d,
            breakout_10d,
            breakout_20d,
            breakout_strength,

            higher_high,
            higher_low

        FROM candidates

        WHERE potential_score >= 70

        ORDER BY
            breakout_strength DESC,
            potential_score DESC,
            atr_pct DESC,
            return_5d DESC

        LIMIT 20
        """;

        List<PotentialStock> result = new ArrayList<>();
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setObject(1, tradeDate);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    PotentialStock stock = new PotentialStock();

                    stock.setSymbol(rs.getString("symbol"));
                    stock.setTradeDate(
                        rs.getObject("trade_date", LocalDate.class)
                    );

                    stock.setClosePrice(
                        rs.getDouble("close_price")
                    );

                    stock.setPotentialScore(
                        rs.getDouble("potential_score")
                    );

                    stock.setAtr14(
                        rs.getDouble("atr14")
                    );

                    stock.setAtrPct(
                        rs.getDouble("atr_pct")
                    );

                    stock.setReturn5d(
                        rs.getDouble("return_5d")
                    );

                    stock.setReturn10d(
                        rs.getDouble("return_10d")
                    );

                    stock.setReturn20d(
                        rs.getDouble("return_20d")
                    );

                    stock.setRsi14(
                        rs.getDouble("rsi14")
                    );

                    stock.setAdx14(
                        rs.getDouble("adx14")
                    );

                    stock.setEma20(
                        rs.getDouble("ema20")
                    );

                    stock.setEma50(
                        rs.getDouble("ema50")
                    );

                    stock.setEma200(
                        rs.getDouble("ema200")
                    );

                    stock.setMacdHistogram(
                        rs.getDouble("macd_histogram")
                    );

                    stock.setBreakout5d(
                        rs.getBoolean("breakout_5d")
                    );

                    stock.setBreakout10d(
                        rs.getBoolean("breakout_10d")
                    );

                    stock.setBreakout20d(
                        rs.getBoolean("breakout_20d")
                    );

                    stock.setBreakoutStrength(
                        rs.getDouble("breakout_strength")
                    );

                    stock.setHigherHigh(
                        rs.getBoolean("higher_high")
                    );

                    stock.setHigherLow(
                        rs.getBoolean("higher_low")
                    );

                    result.add(stock);
                    connection.commit();
                }
            }
        }

        return result;
    }
}
