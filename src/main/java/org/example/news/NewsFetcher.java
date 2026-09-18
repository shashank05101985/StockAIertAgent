package org.example.news;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * News Fetcher - Fetches stock market news from multiple sources
 *
 * Supports:
 * - RSS feeds
 * - News APIs (Google News, NewsAPI, etc.)
 * - Web scraping (basic)
 */
public class NewsFetcher {

    private final SentimentAnalyzer sentimentAnalyzer;
    private final Gson gson;
    private final ExecutorService executor;

    // Cache for news articles
    private Map<String, List<NewsArticle>> newsCache = new ConcurrentHashMap<>();
    private Map<String, LocalDateTime> lastFetchTime = new ConcurrentHashMap<>();

    // Configuration
    private int cacheExpiryMinutes = 15;
    private int maxArticlesPerSymbol = 10;

    // API Keys (set via configuration)
    private String newsApiKey;  // For newsapi.org
    private String googleApiKey; // For Google Custom Search

    public NewsFetcher() {
        this.sentimentAnalyzer = new SentimentAnalyzer();
        this.gson = new Gson();
        this.executor = Executors.newFixedThreadPool(5);
    }

    /**
     * Set NewsAPI key
     */
    public void setNewsApiKey(String apiKey) {
        this.newsApiKey = apiKey;
    }

    /**
     * Fetch news for a single symbol
     */
    public List<NewsArticle> fetchNews(String symbol) {
        // Check cache first
        if (isCacheValid(symbol)) {
            return newsCache.get(symbol);
        }

        List<NewsArticle> allArticles = new ArrayList<>();
        List<Future<List<NewsArticle>>> futures = new ArrayList<>();

        // Fetch from multiple sources in parallel
        futures.add(executor.submit(() -> fetchFromGoogleNews(symbol)));

        if (newsApiKey != null && !newsApiKey.isEmpty()) {
            futures.add(executor.submit(() -> fetchFromNewsApi(symbol)));
        }

        // Collect results
        for (Future<List<NewsArticle>> future : futures) {
            try {
                List<NewsArticle> articles = future.get(10, TimeUnit.SECONDS);
                if (articles != null) {
                    allArticles.addAll(articles);
                }
            } catch (Exception e) {
                System.err.println("Error fetching news: " + e.getMessage());
            }
        }

        // Analyze sentiment for each article
        for (NewsArticle article : allArticles) {
            sentimentAnalyzer.analyzeArticle(article);
        }

        // Sort by publish time (newest first)
        allArticles.sort((a, b) -> b.getPublishedAt().compareTo(a.getPublishedAt()));

        // Limit articles
        if (allArticles.size() > maxArticlesPerSymbol) {
            allArticles = allArticles.subList(0, maxArticlesPerSymbol);
        }

        // Update cache
        newsCache.put(symbol, allArticles);
        lastFetchTime.put(symbol, LocalDateTime.now());

        return allArticles;
    }

    /**
     * Fetch news for multiple symbols
     */
    public Map<String, List<NewsArticle>> fetchNewsForSymbols(List<String> symbols) {
        Map<String, List<NewsArticle>> results = new ConcurrentHashMap<>();

        List<Future<?>> futures = new ArrayList<>();

        for (String symbol : symbols) {
            futures.add(executor.submit(() -> {
                List<NewsArticle> articles = fetchNews(symbol);
                results.put(symbol, articles);
            }));
        }

        // Wait for all to complete
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Continue with other symbols
            }
        }

        return results;
    }

    /**
     * Fetch from Google News RSS
     */
    private List<NewsArticle> fetchFromGoogleNews(String symbol) {
        List<NewsArticle> articles = new ArrayList<>();

        try {
            // Use company name variations for better results
            String query = URLEncoder.encode(symbol + " stock NSE", StandardCharsets.UTF_8);
            String url = "https://news.google.com/rss/search?q=" + query + "&hl=en-IN&gl=IN&ceid=IN:en";

            String rssContent = fetchUrl(url);
            if (rssContent != null) {
                articles = parseRssFeed(rssContent, symbol, NewsSource.REUTERS); // Generic source
            }
        } catch (Exception e) {
            System.err.println("Error fetching Google News for " + symbol + ": " + e.getMessage());
        }

        return articles;
    }

    /**
     * Fetch from NewsAPI.org
     */
    private List<NewsArticle> fetchFromNewsApi(String symbol) {
        List<NewsArticle> articles = new ArrayList<>();

        if (newsApiKey == null || newsApiKey.isEmpty()) {
            return articles;
        }

        try {
            String query = URLEncoder.encode(symbol + " stock", StandardCharsets.UTF_8);
            String url = String.format(
                "https://newsapi.org/v2/everything?q=%s&language=en&sortBy=publishedAt&pageSize=10&apiKey=%s",
                query, newsApiKey);

            String jsonResponse = fetchUrl(url);
            if (jsonResponse != null) {
                JsonObject json = gson.fromJson(jsonResponse, JsonObject.class);

                if (json.has("articles")) {
                    JsonArray articlesArray = json.getAsJsonArray("articles");

                    for (int i = 0; i < articlesArray.size(); i++) {
                        JsonObject articleJson = articlesArray.get(i).getAsJsonObject();

                        NewsArticle article = new NewsArticle();
                        article.setSymbol(symbol);
                        article.setHeadline(getJsonString(articleJson, "title"));
                        article.setSummary(getJsonString(articleJson, "description"));
                        article.setUrl(getJsonString(articleJson, "url"));
                        article.setSource(determineSource(getJsonString(articleJson, "source.name")));
                        article.setPublishedAt(LocalDateTime.now()); // Parse actual date if needed

                        if (article.getHeadline() != null && !article.getHeadline().isEmpty()) {
                            articles.add(article);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching NewsAPI for " + symbol + ": " + e.getMessage());
        }

        return articles;
    }

    /**
     * Parse RSS feed to extract articles
     */
    private List<NewsArticle> parseRssFeed(String rssContent, String symbol, NewsSource defaultSource) {
        List<NewsArticle> articles = new ArrayList<>();

        // Simple XML parsing (for production, use proper XML parser)
        String[] items = rssContent.split("<item>");

        for (int i = 1; i < items.length && i <= maxArticlesPerSymbol; i++) {
            try {
                String item = items[i];

                String title = extractXmlTag(item, "title");
                String link = extractXmlTag(item, "link");
                String pubDate = extractXmlTag(item, "pubDate");
                String description = extractXmlTag(item, "description");

                if (title != null && !title.isEmpty()) {
                    NewsArticle article = new NewsArticle();
                    article.setSymbol(symbol);
                    article.setHeadline(cleanHtml(title));
                    article.setSummary(cleanHtml(description));
                    article.setUrl(link);
                    article.setSource(determineSource(title + " " + (description != null ? description : "")));
                    article.setPublishedAt(LocalDateTime.now()); // Parse pubDate if needed

                    articles.add(article);
                }
            } catch (Exception e) {
                // Skip malformed items
            }
        }

        return articles;
    }

    /**
     * Extract content between XML tags
     */
    private String extractXmlTag(String content, String tag) {
        String startTag = "<" + tag + ">";
        String endTag = "</" + tag + ">";

        int startIdx = content.indexOf(startTag);
        int endIdx = content.indexOf(endTag);

        if (startIdx >= 0 && endIdx > startIdx) {
            String value = content.substring(startIdx + startTag.length(), endIdx);
            // Handle CDATA
            if (value.startsWith("<![CDATA[")) {
                value = value.substring(9);
                if (value.endsWith("]]>")) {
                    value = value.substring(0, value.length() - 3);
                }
            }
            return value.trim();
        }

        return null;
    }

    /**
     * Clean HTML tags from text
     */
    private String cleanHtml(String text) {
        if (text == null) return null;
        return text.replaceAll("<[^>]+>", "").replaceAll("&nbsp;", " ").trim();
    }

    /**
     * Determine news source from URL or content
     */
    private NewsSource determineSource(String content) {
        if (content == null) return NewsSource.REUTERS;

        String lower = content.toLowerCase();

        if (lower.contains("moneycontrol")) return NewsSource.MONEYCONTROL;
        if (lower.contains("mint") || lower.contains("livemint")) return NewsSource.MINT;
        if (lower.contains("cnbc")) return NewsSource.CNBC;
        if (lower.contains("business-standard") || lower.contains("business standard")) return NewsSource.BUSINESS_STANDARD;
        if (lower.contains("economictimes") || lower.contains("economic times")) return NewsSource.ECONOMIC_TIMES;
        if (lower.contains("reuters")) return NewsSource.REUTERS;
        if (lower.contains("bloomberg")) return NewsSource.BLOOMBERG;
        if (lower.contains("nse") || lower.contains("nseindia")) return NewsSource.NSE;
        if (lower.contains("bse") || lower.contains("bseindia")) return NewsSource.BSE;

        return NewsSource.REUTERS; // Default
    }

    /**
     * Fetch URL content
     */
    private String fetchUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    return reader.lines().collect(Collectors.joining("\n"));
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching URL: " + e.getMessage());
        }

        return null;
    }

    /**
     * Get JSON string value safely
     */
    private String getJsonString(JsonObject json, String path) {
        try {
            String[] parts = path.split("\\.");
            JsonObject current = json;

            for (int i = 0; i < parts.length - 1; i++) {
                current = current.getAsJsonObject(parts[i]);
            }

            return current.get(parts[parts.length - 1]).getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if cache is valid
     */
    private boolean isCacheValid(String symbol) {
        if (!newsCache.containsKey(symbol) || !lastFetchTime.containsKey(symbol)) {
            return false;
        }

        LocalDateTime fetchTime = lastFetchTime.get(symbol);
        return fetchTime.plusMinutes(cacheExpiryMinutes).isAfter(LocalDateTime.now());
    }

    /**
     * Clear cache for a symbol
     */
    public void clearCache(String symbol) {
        newsCache.remove(symbol);
        lastFetchTime.remove(symbol);
    }

    /**
     * Clear all cache
     */
    public void clearAllCache() {
        newsCache.clear();
        lastFetchTime.clear();
    }

    /**
     * Get aggregate sentiment for a symbol
     */
    public NewsSentimentSummary getSentimentSummary(String symbol) {
        List<NewsArticle> articles = fetchNews(symbol);
        return calculateSentimentSummary(symbol, articles);
    }

    /**
     * Calculate sentiment summary from articles
     */
    public NewsSentimentSummary calculateSentimentSummary(String symbol, List<NewsArticle> articles) {
        if (articles == null || articles.isEmpty()) {
            return new NewsSentimentSummary(symbol);
        }

        NewsSentimentSummary summary = new NewsSentimentSummary(symbol);

        double totalWeightedScore = 0;
        double totalWeight = 0;

        for (NewsArticle article : articles) {
            double weight = article.getSource().getReliabilityWeight() * article.getConfidence();

            // More weight to recent articles
            if (article.isRecent(6)) {
                weight *= 1.5;
            } else if (article.isRecent(24)) {
                weight *= 1.2;
            }

            totalWeightedScore += article.getSentimentScore() * weight;
            totalWeight += weight;

            // Count by sentiment
            switch (article.getSentiment()) {
                case VERY_POSITIVE:
                case POSITIVE:
                    summary.positiveCount++;
                    break;
                case VERY_NEGATIVE:
                case NEGATIVE:
                    summary.negativeCount++;
                    break;
                default:
                    summary.neutralCount++;
            }
        }

        summary.totalArticles = articles.size();
        summary.aggregateScore = totalWeight > 0 ? totalWeightedScore / totalWeight : 0;
        summary.articles = articles;

        return summary;
    }

    /**
     * Shutdown executor
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * News sentiment summary for a symbol
     */
    public static class NewsSentimentSummary {
        public String symbol;
        public int totalArticles;
        public int positiveCount;
        public int negativeCount;
        public int neutralCount;
        public double aggregateScore;  // -1 to +1
        public List<NewsArticle> articles;

        public NewsSentimentSummary(String symbol) {
            this.symbol = symbol;
            this.articles = new ArrayList<>();
        }

        public int getNewsScore() {
            // Convert -1 to +1 score to points for stock scoring
            // Range: -15 to +15 points
            return (int) Math.round(aggregateScore * 15);
        }

        public String getSentimentLabel() {
            if (aggregateScore >= 0.4) return "Very Bullish 🟢🟢";
            if (aggregateScore >= 0.15) return "Bullish 🟢";
            if (aggregateScore <= -0.4) return "Very Bearish 🔴🔴";
            if (aggregateScore <= -0.15) return "Bearish 🔴";
            return "Neutral ⚪";
        }

        @Override
        public String toString() {
            return String.format(
                "%s News Sentiment: %s (Score: %.2f)\n" +
                "  Articles: %d total | %d positive | %d negative | %d neutral",
                symbol, getSentimentLabel(), aggregateScore,
                totalArticles, positiveCount, negativeCount, neutralCount);
        }
    }
}

