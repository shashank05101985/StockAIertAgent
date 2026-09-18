package org.example.zerodha;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.example.model.IntradayCandle;
import org.example.model.StockData;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Zerodha Data Fetcher
 * Fetches historical and real-time data from Zerodha Kite API
 */
public class ZerodhaDataFetcher {

    private final ZerodhaConfig config;
    private final OkHttpClient httpClient;
    private final Gson gson;

    // Instrument token cache (symbol -> token)
    private Map<String, Long> instrumentTokens = new HashMap<>();

    // NSE exchange segment
    private static final String EXCHANGE_NSE = "NSE";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ZerodhaDataFetcher() {
        this.config = ZerodhaConfig.getInstance();
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
        this.gson = new Gson();
    }

    /**
     * Initialize with API credentials
     */
    public ZerodhaDataFetcher(String apiKey, String apiSecret) {
        this();
        config.setCredentials(apiKey, apiSecret);
    }

    /**
     * Generate access token from request token
     * Call this after user logs in via login URL
     */
    public String generateAccessToken(String requestToken) throws IOException {
        if (!config.isConfigured()) {
            throw new IllegalStateException("API credentials not configured");
        }

        // Create checksum
        String checksum = sha256(config.getApiKey() + requestToken + config.getApiSecret());

        RequestBody formBody = new FormBody.Builder()
            .add("api_key", config.getApiKey())
            .add("request_token", requestToken)
            .add("checksum", checksum)
            .build();

        Request request = new Request.Builder()
            .url(ZerodhaConfig.BASE_URL + "/session/token")
            .post(formBody)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to generate access token: " + response.code());
            }

            String body = response.body().string();
            JsonObject json = gson.fromJson(body, JsonObject.class);

            if (json.has("data")) {
                JsonObject data = json.getAsJsonObject("data");
                String accessToken = data.get("access_token").getAsString();
                String userId = data.get("user_id").getAsString();

                config.setAccessToken(accessToken);
                config.setUserId(userId);
                config.saveAccessToken(accessToken);

                return accessToken;
            } else {
                throw new IOException("Invalid response: " + body);
            }
        }
    }

    /**
     * Load instrument tokens for given symbols
     */
    public void loadInstrumentTokens(List<String> symbols) throws IOException {
        // Fetch all NSE instruments
        Request request = new Request.Builder()
            .url(ZerodhaConfig.BASE_URL + "/instruments/NSE")
            .addHeader("Authorization", "token " + config.getApiKey() + ":" + config.getAccessToken())
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch instruments: " + response.code());
            }

            String csvData = response.body().string();
            String[] lines = csvData.split("\n");

            // Parse CSV (skip header)
            for (int i = 1; i < lines.length; i++) {
                String[] parts = lines[i].split(",");
                if (parts.length >= 3) {
                    long token = Long.parseLong(parts[0].trim());
                    String tradingsymbol = parts[2].trim().replace("\"", "");

                    if (symbols.contains(tradingsymbol)) {
                        instrumentTokens.put(tradingsymbol, token);
                    }
                }
            }
        }

        System.out.println("Loaded " + instrumentTokens.size() + " instrument tokens");
    }

    /**
     * Get instrument token for a symbol
     */
    public long getInstrumentToken(String symbol) {
        return instrumentTokens.getOrDefault(symbol, 0L);
    }

    /**
     * Fetch historical candles (OHLC data)
     */
    public List<IntradayCandle> getHistoricalData(String symbol, String interval,
                                                   LocalDateTime from, LocalDateTime to) throws IOException {
        List<IntradayCandle> candles = new ArrayList<>();

        long token = getInstrumentToken(symbol);
        if (token == 0) {
            System.err.println("Instrument token not found for: " + symbol);
            return candles;
        }

        String fromStr = from.format(DATETIME_FORMAT);
        String toStr = to.format(DATETIME_FORMAT);

        String url = String.format("%s/instruments/historical/%d/%s?from=%s&to=%s",
            ZerodhaConfig.BASE_URL, token, interval, fromStr, toStr);

        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "token " + config.getApiKey() + ":" + config.getAccessToken())
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch historical data: " + response.code());
            }

            String body = response.body().string();
            JsonObject json = gson.fromJson(body, JsonObject.class);

            if (json.has("data") && json.getAsJsonObject("data").has("candles")) {
                JsonArray candlesArray = json.getAsJsonObject("data").getAsJsonArray("candles");

                int intervalMinutes = parseInterval(interval);

                for (int i = 0; i < candlesArray.size(); i++) {
                    JsonArray candle = candlesArray.get(i).getAsJsonArray();

                    String timestampStr = candle.get(0).getAsString();
                    LocalDateTime timestamp = LocalDateTime.parse(timestampStr.substring(0, 19), DATETIME_FORMAT);

                    double open = candle.get(1).getAsDouble();
                    double high = candle.get(2).getAsDouble();
                    double low = candle.get(3).getAsDouble();
                    double close = candle.get(4).getAsDouble();
                    long volume = candle.get(5).getAsLong();

                    IntradayCandle c = new IntradayCandle(symbol, timestamp, open, high, low, close, volume, intervalMinutes);
                    candles.add(c);
                }
            }
        }

        return candles;
    }

    /**
     * Fetch today's intraday data (5-minute candles)
     */
    public List<IntradayCandle> getTodayIntradayData(String symbol) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime marketOpen = now.withHour(9).withMinute(15).withSecond(0).withNano(0);

        return getHistoricalData(symbol, ZerodhaConfig.INTERVAL_5MINUTE, marketOpen, now);
    }

    /**
     * Fetch daily data for last N days
     */
    public List<StockData> getDailyData(String symbol, int days) throws IOException {
        List<StockData> stockDataList = new ArrayList<>();

        long token = getInstrumentToken(symbol);
        if (token == 0) {
            System.err.println("Instrument token not found for: " + symbol);
            return stockDataList;
        }

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days);

        String url = String.format("%s/instruments/historical/%d/day?from=%s&to=%s",
            ZerodhaConfig.BASE_URL, token, from.format(DATE_FORMAT), to.format(DATE_FORMAT));

        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "token " + config.getApiKey() + ":" + config.getAccessToken())
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch daily data: " + response.code());
            }

            String body = response.body().string();
            JsonObject json = gson.fromJson(body, JsonObject.class);

            if (json.has("data") && json.getAsJsonObject("data").has("candles")) {
                JsonArray candlesArray = json.getAsJsonObject("data").getAsJsonArray("candles");

                StockData previous = null;

                for (int i = 0; i < candlesArray.size(); i++) {
                    JsonArray candle = candlesArray.get(i).getAsJsonArray();

                    String dateStr = candle.get(0).getAsString().substring(0, 10);
                    LocalDate date = LocalDate.parse(dateStr, DATE_FORMAT);

                    double open = candle.get(1).getAsDouble();
                    double high = candle.get(2).getAsDouble();
                    double low = candle.get(3).getAsDouble();
                    double close = candle.get(4).getAsDouble();
                    long volume = candle.get(5).getAsLong();

                    StockData data = new StockData(symbol, date, open, high, low, close, volume, 0);

                    if (previous != null) {
                        data.setPreviousClose(previous.getClose());
                    }

                    stockDataList.add(data);
                    previous = data;
                }
            }
        }

        return stockDataList;
    }

    /**
     * Get current LTP (Last Traded Price) for symbols
     */
    public Map<String, Double> getLTP(List<String> symbols) throws IOException {
        Map<String, Double> ltpMap = new HashMap<>();

        StringBuilder instrumentsParam = new StringBuilder();
        for (String symbol : symbols) {
            long token = getInstrumentToken(symbol);
            if (token > 0) {
                if (instrumentsParam.length() > 0) {
                    instrumentsParam.append("&");
                }
                instrumentsParam.append("i=").append(EXCHANGE_NSE).append(":").append(symbol);
            }
        }

        String url = ZerodhaConfig.BASE_URL + "/quote/ltp?" + instrumentsParam;

        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "token " + config.getApiKey() + ":" + config.getAccessToken())
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch LTP: " + response.code());
            }

            String body = response.body().string();
            JsonObject json = gson.fromJson(body, JsonObject.class);

            if (json.has("data")) {
                JsonObject data = json.getAsJsonObject("data");

                for (String symbol : symbols) {
                    String key = EXCHANGE_NSE + ":" + symbol;
                    if (data.has(key)) {
                        double ltp = data.getAsJsonObject(key).get("last_price").getAsDouble();
                        ltpMap.put(symbol, ltp);
                    }
                }
            }
        }

        return ltpMap;
    }

    /**
     * Get full quote (OHLC, volume, etc.) for symbols
     */
    public Map<String, JsonObject> getQuote(List<String> symbols) throws IOException {
        Map<String, JsonObject> quoteMap = new HashMap<>();

        StringBuilder instrumentsParam = new StringBuilder();
        for (String symbol : symbols) {
            if (instrumentsParam.length() > 0) {
                instrumentsParam.append("&");
            }
            instrumentsParam.append("i=").append(EXCHANGE_NSE).append(":").append(symbol);
        }

        String url = ZerodhaConfig.BASE_URL + "/quote?" + instrumentsParam;

        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "token " + config.getApiKey() + ":" + config.getAccessToken())
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch quote: " + response.code());
            }

            String body = response.body().string();
            JsonObject json = gson.fromJson(body, JsonObject.class);

            if (json.has("data")) {
                JsonObject data = json.getAsJsonObject("data");

                for (String symbol : symbols) {
                    String key = EXCHANGE_NSE + ":" + symbol;
                    if (data.has(key)) {
                        quoteMap.put(symbol, data.getAsJsonObject(key));
                    }
                }
            }
        }

        return quoteMap;
    }

    /**
     * Parse interval string to minutes
     */
    private int parseInterval(String interval) {
        return switch (interval) {
            case "minute" -> 1;
            case "3minute" -> 3;
            case "5minute" -> 5;
            case "10minute" -> 10;
            case "15minute" -> 15;
            case "30minute" -> 30;
            case "60minute" -> 60;
            default -> 5;
        };
    }

    /**
     * SHA256 hash for checksum
     */
    private String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Check if API is ready
     */
    public boolean isReady() {
        return config.isConfigured() && config.hasAccessToken();
    }

    /**
     * Get login URL for authentication
     */
    public String getLoginUrl() {
        return config.getLoginUrl();
    }
}

