package org.example.repository;

import org.example.model.StockDailyFeature;

import java.sql.*;
import java.sql.Date;
import java.util.*;

public class StockFeatureRepository {

    private final Connection connection;

    public StockFeatureRepository(Connection connection) {
        this.connection = connection;
    }

    /**
     * Dates (per symbol) that exist in stock_data from fromDate onward
     * but have no row in stock_daily_features yet.
     */
    public Map<String, Set<java.time.LocalDate>> findMissingDates(java.time.LocalDate fromDate) throws SQLException {

        String sql = """
            SELECT s.symbol, s.date
            FROM stock_data s
            LEFT JOIN stock_daily_features f
                   ON f.symbol = s.symbol
                  AND f.trade_date = s.date
            WHERE s.date >= ?
              AND f.symbol IS NULL
            """;

        Map<String, Set<java.time.LocalDate>> missing = new HashMap<>();

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setDate(1, Date.valueOf(fromDate));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    missing.computeIfAbsent(rs.getString(1), k -> new TreeSet<>())
                        .add(rs.getDate(2).toLocalDate());
                }
            }
        }

        // Plain SELECT opened a transaction (autocommit is off); end it.
        connection.commit();

        return missing;
    }

    /** Built once: the SQL never changes. */
    private static final String UPSERT_SQL = buildUpsertSql();

    private static String buildUpsertSql() {

        String placeholders = String.join(",", Collections.nCopies(120, "?"));

        String sql = """
            INSERT INTO stock_daily_features (
            
                symbol,
                trade_date,
                open_price,
                high_price,
                low_price,
                close_price,
                volume,
            
                delivery_percent,
                avg_delivery_percent5,
                delivery_change,
                delivery_confirmation,
            
                return_1d,
                return_3d,
                return_5d,
                return_10d,
                return_20d,
            
                sma20,
                sma50,
                sma100,
                sma200,
            
                ema9,
                ema20,
                ema50,
                ema100,
                ema200,
            
                close_above_ema20,
                ema20_above_ema50,
                ema50_above_ema200,
            
                rsi14,
            
                macd,
                macd_signal,
                macd_histogram,
            
                tr14,
                atr14,
                atr_percent,
            
                plus_dm14,
                minus_dm14,
                plus_di14,
                minus_di14,
                adx14,
            
                stochastic_k,
                stochastic_d,
            
                cci20,
            
                roc5,
                roc10,
                roc20,
            
                bb_middle,
                bb_upper,
                bb_lower,
                bb_width,
                bb_percent_b,
            
                avg_volume5,
                avg_volume10,
                avg_volume20,
                avg_volume50,
            
                volume_ratio5,
                volume_ratio20,
                volume_ratio50,
            
                obv,
            
                high_5d,
                high_10d,
                high_20d,
                high_50d,
            
                low_5d,
                low_10d,
                low_20d,
                low_50d,
            
                distance_from_5d_high,
                distance_from_10d_high,
                distance_from_20d_high,
                distance_from_52w_high,
            
                previous_5d_high,
                previous_5d_low,
                distance_to_previous5d_high,
                pullback_depth5d,
                distance_from_pullback_low,
                pullback_days5d,
                recovery_from_pullback,
                recovery_volume_ratio,
                pullback_detected,
                recovery_detected,
            
                daily_range_percent,
                avg_range5_percent,
                avg_range20_percent,
            
                volatility5,
                volatility20,
            
                higher_high,
                higher_low,
                lower_high,
                lower_low,
            
                breakout_5d,
                breakout_10d,
                breakout_20d,
            
                breakdown_5d,
                breakdown_10d,
                breakdown_20d,
            
                consolidation_width,
                consolidation_score,
                breakout_strength,
            
                nifty_return_5d,
                nifty_return_10d,
                nifty_return_20d,
            
                relative_strength_5d,
                relative_strength_10d,
                relative_strength_20d,
            
                trend_score,
                momentum_score,
                volume_score,
                volatility_score,
                relative_strength_score,
                price_structure_score,
                breakout_score,
            
                bullish_score,
                market_bias,
                resistance_price,
                support_price,
                resistance_strength,
                support_strength,
                distance_to_resistance,
                distance_to_support
            )
            
            VALUES (
                %s
            )
            
            ON CONFLICT (symbol, trade_date)
            DO UPDATE SET
            
                open_price = EXCLUDED.open_price,
                high_price = EXCLUDED.high_price,
                low_price = EXCLUDED.low_price,
                close_price = EXCLUDED.close_price,
                volume = EXCLUDED.volume,
            
                delivery_percent =
                    EXCLUDED.delivery_percent,
            
                avg_delivery_percent5 =
                    EXCLUDED.avg_delivery_percent5,
            
                delivery_change =
                    EXCLUDED.delivery_change,
            
                delivery_confirmation =
                    EXCLUDED.delivery_confirmation,
            
                return_1d = EXCLUDED.return_1d,
                return_3d = EXCLUDED.return_3d,
                return_5d = EXCLUDED.return_5d,
                return_10d = EXCLUDED.return_10d,
                return_20d = EXCLUDED.return_20d,
            
                sma20 = EXCLUDED.sma20,
                sma50 = EXCLUDED.sma50,
                sma100 = EXCLUDED.sma100,
                sma200 = EXCLUDED.sma200,
            
                ema9 = EXCLUDED.ema9,
                ema20 = EXCLUDED.ema20,
                ema50 = EXCLUDED.ema50,
                ema100 = EXCLUDED.ema100,
                ema200 = EXCLUDED.ema200,
            
                close_above_ema20 =
                    EXCLUDED.close_above_ema20,
            
                ema20_above_ema50 =
                    EXCLUDED.ema20_above_ema50,
            
                ema50_above_ema200 =
                    EXCLUDED.ema50_above_ema200,
            
                rsi14 = EXCLUDED.rsi14,
            
                macd = EXCLUDED.macd,
                macd_signal = EXCLUDED.macd_signal,
                macd_histogram = EXCLUDED.macd_histogram,
            
                tr14 = EXCLUDED.tr14,
                atr14 = EXCLUDED.atr14,
                atr_percent = EXCLUDED.atr_percent,
            
                plus_dm14 = EXCLUDED.plus_dm14,
                minus_dm14 = EXCLUDED.minus_dm14,
                plus_di14 = EXCLUDED.plus_di14,
                minus_di14 = EXCLUDED.minus_di14,
                adx14 = EXCLUDED.adx14,
            
                stochastic_k = EXCLUDED.stochastic_k,
                stochastic_d = EXCLUDED.stochastic_d,
            
                cci20 = EXCLUDED.cci20,
            
                roc5 = EXCLUDED.roc5,
                roc10 = EXCLUDED.roc10,
                roc20 = EXCLUDED.roc20,
            
                bb_middle = EXCLUDED.bb_middle,
                bb_upper = EXCLUDED.bb_upper,
                bb_lower = EXCLUDED.bb_lower,
                bb_width = EXCLUDED.bb_width,
                bb_percent_b = EXCLUDED.bb_percent_b,
            
                avg_volume5 = EXCLUDED.avg_volume5,
                avg_volume10 = EXCLUDED.avg_volume10,
                avg_volume20 = EXCLUDED.avg_volume20,
                avg_volume50 = EXCLUDED.avg_volume50,
            
                volume_ratio5 = EXCLUDED.volume_ratio5,
                volume_ratio20 = EXCLUDED.volume_ratio20,
                volume_ratio50 = EXCLUDED.volume_ratio50,
            
                obv = EXCLUDED.obv,
            
                high_5d = EXCLUDED.high_5d,
                high_10d = EXCLUDED.high_10d,
                high_20d = EXCLUDED.high_20d,
                high_50d = EXCLUDED.high_50d,
            
                low_5d = EXCLUDED.low_5d,
                low_10d = EXCLUDED.low_10d,
                low_20d = EXCLUDED.low_20d,
                low_50d = EXCLUDED.low_50d,
            
                distance_from_5d_high =
                    EXCLUDED.distance_from_5d_high,
            
                distance_from_10d_high =
                    EXCLUDED.distance_from_10d_high,
            
                distance_from_20d_high =
                    EXCLUDED.distance_from_20d_high,
            
                distance_from_52w_high =
                    EXCLUDED.distance_from_52w_high,
            
                previous_5d_high =
                    EXCLUDED.previous_5d_high,
            
                previous_5d_low =
                    EXCLUDED.previous_5d_low,
            
                distance_to_previous5d_high =
                    EXCLUDED.distance_to_previous5d_high,
            
                pullback_depth5d =
                    EXCLUDED.pullback_depth5d,
            
                distance_from_pullback_low =
                    EXCLUDED.distance_from_pullback_low,
            
                pullback_days5d =
                    EXCLUDED.pullback_days5d,
            
                recovery_from_pullback =
                    EXCLUDED.recovery_from_pullback,
            
                recovery_volume_ratio =
                    EXCLUDED.recovery_volume_ratio,
            
                pullback_detected =
                    EXCLUDED.pullback_detected,
            
                recovery_detected =
                    EXCLUDED.recovery_detected,
            
                daily_range_percent =
                    EXCLUDED.daily_range_percent,
            
                avg_range5_percent =
                    EXCLUDED.avg_range5_percent,
            
                avg_range20_percent =
                    EXCLUDED.avg_range20_percent,
            
                volatility5 =
                    EXCLUDED.volatility5,
            
                volatility20 =
                    EXCLUDED.volatility20,
            
                higher_high =
                    EXCLUDED.higher_high,
            
                higher_low =
                    EXCLUDED.higher_low,
            
                lower_high =
                    EXCLUDED.lower_high,
            
                lower_low =
                    EXCLUDED.lower_low,
            
                breakout_5d =
                    EXCLUDED.breakout_5d,
            
                breakout_10d =
                    EXCLUDED.breakout_10d,
            
                breakout_20d =
                    EXCLUDED.breakout_20d,
            
                breakdown_5d =
                    EXCLUDED.breakdown_5d,
            
                breakdown_10d =
                    EXCLUDED.breakdown_10d,
            
                breakdown_20d =
                    EXCLUDED.breakdown_20d,
            
                consolidation_width =
                    EXCLUDED.consolidation_width,
            
                consolidation_score =
                    EXCLUDED.consolidation_score,
            
                breakout_strength =
                    EXCLUDED.breakout_strength,
            
                nifty_return_5d =
                    EXCLUDED.nifty_return_5d,
            
                nifty_return_10d =
                    EXCLUDED.nifty_return_10d,
            
                nifty_return_20d =
                    EXCLUDED.nifty_return_20d,
            
                relative_strength_5d =
                    EXCLUDED.relative_strength_5d,
            
                relative_strength_10d =
                    EXCLUDED.relative_strength_10d,
            
                relative_strength_20d =
                    EXCLUDED.relative_strength_20d,
            
                trend_score =
                    EXCLUDED.trend_score,
            
                momentum_score =
                    EXCLUDED.momentum_score,
            
                volume_score =
                    EXCLUDED.volume_score,
            
                volatility_score =
                    EXCLUDED.volatility_score,
            
                relative_strength_score =
                    EXCLUDED.relative_strength_score,
            
                price_structure_score =
                    EXCLUDED.price_structure_score,
            
                breakout_score =
                    EXCLUDED.breakout_score,
            
                bullish_score =
                    EXCLUDED.bullish_score,
            
                market_bias =
                    EXCLUDED.market_bias,
                    resistance_price =EXCLUDED.resistance_price ,
                support_price = EXCLUDED.support_price ,
                resistance_strength = EXCLUDED.resistance_strength,
                support_strength = EXCLUDED.support_strength,
                distance_to_resistance = EXCLUDED.distance_to_resistance,
                distance_to_support = EXCLUDED.distance_to_support
            """.formatted(placeholders);

        return sql;
    }

    public void upsert(StockDailyFeature f) throws SQLException {
        upsertBatch(List.of(f));
    }

    /**
     * Writes many feature rows in one JDBC batch and one commit.
     * Much faster than one commit per row when backfilling history.
     */
    public void upsertBatch(List<StockDailyFeature> features) throws SQLException {

        if (features.isEmpty()) {
            return;
        }

        boolean autoCommit = connection.getAutoCommit();

        connection.setAutoCommit(false);

        try (PreparedStatement ps = connection.prepareStatement(UPSERT_SQL)) {

            for (StockDailyFeature f : features) {
                bind(ps, f);
                ps.addBatch();
            }

            ps.executeBatch();

            connection.commit();

        } catch (Exception ex) {

            connection.rollback();

            throw ex;

        } finally {

            connection.setAutoCommit(autoCommit);
        }
    }

    private static void bind(PreparedStatement ps, StockDailyFeature f) throws SQLException {

        int i = 1;


        // =====================================================
        // RAW DATA
        // =====================================================

        ps.setString(i++, f.symbol);

        ps.setDate(i++, Date.valueOf(f.tradeDate));

        ps.setDouble(i++, f.open);
        ps.setDouble(i++, f.high);
        ps.setDouble(i++, f.low);
        ps.setDouble(i++, f.close);
        ps.setLong(i++, f.volume);


        // =====================================================
        // DELIVERY
        // =====================================================

        setDouble(ps, i++, f.deliveryPercent);

        setDouble(ps, i++, f.avgDeliveryPercent5);

        setDouble(ps, i++, f.deliveryChange);

        setBoolean(ps, i++, f.deliveryConfirmation);


        // =====================================================
        // RETURNS
        // =====================================================

        setDouble(ps, i++, f.return1d);
        setDouble(ps, i++, f.return3d);
        setDouble(ps, i++, f.return5d);
        setDouble(ps, i++, f.return10d);
        setDouble(ps, i++, f.return20d);


        // =====================================================
        // SMA
        // =====================================================

        setDouble(ps, i++, f.sma20);
        setDouble(ps, i++, f.sma50);
        setDouble(ps, i++, f.sma100);
        setDouble(ps, i++, f.sma200);


        // =====================================================
        // EMA
        // =====================================================

        setDouble(ps, i++, f.ema9);
        setDouble(ps, i++, f.ema20);
        setDouble(ps, i++, f.ema50);
        setDouble(ps, i++, f.ema100);
        setDouble(ps, i++, f.ema200);


        // =====================================================
        // TREND FLAGS
        // =====================================================

        setBoolean(ps, i++, f.closeAboveEma20);

        setBoolean(ps, i++, f.ema20AboveEma50);

        setBoolean(ps, i++, f.ema50AboveEma200);


        // =====================================================
        // RSI
        // =====================================================

        setDouble(ps, i++, f.rsi14);


        // =====================================================
        // MACD
        // =====================================================

        setDouble(ps, i++, f.macd);
        setDouble(ps, i++, f.macdSignal);
        setDouble(ps, i++, f.macdHistogram);


        // =====================================================
        // ATR / ADX
        // =====================================================

        setDouble(ps, i++, f.tr14);
        setDouble(ps, i++, f.atr14);
        setDouble(ps, i++, f.atrPercent);

        setDouble(ps, i++, f.plusDm14);
        setDouble(ps, i++, f.minusDm14);
        setDouble(ps, i++, f.plusDi14);
        setDouble(ps, i++, f.minusDi14);
        setDouble(ps, i++, f.adx14);


        // =====================================================
        // STOCHASTIC
        // =====================================================

        setDouble(ps, i++, f.stochasticK);
        setDouble(ps, i++, f.stochasticD);


        // =====================================================
        // CCI
        // =====================================================

        setDouble(ps, i++, f.cci20);


        // =====================================================
        // ROC
        // =====================================================

        setDouble(ps, i++, f.roc5);
        setDouble(ps, i++, f.roc10);
        setDouble(ps, i++, f.roc20);


        // =====================================================
        // BOLLINGER
        // =====================================================

        setDouble(ps, i++, f.bbMiddle);
        setDouble(ps, i++, f.bbUpper);
        setDouble(ps, i++, f.bbLower);
        setDouble(ps, i++, f.bbWidth);
        setDouble(ps, i++, f.bbPercentB);


        // =====================================================
        // VOLUME
        // =====================================================

        setDouble(ps, i++, f.avgVolume5);
        setDouble(ps, i++, f.avgVolume10);
        setDouble(ps, i++, f.avgVolume20);
        setDouble(ps, i++, f.avgVolume50);

        setDouble(ps, i++, f.volumeRatio5);
        setDouble(ps, i++, f.volumeRatio20);
        setDouble(ps, i++, f.volumeRatio50);

        setDouble(ps, i++, f.obv);


        // =====================================================
        // HIGHS
        // =====================================================

        setDouble(ps, i++, f.high5d);
        setDouble(ps, i++, f.high10d);
        setDouble(ps, i++, f.high20d);
        setDouble(ps, i++, f.high50d);


        // =====================================================
        // LOWS
        // =====================================================

        setDouble(ps, i++, f.low5d);
        setDouble(ps, i++, f.low10d);
        setDouble(ps, i++, f.low20d);
        setDouble(ps, i++, f.low50d);


        // =====================================================
        // DISTANCE FROM HIGH
        // =====================================================

        setDouble(ps, i++, f.distanceFrom5dHigh);

        setDouble(ps, i++, f.distanceFrom10dHigh);

        setDouble(ps, i++, f.distanceFrom20dHigh);

        setDouble(ps, i++, f.distanceFrom52wHigh);


        // =====================================================
        // PULLBACK / RECOVERY
        // =====================================================

        setDouble(ps, i++, f.previous5dHigh);

        setDouble(ps, i++, f.previous5dLow);

        setDouble(ps, i++, f.distanceToPrevious5dHigh);

        setDouble(ps, i++, f.pullbackDepth5d);

        setDouble(ps, i++, f.distanceFromPullbackLow);

        if (f.pullbackDays5d == null) {

            ps.setNull(i++, Types.INTEGER);

        } else {

            ps.setInt(i++, f.pullbackDays5d);
        }

        setDouble(ps, i++, f.recoveryFromPullback);

        setDouble(ps, i++, f.recoveryVolumeRatio);

        setBoolean(ps, i++, f.pullbackDetected);

        setBoolean(ps, i++, f.recoveryDetected);


        // =====================================================
        // RANGE / VOLATILITY
        // =====================================================

        setDouble(ps, i++, f.dailyRangePercent);

        setDouble(ps, i++, f.avgRange5Percent);

        setDouble(ps, i++, f.avgRange20Percent);

        setDouble(ps, i++, f.volatility5);

        setDouble(ps, i++, f.volatility20);


        // =====================================================
        // PRICE STRUCTURE
        // =====================================================

        setBoolean(ps, i++, f.higherHigh);

        setBoolean(ps, i++, f.higherLow);

        setBoolean(ps, i++, f.lowerHigh);

        setBoolean(ps, i++, f.lowerLow);


        // =====================================================
        // BREAKOUT
        // =====================================================

        setBoolean(ps, i++, f.breakout5d);

        setBoolean(ps, i++, f.breakout10d);

        setBoolean(ps, i++, f.breakout20d);


        // =====================================================
        // BREAKDOWN
        // =====================================================

        setBoolean(ps, i++, f.breakdown5d);

        setBoolean(ps, i++, f.breakdown10d);

        setBoolean(ps, i++, f.breakdown20d);


        // =====================================================
        // CONSOLIDATION
        // =====================================================

        setDouble(ps, i++, f.consolidationWidth);

        setDouble(ps, i++, f.consolidationScore);

        setDouble(ps, i++, f.breakoutStrength);


        // =====================================================
        // NIFTY
        // =====================================================

        setDouble(ps, i++, f.niftyReturn5d);

        setDouble(ps, i++, f.niftyReturn10d);

        setDouble(ps, i++, f.niftyReturn20d);


        // =====================================================
        // RELATIVE STRENGTH
        // =====================================================

        setDouble(ps, i++, f.relativeStrength5d);

        setDouble(ps, i++, f.relativeStrength10d);

        setDouble(ps, i++, f.relativeStrength20d);


        // =====================================================
        // SCORES
        // =====================================================

        setDouble(ps, i++, f.trendScore);

        setDouble(ps, i++, f.momentumScore);

        setDouble(ps, i++, f.volumeScore);

        setDouble(ps, i++, f.volatilityScore);

        setDouble(ps, i++, f.relativeStrengthScore);

        setDouble(ps, i++, f.priceStructureScore);

        setDouble(ps, i++, f.breakoutScore);


        // =====================================================
        // FINAL SCORE
        // =====================================================

        setDouble(ps, i++, f.bullishScore);


        // =====================================================
        // MARKET BIAS
        // =====================================================

        if (f.marketBias == null) {

            ps.setNull(i++, Types.VARCHAR);

        } else {

            ps.setString(i++, f.marketBias);
        }

        setDouble(ps,i++,f.resistancePrice);
        setDouble(ps,i++,f.supportPrice);
        setDouble(ps,i++,f.resistanceStrength);
        setDouble(ps,i++,f.supportStrength);
        setDouble(ps,i++,f.distanceToResistance);
        setDouble(ps,i++,f.distanceToSupport);


        // =====================================================
        // VALIDATE PARAMETER COUNT
        // =====================================================

        if (i != 121) {

            throw new IllegalStateException("Expected 120 parameters, but assigned " + (i - 1));
        }
    }

    private static void setDouble(PreparedStatement ps, int index, Double value) throws SQLException {

        if (value == null || value.isNaN() || value.isInfinite()) {
            ps.setNull(index, Types.DOUBLE);
        } else {
            ps.setDouble(index, value);
        }
    }

    private static void setBoolean(PreparedStatement ps, int index, Boolean value) throws SQLException {

        if (value == null) {
            ps.setNull(index, Types.BOOLEAN);
        } else {
            ps.setBoolean(index, value);
        }
    }
}
