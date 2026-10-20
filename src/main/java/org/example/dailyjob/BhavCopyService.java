package org.example.dailyjob;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class BhavCopyService {

    private static final DateTimeFormatter NSE_DATE =
            DateTimeFormatter.ofPattern("ddMMyyyy");

    public List<BhavcopyRecord> fetchBhavcopy(
            LocalDate date
    ) {

        List<BhavcopyRecord> list =
                new ArrayList<>();

        BufferedReader reader = null;

        try {

            String formattedDate =
                    date.format(NSE_DATE);

            String urlString =
                    "https://archives.nseindia.com/products/content/sec_bhavdata_full_"
                            + formattedDate
                            + ".csv";
            System.out.println(urlString);

            URL url =
                    new URL(urlString);

            HttpURLConnection conn =
                    (HttpURLConnection) url.openConnection();

            conn.setConnectTimeout(10000);

            conn.setReadTimeout(10000);

            conn.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0"
            );

            conn.setRequestProperty(
                    "Accept",
                    "text/csv"
            );

            reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    conn.getInputStream()
                            )
                    );

            String line;

            boolean header = true;

            while ((line = reader.readLine()) != null) {

                // Skip header
                if (header) {
                    header = false;
                    continue;
                }

                // Skip empty lines
                if (line.isBlank()) {
                    continue;
                }

                String[] cols =
                        line.split(",");

                // Safety check
                if (cols.length < 15) {
                    continue;
                }

                try {

                    String symbol =
                            cols[0].trim();

                    String series =
                            cols[1].trim();

                    // Only EQ stocks
                    if (!"EQ".equals(series)) {
                        continue;
                    }

                    // Delivery qty unavailable
                    if (cols[13].trim().contains("-")) {
                        continue;
                    }

                    BhavcopyRecord stock =
                            new BhavcopyRecord();

                    stock.symbol = symbol;

                    stock.series = series;

                    stock.date = date;

                    // OHLC
                    stock.openPrice =
                            parseDouble(cols[4]);

                    stock.highPrice =
                            parseDouble(cols[5]);

                    stock.lowPrice =
                            parseDouble(cols[6]);

                    stock.closePrice =
                            parseDouble(cols[8]);

                    stock.tradedQty =
                            parseLong(cols[10]);

                    stock.turnover =
                            parseDouble(cols[11]);

                    stock.numberOfTrades =
                            parseLong(cols[12]);

                    stock.deliveryQty =
                            parseLong(cols[13]);

                    stock.deliveryPercent =
                            parseDouble(cols[14]);

                    if (stock.isValid()) {
                        list.add(stock);
                    }

                } catch (Exception rowError) {

                    System.err.println(
                            "Failed parsing row: "
                                    + line
                    );
                }
            }

            System.out.println(
                    "Bhavcopy loaded: "
                            + list.size()
                            + " records for "
                            + date
            );

        } catch (Exception e) {

            System.err.println(
                    "Failed downloading bhavcopy for "
                            + date
            );

            e.printStackTrace();

        } finally {

            try {

                if (reader != null) {
                    reader.close();
                }

            } catch (Exception ignored) {
            }
        }

        return list;
    }

    // =========================
    // SAFE PARSERS
    // =========================

    private static double parseDouble(
            String s
    ) {

        try {

            return Double.parseDouble(
                    s.trim()
            );

        } catch (Exception e) {

            return 0;
        }
    }

    private static long parseLong(
            String s
    ) {

        try {

            return Long.parseLong(
                    s.trim()
            );

        } catch (Exception e) {

            return 0;
        }
    }
}