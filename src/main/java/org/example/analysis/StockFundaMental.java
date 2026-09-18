package org.example.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class StockFundaMental {

    public static void updateAllFundamentals(Connection conn, String bharatStockApiKey) throws Exception {

        ObjectMapper mapper = new ObjectMapper();

        HttpClient client = HttpClient.newHttpClient();

        conn.setAutoCommit(false);

        try {

            // =====================================================
            // 1. GET ALL STOCKS FROM SCREENER
            // =====================================================

            List<JsonNode> stocks = getAllScreenerStocks(client, mapper, bharatStockApiKey);

            System.out.println("Total stocks received = " + stocks.size());


            // =====================================================
            // 2. INSERT BASIC FUNDAMENTALS
            // =====================================================

            for (JsonNode stock : stocks) {

                try {

                    insertFundamentals(conn, stock);

                } catch (Exception e) {

                    System.err.println("Fundamental error: " + getText(stock, "symbol") + " : " + e.getMessage());
                }
            }


            // =====================================================
            // 3. FINANCIALS + SHAREHOLDING
            // =====================================================

            int count = 0;

            for (JsonNode stock : stocks) {

                String symbol = getText(stock, "symbol");

                if (symbol == null || symbol.isBlank()) {
                    continue;
                }

                try {

                    // ---------------------------------------------
                    // FINANCIALS
                    // ---------------------------------------------

                    JsonNode financials = getFinancials(client, mapper, bharatStockApiKey, symbol);

                    if (financials != null) {

                        insertFinancials(conn, symbol, financials);
                    }


                    // ---------------------------------------------
                    // SHAREHOLDING
                    // ---------------------------------------------

                    JsonNode shareholding = getShareholding(client, mapper, bharatStockApiKey, symbol);

                    if (shareholding != null) {

                        insertShareholding(conn, symbol, shareholding);
                    }


                    count++;

                    System.out.println("Processed " + count + "/" + stocks.size() + " : " + symbol);


                    // Don't hammer the API
                    Thread.sleep(100);

                } catch (Exception e) {

                    System.err.println("Error processing " + symbol + " : " + e.getMessage());
                }
            }


            conn.commit();

            System.out.println("====================================");

            System.out.println("Fundamental update completed");

            System.out.println("Stocks = " + stocks.size());

            System.out.println("====================================");


        } catch (Exception e) {

            conn.rollback();

            throw e;
        }
    }

    private static void insertShareholding(Connection conn, String symbol, JsonNode data) throws SQLException {

        if (data == null || !data.isArray()) {
            return;
        }


        String sql = """
            INSERT INTO stock_shareholding (
                symbol,
                period_end_date,
            
                promoter_holding,
                promoter_pledged,
            
                fii_holding,
                dii_holding,
                mutual_funds_holding,
            
                public_holding,
                individuals_holding,
            
                fii_change_qoq,
                dii_change_qoq,
                mutual_funds_change_qoq,
                promoter_change_qoq
            )
            VALUES (
                ?,?,
                ?,?,
                ?,?,?,
                ?,?,
                ?,?,?,?
            )
            ON CONFLICT (
                symbol,
                period_end_date
            )
            DO UPDATE SET
            
                promoter_holding =
                    EXCLUDED.promoter_holding,
            
                promoter_pledged =
                    EXCLUDED.promoter_pledged,
            
                fii_holding =
                    EXCLUDED.fii_holding,
            
                dii_holding =
                    EXCLUDED.dii_holding,
            
                mutual_funds_holding =
                    EXCLUDED.mutual_funds_holding,
            
                public_holding =
                    EXCLUDED.public_holding,
            
                individuals_holding =
                    EXCLUDED.individuals_holding,
            
                fii_change_qoq =
                    EXCLUDED.fii_change_qoq,
            
                dii_change_qoq =
                    EXCLUDED.dii_change_qoq,
            
                mutual_funds_change_qoq =
                    EXCLUDED.mutual_funds_change_qoq,
            
                promoter_change_qoq =
                    EXCLUDED.promoter_change_qoq
            """;


        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            for (JsonNode row : data) {

                int p = 1;


                setDate(ps, p++, row, "period_end_date");


                setDouble(ps, p++, row, "promoter_holding");

                setDouble(ps, p++, row, "promoter_pledged");


                setDouble(ps, p++, row, "fii_holding");

                setDouble(ps, p++, row, "dii_holding");

                setDouble(ps, p++, row, "mutual_funds_holding");


                setDouble(ps, p++, row, "public_holding");

                setDouble(ps, p++, row, "individuals_holding");


                setDouble(ps, p++, row, "fii_change_qoq");

                setDouble(ps, p++, row, "dii_change_qoq");

                setDouble(ps, p++, row, "mutual_funds_change_qoq");

                setDouble(ps, p++, row, "promoter_change_qoq");


                ps.addBatch();
            }


            ps.executeBatch();
        }
    }

    private static List<JsonNode> getAllScreenerStocks(HttpClient client, ObjectMapper mapper, String apiKey) throws Exception {

        List<JsonNode> result = new ArrayList<>();

        int page = 1;

        int pageSize = 200;

        while (true) {

            String url = "https://bharatstockapi.com/v1/screener" + "?exchange=NSE" + "&page=" + page + "&page_size=" + pageSize + "&sort_by=market_cap" + "&sort_order=desc";


            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("X-API-Key",
                apiKey).GET().build();


            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());


            if (response.statusCode() != 200) {

                throw new RuntimeException("Screener API error " + response.statusCode() + " : " + response.body());
            }


            JsonNode root = mapper.readTree(response.body());


            JsonNode data = root.get("data");


            if (data == null || !data.isArray() || data.isEmpty()) {

                break;
            }


            for (JsonNode stock : data) {

                result.add(stock);
            }


            System.out.println("Screener page " + page + " = " + data.size());


            if (data.size() < pageSize) {
                break;
            }


            page++;
        }


        return result;
    }

    private static void insertFundamentals(Connection conn, JsonNode stock) throws SQLException {

        String sql = """
            INSERT INTO stock_fundamentals (
                symbol,
                company_name,
                exchange,
                isin,
                sector,
            
                price,
                market_cap,
            
                pe_ratio,
                pb_ratio,
                peg_ratio,
                ev_to_ebitda,
            
                roe,
                roce,
            
                high_52w,
                low_52w,
            
                eps,
                book_value_per_share,
            
                debt_to_equity,
                current_ratio,
                interest_coverage,
            
                ebitda_margin,
                operating_margin,
                net_margin,
            
                revenue_ttm,
                net_profit_ttm,
            
                price_vs_50dma_pct,
                price_vs_200dma_pct,
            
                return_1m,
                return_3m,
                return_6m,
                return_1y,
                return_3y,
            
                fundamental_score,
                fundamental_grade,
            
                last_updated
            )
            VALUES (
                ?,?,?,?,?,?,
                ?,?,
                ?,?, ?,?,
                ?,?,
                ?,?,
                ?,?,
                ?,?,?,
                ?,?,?,
                ?,?,
                ?,?,
                ?,?,?,?,?,
                ?,?,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (symbol)
            DO UPDATE SET
            
                company_name = EXCLUDED.company_name,
                exchange = EXCLUDED.exchange,
                isin = EXCLUDED.isin,
                sector = EXCLUDED.sector,
            
                price = EXCLUDED.price,
                market_cap = EXCLUDED.market_cap,
            
                pe_ratio = EXCLUDED.pe_ratio,
                pb_ratio = EXCLUDED.pb_ratio,
                peg_ratio = EXCLUDED.peg_ratio,
                ev_to_ebitda = EXCLUDED.ev_to_ebitda,
            
                roe = EXCLUDED.roe,
                roce = EXCLUDED.roce,
            
                high_52w = EXCLUDED.high_52w,
                low_52w = EXCLUDED.low_52w,
            
                eps = EXCLUDED.eps,
                book_value_per_share =
                    EXCLUDED.book_value_per_share,
            
                debt_to_equity =
                    EXCLUDED.debt_to_equity,
            
                current_ratio =
                    EXCLUDED.current_ratio,
            
                interest_coverage =
                    EXCLUDED.interest_coverage,
            
                ebitda_margin =
                    EXCLUDED.ebitda_margin,
            
                operating_margin =
                    EXCLUDED.operating_margin,
            
                net_margin =
                    EXCLUDED.net_margin,
            
                revenue_ttm =
                    EXCLUDED.revenue_ttm,
            
                net_profit_ttm =
                    EXCLUDED.net_profit_ttm,
            
                price_vs_50dma_pct =
                    EXCLUDED.price_vs_50dma_pct,
            
                price_vs_200dma_pct =
                    EXCLUDED.price_vs_200dma_pct,
            
                return_1m = EXCLUDED.return_1m,
                return_3m = EXCLUDED.return_3m,
                return_6m = EXCLUDED.return_6m,
                return_1y = EXCLUDED.return_1y,
                return_3y = EXCLUDED.return_3y,
            
                fundamental_score =
                    EXCLUDED.fundamental_score,
            
                fundamental_grade =
                    EXCLUDED.fundamental_grade,
            
                last_updated =
                    CURRENT_TIMESTAMP
            """;


        double score = calculateFundamentalScore(stock);

        String grade = getFundamentalGrade(score);


        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            int p = 1;


            setString(ps, p++, stock, "symbol");
            setString(ps, p++, stock, "company_name");
            setString(ps, p++, stock, "exchange");
            setString(ps, p++, stock, "isin");
            setString(ps, p++, stock, "sector");


            setDouble(ps, p++, stock, "price");
            setDouble(ps, p++, stock, "market_cap");


            setDouble(ps, p++, stock, "pe_ratio");
            setDouble(ps, p++, stock, "pb_ratio");
            setDouble(ps, p++, stock, "peg_ratio");
            setDouble(ps, p++, stock, "ev_to_ebitda");


            setDouble(ps, p++, stock, "roe");
            setDouble(ps, p++, stock, "roce");


            setDouble(ps, p++, stock, "high_52w");
            setDouble(ps, p++, stock, "low_52w");


            setDouble(ps, p++, stock, "eps");
            setDouble(ps, p++, stock, "book_value_per_share");


            setDouble(ps, p++, stock, "debt_to_equity");

            setDouble(ps, p++, stock, "current_ratio");

            setDouble(ps, p++, stock, "interest_coverage");


            setDouble(ps, p++, stock, "ebitda_margin");

            setDouble(ps, p++, stock, "operating_margin");

            setDouble(ps, p++, stock, "net_margin");


            setDouble(ps, p++, stock, "revenue_ttm");

            setDouble(ps, p++, stock, "net_profit_ttm");


            setDouble(ps, p++, stock, "price_vs_50dma_pct");

            setDouble(ps, p++, stock, "price_vs_200dma_pct");


            setDouble(ps, p++, stock, "return_1m");

            setDouble(ps, p++, stock, "return_3m");

            setDouble(ps, p++, stock, "return_6m");

            setDouble(ps, p++, stock, "return_1y");

            setDouble(ps, p++, stock, "return_3y");


            ps.setDouble(p++, score);

            ps.setString(p++, grade);


            ps.executeUpdate();
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

    private static Double getDouble(JsonNode node, String field) {

        JsonNode value = node.get(field);

        if (value == null || value.isNull()) {

            return null;
        }

        return value.asDouble();
    }

    private static double calculateFundamentalScore(JsonNode s) {

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

        if (debt != null) {

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

    private static JsonNode getFinancials(HttpClient client, ObjectMapper mapper, String apiKey, String symbol) throws Exception {

        String url = "https://bharatstockapi.com/v1/stocks/" + URLEncoder.encode(symbol,
            StandardCharsets.UTF_8) + "/financials";


        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("X-API-Key", apiKey).GET().build();


        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());


        if (response.statusCode() != 200) {

            System.err.println("Financial API " + symbol + " " + response.statusCode());

            return null;
        }


        JsonNode root = mapper.readTree(response.body());

        return root.get("data");
    }

    private static void insertFinancials(Connection conn, String symbol, JsonNode financials) throws SQLException {

        if (financials == null) {
            return;
        }


        if (!financials.isArray()) {
            return;
        }


        String sql = """
            INSERT INTO stock_financials (
                symbol,
                period_type,
                period_end_date,
            
                revenue,
                operating_profit,
                ebitda,
                net_profit,
                eps,
            
                total_assets,
                total_debt,
            
                operating_cash_flow,
                investing_cash_flow,
                financing_cash_flow,
            
                free_cash_flow,
            
                revenue_growth_yoy,
                profit_growth_yoy,
                eps_growth_yoy
            )
            VALUES (
                ?,?,?,?,
                ?,?,?,?,
                ?,?,
                ?,?,?,
                ?,?,
                ?,?
            )
            ON CONFLICT (
                symbol,
                period_type,
                period_end_date
            )
            DO UPDATE SET
            
                revenue =
                    EXCLUDED.revenue,
            
                operating_profit =
                    EXCLUDED.operating_profit,
            
                ebitda =
                    EXCLUDED.ebitda,
            
                net_profit =
                    EXCLUDED.net_profit,
            
                eps =
                    EXCLUDED.eps,
            
                total_assets =
                    EXCLUDED.total_assets,
            
                total_debt =
                    EXCLUDED.total_debt,
            
                operating_cash_flow =
                    EXCLUDED.operating_cash_flow,
            
                investing_cash_flow =
                    EXCLUDED.investing_cash_flow,
            
                financing_cash_flow =
                    EXCLUDED.financing_cash_flow,
            
                free_cash_flow =
                    EXCLUDED.free_cash_flow,
            
                revenue_growth_yoy =
                    EXCLUDED.revenue_growth_yoy,
            
                profit_growth_yoy =
                    EXCLUDED.profit_growth_yoy,
            
                eps_growth_yoy =
                    EXCLUDED.eps_growth_yoy
            """;


        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            for (JsonNode row : financials) {

                int p = 1;


                setString(ps, p++, row, "period_type");

                setDate(ps, p++, row, "period_end_date");


                setDouble(ps, p++, row, "revenue");

                setDouble(ps, p++, row, "operating_profit");

                setDouble(ps, p++, row, "ebitda");

                setDouble(ps, p++, row, "net_profit");

                setDouble(ps, p++, row, "eps");


                setDouble(ps, p++, row, "total_assets");

                setDouble(ps, p++, row, "total_debt");


                setDouble(ps, p++, row, "operating_cash_flow");

                setDouble(ps, p++, row, "investing_cash_flow");

                setDouble(ps, p++, row, "financing_cash_flow");


                setDouble(ps, p++, row, "free_cash_flow");


                setDouble(ps, p++, row, "revenue_growth_yoy");

                setDouble(ps, p++, row, "profit_growth_yoy");

                setDouble(ps, p++, row, "eps_growth_yoy");


                ps.addBatch();
            }


            ps.executeBatch();
        }
    }

    private static JsonNode getShareholding(HttpClient client, ObjectMapper mapper, String apiKey, String symbol) throws Exception {

        String url = "https://bharatstockapi.com/v1/stocks/" + URLEncoder.encode(symbol,
            StandardCharsets.UTF_8) + "/shareholding";


        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("X-API-Key", apiKey).GET().build();


        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());


        if (response.statusCode() != 200) {

            System.err.println("Shareholding API " + symbol + " " + response.statusCode());

            return null;
        }


        JsonNode root = mapper.readTree(response.body());

        return root.get("data");
    }

    private static String getText(JsonNode node, String field) {

        JsonNode value = node.get(field);

        if (value == null || value.isNull()) {

            return null;
        }

        return value.asText();
    }

    private static void setString(PreparedStatement ps, int index, JsonNode node, String field) throws SQLException {

        String value = getText(node, field);

        if (value == null || value.isBlank()) {

            ps.setNull(index, Types.VARCHAR);

        } else {

            ps.setString(index, value);
        }
    }

    private static void setDouble(PreparedStatement ps, int index, JsonNode node, String field) throws SQLException {

        JsonNode value = node.get(field);

        if (value == null || value.isNull() || value.asText().isBlank()) {

            ps.setNull(index, Types.NUMERIC);

            return;
        }

        try {

            ps.setDouble(index, value.asDouble());

        } catch (Exception e) {

            ps.setNull(index, Types.NUMERIC);
        }
    }

    private static void setDate(PreparedStatement ps, int index, JsonNode node, String field) throws SQLException {

        String value = getText(node, field);

        if (value == null || value.isBlank()) {

            ps.setNull(index, Types.DATE);

            return;
        }

        try {

            ps.setDate(index, Date.valueOf(value.substring(0, 10)));

        } catch (Exception e) {

            ps.setNull(index, Types.DATE);
        }
    }
}
