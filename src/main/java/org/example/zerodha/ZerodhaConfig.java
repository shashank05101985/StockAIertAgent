package org.example.zerodha;

import java.io.*;
import java.util.Properties;

/**
 * Zerodha Kite Connect API Configuration
 *
 * Store your credentials in: src/main/resources/zerodha.properties
 *
 * DO NOT commit credentials to version control!
 */
public class ZerodhaConfig {

    private String apiKey;
    private String apiSecret;
    private String accessToken;
    private String userId;

    // API endpoints
    public static final String BASE_URL = "https://api.kite.trade";
    public static final String LOGIN_URL = "https://kite.zerodha.com/connect/login";
    public static final String WS_URL = "wss://ws.kite.trade";

    // Default intervals
    public static final String INTERVAL_MINUTE = "minute";
    public static final String INTERVAL_5MINUTE = "5minute";
    public static final String INTERVAL_15MINUTE = "15minute";
    public static final String INTERVAL_DAY = "day";

    private static ZerodhaConfig instance;

    private ZerodhaConfig() {
        loadConfig();
    }

    public static ZerodhaConfig getInstance() {
        if (instance == null) {
            instance = new ZerodhaConfig();
        }
        return instance;
    }

    /**
     * Load configuration from properties file
     */
    private void loadConfig() {
        Properties props = new Properties();

        // Try to load from file
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("zerodha.properties")) {
            if (is != null) {
                props.load(is);
                this.apiKey = props.getProperty("api.key");
                this.apiSecret = props.getProperty("api.secret");
                this.accessToken = props.getProperty("access.token");
                this.userId = props.getProperty("user.id");
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load zerodha.properties");
        }

        // Override with environment variables if set
        if (System.getenv("ZERODHA_API_KEY") != null) {
            this.apiKey = System.getenv("ZERODHA_API_KEY");
        }
        if (System.getenv("ZERODHA_API_SECRET") != null) {
            this.apiSecret = System.getenv("ZERODHA_API_SECRET");
        }
        if (System.getenv("ZERODHA_ACCESS_TOKEN") != null) {
            this.accessToken = System.getenv("ZERODHA_ACCESS_TOKEN");
        }
        if (System.getenv("ZERODHA_USER_ID") != null) {
            this.userId = System.getenv("ZERODHA_USER_ID");
        }
    }

    /**
     * Set credentials programmatically
     */
    public void setCredentials(String apiKey, String apiSecret) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    // Getters
    public String getApiKey() { return apiKey; }
    public String getApiSecret() { return apiSecret; }
    public String getAccessToken() { return accessToken; }
    public String getUserId() { return userId; }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty() &&
               apiSecret != null && !apiSecret.isEmpty();
    }

    public boolean hasAccessToken() {
        return accessToken != null && !accessToken.isEmpty();
    }

    /**
     * Get login URL for user authentication
     */
    public String getLoginUrl() {
        return LOGIN_URL + "?api_key=" + apiKey + "&v=3";
    }

    /**
     * Save access token to properties file
     */
    public void saveAccessToken(String token) {
        this.accessToken = token;

        Properties props = new Properties();
        props.setProperty("api.key", apiKey);
        props.setProperty("api.secret", apiSecret);
        props.setProperty("access.token", token);
        if (userId != null) {
            props.setProperty("user.id", userId);
        }

        try (OutputStream os = new FileOutputStream("src/main/resources/zerodha.properties")) {
            props.store(os, "Zerodha API Configuration");
        } catch (IOException e) {
            System.err.println("Warning: Could not save access token");
        }
    }

    @Override
    public String toString() {
        return String.format("ZerodhaConfig[apiKey=%s, hasToken=%s, userId=%s]",
            apiKey != null ? apiKey.substring(0, 4) + "****" : "null",
            hasAccessToken(),
            userId);
    }
}

