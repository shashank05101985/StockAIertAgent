package org.example;

import org.example.analysis.StockFeatureCalculator;
import org.example.collector.NseCorporateActionImporter;
import org.example.dailyjob.DailyJobTofectchData;
import org.example.reader.DataLoader;
import org.example.repository.CorporateActionRepository;
import org.example.repository.DB;
import org.example.repository.StockFeatureRepository;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public class StockFeatureMain {

    /**
     * Benchmark for relative strength. NIFTYBEES (Nifty 50 ETF) has full daily
     * history since Jan 2025; the "NSE" index rows stop on 17 Aug 2026.
     */
    private static final String NIFTY_BENCHMARK = "NIFTYBEES";

    /**
     * Usage:
     *   StockFeatureMain              -> latest day in stock_data only (daily run)
     *   StockFeatureMain 2026-09-01   -> fill missing days from that date onward
     *   StockFeatureMain 2025-01-01 --all -> rebuild every row from that date,
     *                                        including rows that already exist
     *   StockFeatureMain 2026-08-12 2026-08-12        -> only that date (missing rows)
     *   StockFeatureMain 2026-08-12 2026-08-12 --all  -> rebuild that whole date
     *
     * Without a toDate (daily run), new splits/bonuses/demergers are first
     * downloaded from NSE. Add --no-fetch to skip that step.
     *
     * The daily run (no dates) first loads missing bhavcopy days into stock_data
     * and today's fundamentals (DailyJobTofectchData). Add --no-load to skip that step.
     */
    public static void main(String[] args) throws Exception {

        List<String> dates = Arrays.stream(args).filter(a -> !a.startsWith("--")).toList();

        // Daily run: load missing bhavcopy days + today's fundamentals first,
        // so the features below are calculated on fresh data
        if (dates.isEmpty() && !Arrays.asList(args).contains("--no-load")) {
            try {
                DailyJobTofectchData.run();
            } catch (Exception e) {
                // Features can still be calculated on the data already in stock_data
                System.out.println("WARNING: daily data load failed, continuing: " + e);
            }
        }

        LocalDate fromDate = !dates.isEmpty()
            ? LocalDate.parse(dates.get(0))
            : DataLoader.latestDate(DB.get());

        if (fromDate == null) {
            System.out.println("stock_data is empty, nothing to calculate.");
            return;
        }

        LocalDate toDate = dates.size() > 1 ? LocalDate.parse(dates.get(1)) : null;

        if (toDate == null && !Arrays.asList(args).contains("--no-fetch")) {
            try {
                NseCorporateActionImporter.fetchAndImport(LocalDate.now().minusDays(30), LocalDate.now().plusDays(30));
            } catch (Exception e) {
                // Features are still correct for already-known actions; a
                // missed action is picked up and rebuilt on the next run.
                System.out.println("WARNING: corporate action download failed, continuing: " + e);
            }
        }

        StockFeatureRepository repository = new StockFeatureRepository(DB.get());
        CorporateActionRepository corporateActions = new CorporateActionRepository(DB.get());
        StockFeatureCalculator calculator = new StockFeatureCalculator(repository, corporateActions, NIFTY_BENCHMARK);
        boolean recalculateAll = Arrays.asList(args).contains("--all");

        calculator.calculate(fromDate, toDate, recalculateAll);
    }
}
