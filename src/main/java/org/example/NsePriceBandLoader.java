package org.example;

import org.example.model.PriceBand;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class NsePriceBandLoader {

    private static final String URL = "https://nsearchives.nseindia.com/content/equities/sec_list.csv";

    private static final HttpClient CLIENT = HttpClient.newBuilder().followRedirects(
        HttpClient.Redirect.NORMAL).build();


    public static Map<String, PriceBand> load() throws Exception {

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(URL)).header("User-Agent",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " + "AppleWebKit/537.36 " + "(KHTML, like Gecko) " + "Chrome/151.0 Safari/537.36").header(
            "Accept", "text/csv,text/plain,*/*").GET().build();


        HttpResponse<String> response = CLIENT.send(request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));


        if (response.statusCode() != 200) {

            throw new IOException("NSE HTTP " + response.statusCode() + "\nURL: " + URL);
        }


        System.out.println("NSE price-band CSV downloaded");


        return parse(response.body());
    }


    private static Map<String, PriceBand> parse(String csv) {

        Map<String, PriceBand> result = new HashMap<>();


        String[] lines = csv.split("\\R");


        if (lines.length == 0) {
            return result;
        }


        System.out.println("Header: " + lines[0]);


        for (int i = 1; i < lines.length; i++) {

            String line = lines[i].trim();


            if (line.isEmpty()) {
                continue;
            }


            String[] columns = line.split(",", -1);


            try {

                /*
                 * Current NSE sec_list.csv format:
                 *
                 * SYMBOL
                 * SERIES
                 * SECURITY NAME
                 * PRICE BAND
                 */

                if (columns.length < 4) {
                    continue;
                }


                String symbol = clean(columns[0]);


                String series = clean(columns[1]);


                String securityName = clean(columns[2]);


                String band = clean(columns[3]);


                /*
                 * We only want normal equity.
                 */

                if (!"EQ".equalsIgnoreCase(series)) {
                    continue;
                }


                double bandPercent = parseBand(band);


                result.put(symbol, new PriceBand(symbol, bandPercent));

            } catch (Exception e) {

                System.err.println("Unable to parse: " + line);
            }
        }


        System.out.println("Price bands loaded: " + result.size());


        return result;
    }


    private static double parseBand(String value) {

        if (value == null || value.isBlank() || value.equals("-") || value.equalsIgnoreCase("No Band")) {

            return 0;
        }


        value = value.replace("%", "").trim();


        try {

            return Double.parseDouble(value);

        } catch (Exception e) {

            return 0;
        }
    }


    private static String clean(String value) {

        if (value == null) {
            return "";
        }

        return value.trim().replace("\"", "");
    }

    public static void main(String[] args) throws Exception {
        Map<String, PriceBand> bands = load();


        String[] testSymbols = {"ORIRAIL", "UFLEX", "AEROFLEX", "ABB"};


        for (String symbol : testSymbols) {

            PriceBand band = bands.get(symbol);


            if (band == null) {

                System.out.println(symbol + " -> NOT FOUND");

                continue;
            }


            System.out.println(symbol + " -> " + band.getBandPercent());
        }
    }
}
