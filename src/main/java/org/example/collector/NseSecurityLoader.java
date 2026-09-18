package org.example.collector;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Instrument;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

public class NseSecurityLoader {


    private static final String API_KEY = "8311x4p8tm56j4vc";
    private static final String ACCESS_TOKEN = "5JNQyh3LWGcNyfS9FTgM732tVWVfctiU";

    public static void load(Connection connection) throws Exception, KiteException {

        KiteConnect kite = new KiteConnect(API_KEY);

        kite.setAccessToken(ACCESS_TOKEN);

        List<Instrument> instruments = kite.getInstruments("NSE");

        String sql = """
            INSERT INTO nse_security_master (
                symbol,
                company_name,
                series,
                active,
                updated_at
            )
            VALUES (?, ?, ?, true, CURRENT_TIMESTAMP)
            
            ON CONFLICT (symbol)
            DO UPDATE SET
                company_name = EXCLUDED.company_name,
                series = EXCLUDED.series,
                isin = EXCLUDED.isin,
                active = true,
                updated_at = CURRENT_TIMESTAMP
            """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            int count = 0;

            for (Instrument instrument : instruments) {

                if (instrument == null) {
                    continue;
                }

                String symbol = instrument.getTradingsymbol();

                if (symbol == null || symbol.isBlank()) {
                    continue;
                }

                ps.setString(1, symbol.trim());

                ps.setString(2, instrument.getName());

                ps.setString(3, instrument.getSegment());

                ps.addBatch();

                count++;

                if (count % 500 == 0) {
                    ps.executeBatch();
                }
                connection.commit();
            }

            ps.executeBatch();


            System.out.println("NSE securities loaded: " + count);
        }
    }

    public static  Map<String, String> loadTitleSymbolMap(Connection connection)
        throws SQLException {

        Map<String, String> map = new HashMap<>();

        String sql = """
        SELECT symbol, company_name
        FROM nse_security_master
        WHERE active = true
          AND company_name IS NOT NULL
          AND company_name <> ''
        """;

        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {

                String symbol = rs.getString("symbol");
                String companyName = rs.getString("company_name");

                if (symbol == null || companyName == null) {
                    continue;
                }

                map.put(
                    normalize(companyName),
                    symbol
                );
            }
        }

        System.out.println(
            "Title → Symbol map loaded: " + map.size()
        );

        return map;
    }
    public static  String normalize(String value) {

        return value
            .toUpperCase(Locale.ROOT)
            .replace("&", " AND ")
            .replaceAll("[^A-Z0-9 ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }
}
