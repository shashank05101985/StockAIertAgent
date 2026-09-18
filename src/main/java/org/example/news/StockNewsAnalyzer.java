package org.example.news;

import org.example.model.StockSignal;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Stock News Analyzer
 *
 * Integrates news sentiment analysis with stock technical analysis
 * to provide enhanced trading signals
 */
public class StockNewsAnalyzer {

    private final NewsFetcher newsFetcher;
    private final SentimentAnalyzer sentimentAnalyzer;

    // News sentiment weight in overall score (0-1)
    private double newsWeight = 0.15;  // 15% weight

    // Cache for sentiment summaries
    private Map<String, NewsFetcher.NewsSentimentSummary> sentimentCache = new HashMap<>();

    public StockNewsAnalyzer() {
        this.newsFetcher = new NewsFetcher();
        this.sentimentAnalyzer = new SentimentAnalyzer();
    }

    /**
     * Set NewsAPI key for fetching news
     */
    public void setNewsApiKey(String apiKey) {
        newsFetcher.setNewsApiKey(apiKey);
    }

    /**
     * Analyze news sentiment for a symbol
     */
    public NewsFetcher.NewsSentimentSummary analyzeSentiment(String symbol) {
        NewsFetcher.NewsSentimentSummary summary = newsFetcher.getSentimentSummary(symbol);
        sentimentCache.put(symbol, summary);
        return summary;
    }

    /**
     * Get cached sentiment or fetch new
     */
    public NewsFetcher.NewsSentimentSummary getSentiment(String symbol) {
        if (sentimentCache.containsKey(symbol)) {
            return sentimentCache.get(symbol);
        }
        return analyzeSentiment(symbol);
    }

    /**
     * Enhance stock signal with news sentiment
     */
    public void enhanceSignalWithNews(StockSignal signal) {
        String symbol = signal.getSymbol();
        NewsFetcher.NewsSentimentSummary sentiment = getSentiment(symbol);

        if (sentiment != null && sentiment.totalArticles > 0) {
            // Get news score adjustment (-15 to +15)
            int newsScore = sentiment.getNewsScore();

            // Apply to existing score with weight
            int adjustedScore = (int) Math.round(
                signal.getScore() * (1 - newsWeight) +
                (signal.getScore() + newsScore) * newsWeight
            );

            // Ensure within bounds
            adjustedScore = Math.max(0, Math.min(100, adjustedScore));

            // Update signal
            signal.setScore(adjustedScore);

            // Add news info to reason
            String newsInfo = String.format(" News: %s (%d articles).",
                sentiment.getSentimentLabel(), sentiment.totalArticles);
            signal.setReason(signal.getReason() + newsInfo);
        }
    }

    /**
     * Enhance multiple signals with news
     */
    public void enhanceSignalsWithNews(List<StockSignal> signals) {
        // Fetch news for all symbols in parallel
        List<String> symbols = signals.stream()
            .map(StockSignal::getSymbol)
            .collect(Collectors.toList());

        Map<String, List<NewsArticle>> newsMap = newsFetcher.fetchNewsForSymbols(symbols);

        // Calculate and cache sentiment summaries
        for (Map.Entry<String, List<NewsArticle>> entry : newsMap.entrySet()) {
            NewsFetcher.NewsSentimentSummary summary =
                newsFetcher.calculateSentimentSummary(entry.getKey(), entry.getValue());
            sentimentCache.put(entry.getKey(), summary);
        }

        // Enhance each signal
        for (StockSignal signal : signals) {
            enhanceSignalWithNews(signal);
        }
    }

    /**
     * Get stocks with most positive news
     */
    public List<String> getPositiveNewsStocks(List<String> symbols, int topN) {
        Map<String, List<NewsArticle>> newsMap = newsFetcher.fetchNewsForSymbols(symbols);

        List<Map.Entry<String, Double>> scoredSymbols = new ArrayList<>();

        for (Map.Entry<String, List<NewsArticle>> entry : newsMap.entrySet()) {
            NewsFetcher.NewsSentimentSummary summary =
                newsFetcher.calculateSentimentSummary(entry.getKey(), entry.getValue());
            scoredSymbols.add(Map.entry(entry.getKey(), summary.aggregateScore));
        }

        // Sort by score descending
        scoredSymbols.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        return scoredSymbols.stream()
            .limit(topN)
            .filter(e -> e.getValue() > 0)  // Only positive
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    /**
     * Get stocks with most negative news (to avoid)
     */
    public List<String> getNegativeNewsStocks(List<String> symbols, int topN) {
        Map<String, List<NewsArticle>> newsMap = newsFetcher.fetchNewsForSymbols(symbols);

        List<Map.Entry<String, Double>> scoredSymbols = new ArrayList<>();

        for (Map.Entry<String, List<NewsArticle>> entry : newsMap.entrySet()) {
            NewsFetcher.NewsSentimentSummary summary =
                newsFetcher.calculateSentimentSummary(entry.getKey(), entry.getValue());
            scoredSymbols.add(Map.entry(entry.getKey(), summary.aggregateScore));
        }

        // Sort by score ascending (most negative first)
        scoredSymbols.sort(Comparator.comparingDouble(Map.Entry::getValue));

        return scoredSymbols.stream()
            .limit(topN)
            .filter(e -> e.getValue() < 0)  // Only negative
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    /**
     * Print news summary for a symbol
     */
    public void printNewsSummary(String symbol) {
        NewsFetcher.NewsSentimentSummary summary = getSentiment(symbol);

        System.out.println("\n" + "=".repeat(70));
        System.out.println("NEWS SENTIMENT: " + symbol);
        System.out.println("=".repeat(70));

        if (summary.totalArticles == 0) {
            System.out.println("No recent news found for " + symbol);
            return;
        }

        System.out.println(summary);
        System.out.println("\n--- Recent Headlines ---");

        for (NewsArticle article : summary.articles.subList(0, Math.min(5, summary.articles.size()))) {
            System.out.println(article);
        }
    }

    /**
     * Print news summary for multiple symbols
     */
    public void printNewsSummaryTable(List<String> symbols) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("NEWS SENTIMENT SUMMARY");
        System.out.println("=".repeat(70));
        System.out.printf("%-12s | %-15s | %6s | %4s | %4s | %4s\n",
            "Symbol", "Sentiment", "Score", "Pos", "Neg", "Neu");
        System.out.println("-".repeat(70));

        Map<String, List<NewsArticle>> newsMap = newsFetcher.fetchNewsForSymbols(symbols);

        for (String symbol : symbols) {
            List<NewsArticle> articles = newsMap.getOrDefault(symbol, new ArrayList<>());
            NewsFetcher.NewsSentimentSummary summary =
                newsFetcher.calculateSentimentSummary(symbol, articles);

            System.out.printf("%-12s | %-15s | %+5.2f | %4d | %4d | %4d\n",
                symbol,
                summary.getSentimentLabel(),
                summary.aggregateScore,
                summary.positiveCount,
                summary.negativeCount,
                summary.neutralCount);
        }
    }

    /**
     * Set news weight in overall scoring
     */
    public void setNewsWeight(double weight) {
        this.newsWeight = Math.max(0, Math.min(0.3, weight)); // Max 30%
    }

    /**
     * Clear sentiment cache
     */
    public void clearCache() {
        sentimentCache.clear();
        newsFetcher.clearAllCache();
    }

    /**
     * Shutdown resources
     */
    public void shutdown() {
        newsFetcher.shutdown();
    }

    /**
     * Create sample news data for testing
     */
    public void addSampleNews(String symbol, List<NewsArticle> articles) {
        for (NewsArticle article : articles) {
            sentimentAnalyzer.analyzeArticle(article);
        }
        NewsFetcher.NewsSentimentSummary summary =
            newsFetcher.calculateSentimentSummary(symbol, articles);
        sentimentCache.put(symbol, summary);
    }

    /**
     * Create sample news for demo
     */
    public static List<NewsArticle> createSampleNews(String symbol) {
        List<NewsArticle> articles = new ArrayList<>();
        Random random = new Random(symbol.hashCode());

        String[] positiveHeadlines = {
            symbol + " reports strong quarterly earnings, beats estimates",
            symbol + " stock surges on massive order win",
            symbol + " announces dividend, shares rally",
            "Analysts upgrade " + symbol + " to strong buy",
            symbol + " expansion plans boost investor confidence",
            symbol + " profit jumps 25% on robust demand"
        };

        String[] negativeHeadlines = {
            symbol + " shares fall on weak guidance",
            symbol + " misses profit estimates, stock tumbles",
            "Concerns over " + symbol + " debt levels",
            symbol + " faces regulatory probe",
            symbol + " announces layoffs amid slowdown",
            "Analysts downgrade " + symbol + " citing risks"
        };

        String[] neutralHeadlines = {
            symbol + " to announce results next week",
            symbol + " trading flat ahead of earnings",
            symbol + " in talks for potential deal",
            symbol + " board meeting scheduled"
        };

        // Add mix of headlines
        int numPositive = random.nextInt(3) + 1;
        int numNegative = random.nextInt(2);
        int numNeutral = random.nextInt(2);

        for (int i = 0; i < numPositive && i < positiveHeadlines.length; i++) {
            NewsArticle article = new NewsArticle(symbol,
                positiveHeadlines[random.nextInt(positiveHeadlines.length)],
                NewsSource.values()[random.nextInt(NewsSource.values().length)]);
            articles.add(article);
        }

        for (int i = 0; i < numNegative && i < negativeHeadlines.length; i++) {
            NewsArticle article = new NewsArticle(symbol,
                negativeHeadlines[random.nextInt(negativeHeadlines.length)],
                NewsSource.values()[random.nextInt(NewsSource.values().length)]);
            articles.add(article);
        }

        for (int i = 0; i < numNeutral && i < neutralHeadlines.length; i++) {
            NewsArticle article = new NewsArticle(symbol,
                neutralHeadlines[random.nextInt(neutralHeadlines.length)],
                NewsSource.values()[random.nextInt(NewsSource.values().length)]);
            articles.add(article);
        }

        return articles;
    }
}

