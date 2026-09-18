package org.example.analysis;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class GapUpMomentumScanner {

    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

    private static final double MIN_GAP_PERCENT = 2.0;

    /**
     * Call this method every minute.
     */
    public List<GapUpCandidate> scan(Connection connection)
        throws SQLException {

        LocalTime now = LocalTime.now();

        if (now.isBefore(MARKET_OPEN)
            || now.isAfter(MARKET_CLOSE)) {
            return List.of();
        }

        String sql = """
            WITH today_candles AS (
                SELECT
                    mc.symbol,
                    mc.time,
                    mc.open_price,
                    mc.high_price,
                    mc.low_price,
                    mc.close_price,
                    mc.volume
                FROM minute_candle mc
                WHERE mc.time >= CURRENT_DATE + TIME '09:15:00'
                  AND mc.time <= CURRENT_TIMESTAMP
                  AND mc.time < CURRENT_DATE + TIME '15:30:00'
            ),

            opening AS (
                SELECT DISTINCT ON (symbol)
                    symbol,
                    time AS opening_time,
                    open_price AS opening_price
                FROM today_candles
                WHERE time::time = TIME '09:15:00'
                ORDER BY symbol, time
            ),

            previous_close AS (
                SELECT DISTINCT ON (mc.symbol)
                    mc.symbol,
                    mc.close_price AS prev_close,
                    mc.time::date AS prev_trade_date
                FROM minute_candle mc
                WHERE mc.time < CURRENT_DATE + TIME '09:15:00'
                ORDER BY mc.symbol, mc.time DESC
            ),

            current_candle AS (
                SELECT DISTINCT ON (symbol)
                    symbol,
                    time AS current_time,
                    open_price AS current_open,
                    high_price AS current_high,
                    low_price AS current_low,
                    close_price AS current_close,
                    volume AS current_volume
                FROM today_candles
                ORDER BY symbol, time DESC
            ),

            morning_stats AS (
                SELECT
                    symbol,

                    MAX(high_price) AS morning_high,

                    MIN(low_price) AS morning_low,

                    SUM(
                        (
                            (high_price + low_price + close_price) / 3.0
                        ) * volume
                    ) / NULLIF(SUM(volume), 0) AS vwap,

                    AVG(volume) AS avg_volume,

                    COUNT(*) AS candle_count

                FROM today_candles
                GROUP BY symbol
            ),

            previous_volume AS (
                SELECT
                    symbol,
                    AVG(volume) AS avg_previous_volume
                FROM (
                    SELECT
                        symbol,
                        volume,
                        ROW_NUMBER() OVER (
                            PARTITION BY symbol
                            ORDER BY time DESC
                        ) AS rn
                    FROM today_candles
                ) x
                WHERE rn BETWEEN 2 AND 6
                GROUP BY symbol
            )

            SELECT
                c.symbol,
                c.current_time,

                o.opening_price,

                p.prev_close,
                p.prev_trade_date,

                c.current_open,
                c.current_high,
                c.current_low,
                c.current_close,

                c.current_volume,

                s.morning_high,
                s.morning_low,
                s.vwap,

                s.avg_volume,
                pv.avg_previous_volume,

                ROUND(
                    (
                        (
                            (o.opening_price - p.prev_close)
                            / NULLIF(p.prev_close, 0)
                        ) * 100
                    )::numeric,
                    2
                ) AS gap_percent,

                ROUND(
                    (
                        (
                            (c.current_close - o.opening_price)
                            / NULLIF(o.opening_price, 0)
                        ) * 100
                    )::numeric,
                    2
                ) AS move_from_open_percent

            FROM current_candle c

            JOIN opening o
                ON o.symbol = c.symbol

            JOIN previous_close p
                ON p.symbol = c.symbol

            JOIN morning_stats s
                ON s.symbol = c.symbol

            LEFT JOIN previous_volume pv
                ON pv.symbol = c.symbol

            WHERE
                o.opening_price > p.prev_close

                AND
                (
                    (
                        (o.opening_price - p.prev_close)
                        / NULLIF(p.prev_close, 0)
                    ) * 100
                ) >= ?

                AND EXISTS (
                    SELECT 1
                    FROM stock_data sd
                    WHERE sd.symbol = c.symbol
                      AND sd.date::date = p.prev_trade_date
                )

            ORDER BY gap_percent DESC, c.current_volume DESC
            """;

        List<GapUpCandidate> result = new ArrayList<>();

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setDouble(1, MIN_GAP_PERCENT);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    double gap =
                        rs.getDouble("gap_percent");

                    double currentClose =
                        rs.getDouble("current_close");

                    double openingPrice =
                        rs.getDouble("opening_price");

                    double previousClose =
                        rs.getDouble("prev_close");

                    double currentVolume =
                        rs.getDouble("current_volume");

                    double vwap =
                        rs.getDouble("vwap");

                    double morningHigh =
                        rs.getDouble("morning_high");

                    double moveFromOpen =
                        rs.getDouble(
                            "move_from_open_percent"
                        );

                    double avgPreviousVolume =
                        rs.getDouble(
                            "avg_previous_volume"
                        );

                    int score = calculateScore(
                        gap,
                        currentClose,
                        openingPrice,
                        previousClose,
                        currentVolume,
                        avgPreviousVolume,
                        vwap,
                        morningHigh,
                        moveFromOpen
                    );

                    String signal =
                        getSignal(score);

                    Timestamp timestamp =
                        rs.getTimestamp("current_time");

                    result.add(
                        new GapUpCandidate(
                            rs.getString("symbol"),
                            timestamp.toLocalDateTime(),

                            openingPrice,
                            previousClose,
                            currentClose,

                            gap,
                            moveFromOpen,

                            currentVolume,
                            vwap,
                            morningHigh,

                            score,
                            signal
                        )
                    );
                }
            }
        }

        return result;
    }

    private int calculateScore(
        double gap,
        double currentClose,
        double openingPrice,
        double previousClose,
        double currentVolume,
        double avgPreviousVolume,
        double vwap,
        double morningHigh,
        double moveFromOpen
    ) {

        int score = 0;

        /*
         * GAP
         */
        if (gap >= 2.0)
            score += 10;

        if (gap >= 5.0)
            score += 5;

        if (gap >= 10.0)
            score += 5;

        /*
         * CURRENT PRICE VS OPEN
         */
        if (currentClose > openingPrice)
            score += 15;
        else
            score -= 15;

        /*
         * CURRENT PRICE VS PREVIOUS CLOSE
         */
        if (currentClose > previousClose)
            score += 10;
        else
            score -= 10;

        /*
         * VWAP
         */
        if (currentClose > vwap)
            score += 15;
        else
            score -= 15;

        /*
         * MOVE FROM OPEN
         */
        if (moveFromOpen >= 1.0)
            score += 10;

        if (moveFromOpen >= 2.0)
            score += 5;

        /*
         * MORNING HIGH
         */
        if (currentClose >= morningHigh * 0.995)
            score += 10;

        /*
         * RELATIVE VOLUME
         */
        if (avgPreviousVolume > 0) {

            double rvol =
                currentVolume / avgPreviousVolume;

            if (rvol >= 1.5)
                score += 5;

            if (rvol >= 2.0)
                score += 5;

            if (rvol >= 3.0)
                score += 5;
        }

        return Math.max(0, Math.min(score, 100));
    }

    private String getSignal(int score) {

        if (score >= 75)
            return "STRONG_CONTINUATION";

        if (score >= 60)
            return "POTENTIAL_UP";

        if (score >= 45)
            return "WATCH";

        return "WEAK";
    }

    public record GapUpCandidate(
        String symbol,
        LocalDateTime time,

        double openingPrice,
        double previousClose,
        double currentClose,

        double gapPercent,
        double moveFromOpenPercent,

        double currentVolume,
        double vwap,
        double morningHigh,

        int score,
        String signal
    ) {
    }
}