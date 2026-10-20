package org.example.collector;

import org.example.model.CorporateAction;
import org.example.repository.CorporateActionRepository;
import org.example.repository.DB;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imports splits and bonuses from the corporate-actions CSV exported from
 * nseindia.com (Corporate Filings -> Corporate Actions -> Equity -> Download CSV)
 * into the corporate_actions table.
 *
 * Usage: NseCorporateActionImporter <path-to-csv> [more csv files...]
 *
 * Only SPLIT and BONUS purposes are imported; dividends, rights, buybacks
 * etc. are skipped. Re-importing the same file is safe (upsert).
 * After importing, run StockFeatureMain normally: affected symbols are
 * rebuilt automatically from their ex-date.
 */
public class NseCorporateActionImporter {

    // "Bonus 1:1", "BONUS 4:1"
    private static final Pattern BONUS = Pattern.compile("(?i)bonus\\D*?(\\d+(?:\\.\\d+)?)\\s*:\\s*(\\d+(?:\\.\\d+)?)");

    // Bonus of preference shares / debentures - not an equity bonus.
    private static final Pattern NON_EQUITY_BONUS = Pattern.compile(
        "(?i)bonus\\s+(?:ncrps|crps|ocrps|nccps|ccps|ncd|preference|redeemable|debenture)");

    // "Face Value Split (Sub-Division) - From Rs 10/- Per Share To Rs 2/- Per Share"
    // "Face Value Split From Re 1 To Re 0.50"
    private static final Pattern SPLIT = Pattern.compile(
        "(?i)split.*?(?:rs|re|inr)\\.?\\s*(\\d+(?:\\.\\d+)?).*?(?:rs|re|inr)\\.?\\s*(\\d+(?:\\.\\d+)?)");

    private static final DateTimeFormatter[] DATE_FORMATS = {
        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MMM-yyyy").toFormatter(Locale.ENGLISH),
        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d-MMM-yyyy").toFormatter(Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
    };

    /** Daily window: recent past (late demerger prices) and announced future ex-dates. */
    private static final int FETCH_DAYS_BACK = 30;
    private static final int FETCH_DAYS_AHEAD = 30;

    /**
     * Usage:
     *   NseCorporateActionImporter --fetch           -> download from NSE (last 30 / next 30 days) and import
     *   NseCorporateActionImporter --fetch 2025-01-01 -> download from that date to 30 days ahead
     *   NseCorporateActionImporter file.csv [...]     -> import CSV files downloaded by hand
     */
    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            System.out.println("Usage: NseCorporateActionImporter --fetch [fromDate] | <corporate-actions.csv> [...]");
            return;
        }

        if (args[0].equals("--fetch")) {
            LocalDate from = args.length > 1 ? LocalDate.parse(args[1]) : LocalDate.now().minusDays(FETCH_DAYS_BACK);
            fetchAndImport(from, LocalDate.now().plusDays(FETCH_DAYS_AHEAD));
            return;
        }

        importFiles(Arrays.stream(args).map(Path::of).toList());
    }

    public static void fetchAndImport(LocalDate from, LocalDate to) throws Exception {

        Path csv = NseCorporateActionFetcher.download(from, to);

        try {
            importFiles(List.of(csv));
        } finally {
            Files.deleteIfExists(csv);
        }
    }

    public static void importFiles(List<Path> files) throws Exception {

        List<CorporateAction> actions = new ArrayList<>();

        for (Path file : files) {
            actions.addAll(parse(file));
        }

        PriceBasedCorporateActions priceBased = new PriceBasedCorporateActions(DB.get());

        List<CorporateAction> derived = new ArrayList<>();
        for (Path file : files) {
            derived.addAll(priceBased.demergersAndRights(file));
        }

        // Same symbol + ex-date as a split/bonus: combine factors.
        Map<String, CorporateAction> byKey = new LinkedHashMap<>();
        for (CorporateAction a : actions) {
            byKey.put(a.symbol() + "|" + a.exDate(), a);
        }
        for (CorporateAction a : derived) {
            byKey.merge(a.symbol() + "|" + a.exDate(), a, (x, y) -> new CorporateAction(x.symbol(), x.exDate(),
                x.action() + "+" + y.action(), x.factor() * y.factor(), x.purpose() + " | " + y.purpose()));
        }

        CorporateActionRepository repository = new CorporateActionRepository(DB.get());

        // Actions already saved also explain price drops (a short daily
        // window does not contain older splits).
        List<CorporateAction> known = new ArrayList<>(byKey.values());
        repository.loadAll().values().forEach(known::addAll);

        Set<String> savedKeys = new HashSet<>();
        known.forEach(a -> savedKeys.add(a.symbol() + "|" + a.exDate()));

        List<CorporateAction> inferred = priceBased.inferUnitSplits(known);
        for (CorporateAction a : inferred) {
            // Never overwrite an action that is already saved (e.g. a manual fix).
            if (!savedKeys.contains(a.symbol() + "|" + a.exDate())) {
                byKey.putIfAbsent(a.symbol() + "|" + a.exDate(), a);
            }
        }

        System.out.println("Demerger/rights from prices: " + derived.size());
        derived.forEach(a -> System.out.printf("  %-12s %s %-9s factor %.4f  (%s)%n", a.symbol(), a.exDate(), a.action(), a.factor(), a.purpose()));
        System.out.println("Inferred unit splits: " + inferred.size());
        inferred.forEach(a -> System.out.printf("  %-12s %s factor %.0f  (%s)%n", a.symbol(), a.exDate(), a.factor(), a.purpose()));

        int saved = repository.upsert(byKey.values());

        System.out.println("Saved " + saved + " corporate actions.");
    }

    public static List<CorporateAction> parse(Path csv) throws Exception {

        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);

        if (lines.isEmpty()) {
            return List.of();
        }

        List<String> header = splitCsv(lines.get(0).replace("﻿", ""));

        int symbolCol = column(header, "SYMBOL");
        int purposeCol = column(header, "PURPOSE", "SUBJECT");
        int exDateCol = column(header, "EXDATE");

        // Same symbol + ex-date can appear twice (e.g. "Bonus" and "Split"
        // on separate lines); combine their factors.
        Map<String, CorporateAction> byKey = new LinkedHashMap<>();

        int skipped = 0;

        for (int n = 1; n < lines.size(); n++) {

            if (lines.get(n).isBlank()) {
                continue;
            }

            List<String> row = splitCsv(lines.get(n));

            if (row.size() <= Math.max(symbolCol, Math.max(purposeCol, exDateCol))) {
                continue;
            }

            String symbol = row.get(symbolCol).trim().toUpperCase();
            String purpose = row.get(purposeCol).trim();
            LocalDate exDate = parseDate(row.get(exDateCol).trim());

            double factor = 1.0;
            List<String> kinds = new ArrayList<>();

            Matcher split = SPLIT.matcher(purpose);
            if (split.find()) {
                double from = Double.parseDouble(split.group(1));
                double to = Double.parseDouble(split.group(2));
                if (from > 0 && to > 0 && from != to) {
                    factor *= from / to;
                    kinds.add("SPLIT");
                }
            }

            // "Bonus NCRPS 4:1" = bonus preference shares / debentures: the
            // equity share count does not change, so no price adjustment.
            boolean nonEquityBonus = NON_EQUITY_BONUS.matcher(purpose).find();

            if (nonEquityBonus) {
                System.out.println("Skipped non-equity bonus: " + symbol + " " + row.get(exDateCol) + " | " + purpose);
            }

            Matcher bonus = BONUS.matcher(purpose);
            if (!nonEquityBonus && bonus.find()) {
                double a = Double.parseDouble(bonus.group(1));
                double b = Double.parseDouble(bonus.group(2));
                if (a > 0 && b > 0) {
                    factor *= (a + b) / b;
                    kinds.add("BONUS");
                }
            }

            if (kinds.isEmpty()) {
                if (!nonEquityBonus && (purpose.toLowerCase().contains("split") || purpose.toLowerCase().contains("bonus"))) {
                    System.out.println("WARNING: could not read factor, add manually: " + symbol + " " + row.get(exDateCol) + " | " + purpose);
                }
                skipped++;
                continue;
            }

            if (exDate == null) {
                System.out.println("WARNING: bad ex-date, skipped: " + symbol + " | " + row.get(exDateCol));
                continue;
            }

            String key = symbol + "|" + exDate;

            CorporateAction previous = byKey.get(key);

            if (previous != null && !previous.purpose().equalsIgnoreCase(purpose)) {
                factor *= previous.factor();
                kinds.add(0, previous.action());
                purpose = previous.purpose() + " | " + purpose;
            }

            byKey.put(key, new CorporateAction(symbol, exDate, String.join("+", kinds), factor, purpose));
        }

        System.out.println(csv.getFileName() + ": " + byKey.size() + " split/bonus, " + skipped + " other actions skipped");

        byKey.values().forEach(a -> System.out.printf("  %-12s %s %-12s factor %.4f  (%s)%n",
            a.symbol(), a.exDate(), a.action(), a.factor(), a.purpose()));

        return new ArrayList<>(byKey.values());
    }

    static int column(List<String> header, String... names) {

        for (int i = 0; i < header.size(); i++) {
            String h = header.get(i).toUpperCase().replaceAll("[^A-Z]", "");
            for (String name : names) {
                if (h.equals(name)) {
                    return i;
                }
            }
        }

        throw new IllegalArgumentException("CSV column not found: " + String.join("/", names) + " in " + header);
    }

    static LocalDate parseDate(String text) {

        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(text, f);
            } catch (Exception ignored) {
                // try next format
            }
        }

        return null;
    }

    /** Splits one CSV line, honouring double-quoted fields. */
    static List<String> splitCsv(String line) {

        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < line.length(); i++) {

            char ch = line.charAt(i);

            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }

        out.add(cur.toString());

        return out;
    }
}
