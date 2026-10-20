package org.example.analysis;

import org.example.model.CorporateAction;
import org.example.model.StockData;

import java.time.LocalDate;
import java.util.*;

/**
 * Point-in-time split/bonus adjustment of one symbol's raw candles.
 *
 * historyUpTo(i) returns candles 0..i adjusted only for actions whose
 * ex-date is on or before candle i's date - i.e. exactly what an investor
 * knew that day. So:
 *
 *   - a row before a split keeps real (raw) prices,
 *   - a row on/after the split sees older candles divided by the factor,
 *     so returns, EMAs, highs etc. have no fake crash,
 *   - the stored close is always the real traded close of that day.
 *
 * Volumes before the ex-date are multiplied by the factor so volume
 * averages stay comparable. Delivery % and turnover are unchanged.
 */
public class SplitAdjustedSeries {

    private final List<StockData> raw;
    private final List<CorporateAction> actions;

    /** Adjusted copies, keyed by how many actions are in effect. */
    private final Map<Integer, List<StockData>> cache = new HashMap<>();

    public SplitAdjustedSeries(List<StockData> rawSortedOldestFirst, List<CorporateAction> actions) {
        this.raw = rawSortedOldestFirst;
        this.actions = actions == null ? List.of() : actions.stream()
            .sorted(Comparator.comparing(CorporateAction::exDate))
            .toList();
    }

    public List<StockData> historyUpTo(int index) {

        LocalDate date = raw.get(index).getDate();

        int effective = 0;
        while (effective < actions.size() && !actions.get(effective).exDate().isAfter(date)) {
            effective++;
        }

        return cache.computeIfAbsent(effective, this::adjust).subList(0, index + 1);
    }

    private List<StockData> adjust(int effectiveActions) {

        if (effectiveActions == 0) {
            return raw;
        }

        List<CorporateAction> inEffect = actions.subList(0, effectiveActions);

        List<StockData> adjusted = new ArrayList<>(raw.size());

        for (StockData c : raw) {

            // Product of factors of every in-effect action after this candle.
            double factor = 1.0;
            for (CorporateAction a : inEffect) {
                if (c.getDate().isBefore(a.exDate())) {
                    factor *= a.factor();
                }
            }

            adjusted.add(factor == 1.0 ? c : scaled(c, factor));
        }

        return adjusted;
    }

    private static StockData scaled(StockData c, double factor) {

        StockData s = new StockData();

        s.setSymbol(c.getSymbol());
        s.setDate(c.getDate());

        s.setOpen(c.getOpen() / factor);
        s.setHigh(c.getHigh() / factor);
        s.setLow(c.getLow() / factor);
        s.setClose(c.getClose() / factor);
        s.setPreviousClose(c.getPreviousClose() / factor);

        s.setTradedQty(Math.round(c.getTradedQty() * factor));
        s.setDeliveredQty(Math.round(c.getDeliveredQty() * factor));

        s.setDeliveryPercent(c.getDeliveryPercent());
        s.setTurnover(c.getTurnover());

        return s;
    }
}
