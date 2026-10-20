package org.example.collector;

import org.example.model.CorporateAction;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Corporate actions whose adjustment factor is not written in the NSE
 * purpose text, so it is derived from prices in stock_data (read only):
 *
 *   DEMERGER : factor = last close before ex-date / open on ex-date.
 *              NSE's special pre-open session on the ex-date discovers the
 *              post-demerger price, so this removes the value that moved to
 *              the new company while keeping the ex-day's own move.
 *
 *   RIGHTS   : factor = close before ex-date / theoretical ex-rights price
 *              TERP = (held * close + new * issuePrice) / (held + new)
 *              issuePrice = face value + premium.
 *
 *   inferred : ETF unit splits are not in NSE's equity corporate-actions
 *              list. A drop between two consecutive trading days that is
 *              within 7% of a round ratio (2, 3, 4, 5, 10, 20, 25, 50, 100)
 *              and has no known action is treated as a split.
 */
public class PriceBasedCorporateActions {

    // "Rights 8:1 @ Premium Rs 0/-", "Rights 1:5 @ Premium Rs 245/-"
    private static final Pattern RIGHTS = Pattern.compile(
        "(?i)rights\\D*?(\\d+(?:\\.\\d+)?)\\s*:\\s*(\\d+(?:\\.\\d+)?)(?:.*?premium\\s*(?:rs|re|inr)?\\.?\\s*(\\d+(?:\\.\\d+)?))?");

    private static final double[] ROUND_RATIOS = {2, 3, 4, 5, 10, 20, 25, 50, 100};

    private final Connection connection;

    public PriceBasedCorporateActions(Connection connection) {
        this.connection = connection;
    }

    public List<CorporateAction> demergersAndRights(Path csv) throws Exception {

        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);

        if (lines.isEmpty()) {
            return List.of();
        }

        List<String> header = NseCorporateActionImporter.splitCsv(lines.get(0).replace("﻿", ""));

        int symbolCol = NseCorporateActionImporter.column(header, "SYMBOL");
        int purposeCol = NseCorporateActionImporter.column(header, "PURPOSE", "SUBJECT");
        int exDateCol = NseCorporateActionImporter.column(header, "EXDATE");
        int faceValueCol = optionalColumn(header, "FACEVALUE", "FACEVAL");

        List<CorporateAction> result = new ArrayList<>();

        for (int n = 1; n < lines.size(); n++) {

            List<String> row = NseCorporateActionImporter.splitCsv(lines.get(n));

            if (row.size() <= Math.max(symbolCol, Math.max(purposeCol, exDateCol))) {
                continue;
            }

            String symbol = row.get(symbolCol).trim().toUpperCase();
            String purpose = row.get(purposeCol).trim();
            LocalDate exDate = NseCorporateActionImporter.parseDate(row.get(exDateCol).trim());

            if (exDate == null) {
                continue;
            }

            String lower = purpose.toLowerCase();

            if (lower.contains("demerger")) {

                Double before = closeBefore(symbol, exDate);
                Double exOpen = openOnOrAfter(symbol, exDate);

                if (before == null || exOpen == null || exOpen <= 0) {
                    continue;
                }

                double factor = before / exOpen;

                // Below 2% the demerged part is tiny vs normal noise.
                if (factor > 1.02) {
                    result.add(new CorporateAction(symbol, exDate, "DEMERGER", factor,
                        purpose + String.format(" (factor from prices: close %.2f -> ex-date open %.2f)", before, exOpen)));
                }

            } else if (lower.contains("rights")) {

                Matcher m = RIGHTS.matcher(purpose);

                if (!m.find()) {
                    continue;
                }

                double newShares = Double.parseDouble(m.group(1));
                double heldShares = Double.parseDouble(m.group(2));
                double premium = m.group(3) == null ? 0 : Double.parseDouble(m.group(3));
                double faceValue = faceValueCol >= 0 && faceValueCol < row.size() ? parseOrZero(row.get(faceValueCol)) : 0;

                double issuePrice = faceValue + premium;

                Double before = closeBefore(symbol, exDate);

                if (before == null || issuePrice <= 0 || issuePrice >= before) {
                    continue;
                }

                double terp = (heldShares * before + newShares * issuePrice) / (heldShares + newShares);

                double factor = before / terp;

                if (factor > 1.005) {
                    result.add(new CorporateAction(symbol, exDate, "RIGHTS", factor,
                        purpose + String.format(" (issue price %.2f, TERP %.2f)", issuePrice, terp)));
                }
            }
        }

        connection.commit();

        return result;
    }

    /**
     * Unit splits missing from NSE's list (mostly ETFs): consecutive-day
     * drops matching a round ratio, not explained by a known action.
     */
    public List<CorporateAction> inferUnitSplits(Collection<CorporateAction> known) throws SQLException {

        Map<String, List<LocalDate>> knownDates = new HashMap<>();
        for (CorporateAction a : known) {
            knownDates.computeIfAbsent(a.symbol(), k -> new ArrayList<>()).add(a.exDate());
        }

        String sql = """
            WITH market_days AS (
                SELECT date, LAG(date) OVER (ORDER BY date) AS previous_market_day
                FROM (SELECT DISTINCT date FROM stock_data) d
            ),
            x AS (
                SELECT symbol, date, close_price AS c,
                       LAG(close_price) OVER (PARTITION BY symbol ORDER BY date) AS pc,
                       LAG(date) OVER (PARTITION BY symbol ORDER BY date) AS pd
                FROM stock_data
            )
            SELECT x.symbol, x.date, x.pc, x.c, x.pd
            FROM x
            JOIN market_days m ON m.date = x.date
            WHERE x.pc > 0 AND x.c > 0
              AND x.c < x.pc * 0.6
              AND x.pd = m.previous_market_day
            """;

        List<CorporateAction> result = new ArrayList<>();

        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {

                String symbol = rs.getString(1);
                LocalDate date = rs.getDate(2).toLocalDate();
                double before = rs.getDouble(3);
                double after = rs.getDouble(4);
                LocalDate previous = rs.getDate(5).toLocalDate();

                boolean explained = knownDates.getOrDefault(symbol, List.of()).stream()
                    .anyMatch(d -> d.isAfter(previous) && !d.isAfter(date));

                if (explained) {
                    continue;
                }

                double observed = before / after;

                for (double ratio : ROUND_RATIOS) {
                    if (Math.abs(observed / ratio - 1) < 0.07) {
                        result.add(new CorporateAction(symbol, date, "SPLIT", ratio,
                            String.format("Inferred from price (not in NSE list): %.2f -> %.2f", before, after)));
                        break;
                    }
                }
            }
        }

        connection.commit();

        return result;
    }

    private Double closeBefore(String symbol, LocalDate date) throws SQLException {
        return price("SELECT close_price FROM stock_data WHERE symbol = ? AND date < ? ORDER BY date DESC LIMIT 1", symbol, date);
    }

    private Double openOnOrAfter(String symbol, LocalDate date) throws SQLException {
        return price("SELECT open_price FROM stock_data WHERE symbol = ? AND date >= ? ORDER BY date LIMIT 1", symbol, date);
    }

    private Double price(String sql, String symbol, LocalDate date) throws SQLException {

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, symbol);
            ps.setDate(2, java.sql.Date.valueOf(date));

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : null;
            }
        }
    }

    private static int optionalColumn(List<String> header, String... names) {
        try {
            return NseCorporateActionImporter.column(header, names);
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    private static double parseOrZero(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
