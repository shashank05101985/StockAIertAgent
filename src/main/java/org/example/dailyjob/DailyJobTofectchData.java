package org.example.dailyjob;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.reader.DataLoader;
import org.example.repository.DB;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class DailyJobTofectchData {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // Used only when stock_data is empty
    private static final int FIRST_RUN_DAYS = 7;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final int PAGE_SIZE = 200;

    private static final int MAX_ATTEMPTS = 3;

    public static void main(String[] args) throws Exception {
        run();
    }

    /**
     * Daily data load, called from StockFeatureMain before features are calculated:
     * 1. bhavcopy for every day after the latest date already in stock_data, up to today
     * 2. fundamentals (only when BHARATSTOCK_API_KEY is set)
     * Re-running is safe: both tables are upserted.
     */
    public static void run() throws Exception {

        LocalDate today = LocalDate.now(IST);

        LocalDate latest = DataLoader.latestDate(DB.get());

        LocalDate from = latest == null ? today.minusDays(FIRST_RUN_DAYS) : latest.plusDays(1);

        if (from.isAfter(today)) {
            System.out.println("stock_data already up to date (" + latest + ")");
        } else {
            HistoricalBhavcopyLoader.loadRange(from, today);
        }

        // Fundamentals run only when the key is set, e.g. export BHARATSTOCK_API_KEY=bsk_live_...
        String bharatStockApiKey = System.getenv("BHARATSTOCK_API_KEY");
        if (bharatStockApiKey != null && !bharatStockApiKey.isBlank()) {
            updateFundamentalStocks(DB.get(), bharatStockApiKey);
        } else {
            System.out.println("BHARATSTOCK_API_KEY not set, skipping fundamentals update");
        }
    }

    public static void updateFundamentalStocks(Connection conn, String bharatStockApiKey) throws Exception {

        // One row per symbol per day; set once so a run that crosses midnight stays on one date
        LocalDate asOfDate = LocalDate.now(IST);

        int page = 1;

        int total = 0;

        while (true) {
            String url = "https://bharatstockapi.com/v1/screener" + "?exchange=NSE" + "&page=" + page + "&page_size=" + PAGE_SIZE + "&sort_by=market_cap" + "&sort_order=desc";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("X-API-Key", bharatStockApiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = sendWithRetry(request);

            JsonElement root = JsonParser.parseString(response.body());

            JsonElement dataElement = root.isJsonObject() ? root.getAsJsonObject().get("data") : null;

            if (dataElement == null || !dataElement.isJsonArray()) {

                throw new RuntimeException("Invalid BharatStock response: " + response.body());
            }

            JsonArray data = dataElement.getAsJsonArray();

            if (data.isEmpty()) {
                break;
            }

            String sql = """
                    INSERT INTO stock_fundamentals (
                        symbol,
                        company_name,
                        exchange,
                        price,
                        market_cap,
                        pe_ratio,
                        pb_ratio,
                        roe,
                        roce,
                        revenue_growth_yoy,
                        profit_growth_yoy,
                        eps_growth_yoy,
                        debt_to_equity,
                        current_ratio,
                        interest_coverage,
                        ebitda_margin,
                        net_margin,
                        cash_flow_operating,
                        free_cash_flow,
                        cfo_to_net_profit,
                        promoter_holding,
                        promoter_holding_change_qoq,
                        fii_holding,
                        dii_holding,
                        mutual_funds_holding,
                        return_1y,
                        return_3y,
                        volatility_30d,
                        fundamental_score,
                        fundamental_grade,
                        calculated_at,
                        as_of_date
                    )
                    VALUES (
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                    )
                    ON CONFLICT (symbol, as_of_date)
                    DO UPDATE SET
                    
                        company_name =
                            EXCLUDED.company_name,
                    
                        exchange =
                            EXCLUDED.exchange,
                    
                        price =
                            EXCLUDED.price,
                    
                        market_cap =
                            EXCLUDED.market_cap,
                    
                        pe_ratio =
                            EXCLUDED.pe_ratio,
                    
                        pb_ratio =
                            EXCLUDED.pb_ratio,
                    
                        roe =
                            EXCLUDED.roe,
                    
                        roce =
                            EXCLUDED.roce,
                    
                        revenue_growth_yoy =
                            EXCLUDED.revenue_growth_yoy,
                    
                        profit_growth_yoy =
                            EXCLUDED.profit_growth_yoy,
                    
                        eps_growth_yoy =
                            EXCLUDED.eps_growth_yoy,
                    
                        debt_to_equity =
                            EXCLUDED.debt_to_equity,
                    
                        current_ratio =
                            EXCLUDED.current_ratio,
                    
                        interest_coverage =
                            EXCLUDED.interest_coverage,
                    
                        ebitda_margin =
                            EXCLUDED.ebitda_margin,
                    
                        net_margin =
                            EXCLUDED.net_margin,
                    
                        cash_flow_operating =
                            EXCLUDED.cash_flow_operating,
                    
                        free_cash_flow =
                            EXCLUDED.free_cash_flow,
                    
                        cfo_to_net_profit =
                            EXCLUDED.cfo_to_net_profit,
                    
                        promoter_holding =
                            EXCLUDED.promoter_holding,
                    
                        promoter_holding_change_qoq =
                            EXCLUDED.promoter_holding_change_qoq,
                    
                        fii_holding =
                            EXCLUDED.fii_holding,
                    
                        dii_holding =
                            EXCLUDED.dii_holding,
                    
                        mutual_funds_holding =
                            EXCLUDED.mutual_funds_holding,
                    
                        return_1y =
                            EXCLUDED.return_1y,
                    
                        return_3y =
                            EXCLUDED.return_3y,
                    
                        volatility_30d =
                            EXCLUDED.volatility_30d,
                    
                        fundamental_score =
                            EXCLUDED.fundamental_score,
                    
                        fundamental_grade =
                            EXCLUDED.fundamental_grade,
                    
                        calculated_at =
                            EXCLUDED.calculated_at,
                    
                        last_updated =
                            CURRENT_TIMESTAMP
                    """;

            int saved = 0;

            int skipped = 0;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {

                for (JsonElement element : data) {

                    if (!element.isJsonObject()) {
                        skipped++;
                        continue;
                    }

                    JsonObject stock = element.getAsJsonObject();

                    // symbol is the conflict key; one bad row must not fail the whole page
                    JsonElement symbol = stock.get("symbol");
                    if (symbol == null || !symbol.isJsonPrimitive() || symbol.getAsString().isBlank()) {
                        skipped++;
                        continue;
                    }

                    double score = calculateFundamentalScore(stock);

                    String grade = getFundamentalGrade(score);

                    int p = 1;

                    setString(ps, p++, stock, "symbol");                         // 1
                    setString(ps, p++, stock, "company_name");                   // 2
                    setString(ps, p++, stock, "exchange");                       // 3

                    setDouble(ps, p++, stock, "price");                          // 4
                    setDouble(ps, p++, stock, "market_cap");                     // 5

                    setDouble(ps, p++, stock, "pe_ratio");                       // 6
                    setDouble(ps, p++, stock, "pb_ratio");                       // 7
                    setDouble(ps, p++, stock, "roe");                            // 8
                    setDouble(ps, p++, stock, "roce");                           // 9

                    setDouble(ps, p++, stock, "revenue_growth_yoy");             // 10
                    setDouble(ps, p++, stock, "profit_growth_yoy");              // 11
                    setDouble(ps, p++, stock, "eps_growth_yoy");                 // 12

                    setDouble(ps, p++, stock, "debt_to_equity");                 // 13
                    setDouble(ps, p++, stock, "current_ratio");                  // 14
                    setDouble(ps, p++, stock, "interest_coverage");              // 15

                    setDouble(ps, p++, stock, "ebitda_margin");                  // 16
                    setDouble(ps, p++, stock, "net_margin");                     // 17

                    setDouble(ps, p++, stock, "cash_flow_operating");            // 18
                    setDouble(ps, p++, stock, "free_cash_flow");                 // 19
                    setDouble(ps, p++, stock, "cfo_to_net_profit");              // 20

                    setDouble(ps, p++, stock, "promoter_holding");               // 21
                    setDouble(ps, p++, stock, "promoter_holding_change_qoq");                      // 22

                    setDouble(ps, p++, stock, "fii_holding");                   // 23
                    setDouble(ps, p++, stock, "dii_holding");                   // 24
                    setDouble(ps, p++, stock, "mutual_funds_holding");                             // 25

                    setDouble(ps, p++, stock, "return_1y");                      // 26
                    setDouble(ps, p++, stock, "return_3y");                      // 27
                    setDouble(ps, p++, stock, "volatility_30d");                 // 28

                    // Our calculated values
                    ps.setDouble(p++, score);                                    // 29
                    ps.setString(p++, grade);                                    // 30

                    ps.setTimestamp(p++, Timestamp.valueOf(LocalDateTime.now()));   // 31

                    ps.setDate(p++, java.sql.Date.valueOf(asOfDate));                // 32

                    ps.addBatch();
                    saved++;
                }

                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException("Failed saving fundamentals page " + page, e);
            }

            total += saved;
            System.out.println("Fundamentals page " + page + ": saved " + saved + ", skipped " + skipped);

            if (data.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }

        System.out.println("Fundamental stocks updated: " + total);
    }

    private static HttpResponse<String> sendWithRetry(HttpRequest request) throws Exception {

        for (int attempt = 1; ; attempt++) {
            try {
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status == 200) {
                    return response;
                }

                // Retry only rate limits and server errors
                boolean retryable = status == 429 || status >= 500;
                if (!retryable || attempt >= MAX_ATTEMPTS) {
                    throw new RuntimeException("BharatStock API error " + status + " : " + response.body());
                }
            } catch (java.io.IOException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
            }
            Thread.sleep(2000L * attempt);
        }
    }

    private static String getFundamentalGrade(double score) {

        if (score >= 85)
            return "A+";

        if (score >= 75)
            return "A";

        if (score >= 70)
            return "B";

        if (score >= 60)
            return "C";

        return "D";
    }

    /** Returns the field as a number, or null when it is missing or not numeric (e.g. "N/A"). */
    private static Double getDouble(JsonObject node, String field) {

        JsonElement value = node.get(field);

        if (value == null || !value.isJsonPrimitive()) {

            return null;
        }

        try {
            double d = Double.parseDouble(value.getAsString().trim());
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void setDouble(PreparedStatement ps, int index, JsonObject node, String field) throws SQLException {

        Double value = getDouble(node, field);

        if (value == null) {

            ps.setNull(index, Types.NUMERIC);

            return;
        }

        ps.setDouble(index, value);
    }


    private static void setString(PreparedStatement ps, int index, JsonObject node, String field) throws SQLException {

        JsonElement value = node.get(field);

        if (value == null || !value.isJsonPrimitive()) {

            ps.setNull(index, Types.VARCHAR);

            return;
        }

        ps.setString(index, value.getAsString());
    }

    private static double calculateFundamentalScore(JsonObject s) {

        double score = 0;

        Double roe = getDouble(s, "roe");

        Double roce = getDouble(s, "roce");

        Double revenueGrowth = getDouble(s, "revenue_growth_yoy");

        Double profitGrowth = getDouble(s, "profit_growth_yoy");

        Double debt = getDouble(s, "debt_to_equity");

        Double interest = getDouble(s, "interest_coverage");

        Double cfo = getDouble(s, "cash_flow_operating");

        Double fcf = getDouble(s, "free_cash_flow");

        Double promoter = getDouble(s, "promoter_holding");

        Double epsGrowth = getDouble(s, "eps_growth_yoy");


        // =========================================
        // ROE - 15 POINTS
        // =========================================

        if (roe != null) {

            if (roe >= 20)
                score += 15;

            else if (roe >= 15)
                score += 12;

            else if (roe >= 10)
                score += 7;
        }


        // =========================================
        // ROCE - 15 POINTS
        // =========================================

        if (roce != null) {

            if (roce >= 20)
                score += 15;

            else if (roce >= 15)
                score += 12;

            else if (roce >= 10)
                score += 7;
        }


        // =========================================
        // REVENUE GROWTH - 10 POINTS
        // =========================================

        if (revenueGrowth != null) {

            if (revenueGrowth >= 20)
                score += 10;

            else if (revenueGrowth >= 10)
                score += 7;

            else if (revenueGrowth > 0)
                score += 3;
        }


        // =========================================
        // PROFIT GROWTH - 15 POINTS
        // =========================================

        if (profitGrowth != null) {

            if (profitGrowth >= 20)
                score += 15;

            else if (profitGrowth >= 10)
                score += 10;

            else if (profitGrowth > 0)
                score += 5;
        }


        // =========================================
        // DEBT - 10 POINTS
        // =========================================

        // Negative D/E means negative equity, which is a red flag, so it scores 0
        if (debt != null && debt >= 0) {

            if (debt <= 0.3)
                score += 10;

            else if (debt <= 0.8)
                score += 7;

            else if (debt <= 1.5)
                score += 3;
        }


        // =========================================
        // INTEREST COVERAGE - 10 POINTS
        // =========================================

        if (interest != null) {

            if (interest >= 8)
                score += 10;

            else if (interest >= 5)
                score += 7;

            else if (interest >= 3)
                score += 4;
        }


        // =========================================
        // CASH FLOW - 10 POINTS
        // =========================================

        if (cfo != null && cfo > 0) {
            score += 5;
        }

        if (fcf != null && fcf > 0) {
            score += 5;
        }


        // =========================================
        // PROMOTER - 5 POINTS
        // =========================================

        if (promoter != null) {

            if (promoter >= 50)
                score += 5;

            else if (promoter >= 30)
                score += 3;

            else if (promoter >= 20)
                score += 1;
        }


        // =========================================
        // EPS GROWTH - 10 POINTS
        // =========================================

        if (epsGrowth != null) {

            if (epsGrowth >= 20)
                score += 10;

            else if (epsGrowth >= 10)
                score += 7;

            else if (epsGrowth > 0)
                score += 3;
        }


        return Math.min(score, 100);
    }
}
