package org.example.news;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * News article with sentiment analysis
 */
public class NewsArticle {

    public enum Sentiment {
        VERY_POSITIVE(2, "Very Positive", "🟢🟢"),
        POSITIVE(1, "Positive", "🟢"),
        NEUTRAL(0, "Neutral", "⚪"),
        NEGATIVE(-1, "Negative", "🔴"),
        VERY_NEGATIVE(-2, "Very Negative", "🔴🔴");

        private final int score;
        private final String description;
        private final String emoji;

        Sentiment(int score, String description, String emoji) {
            this.score = score;
            this.description = description;
            this.emoji = emoji;
        }

        public int getScore() { return score; }
        public String getDescription() { return description; }
        public String getEmoji() { return emoji; }
    }

    private String symbol;
    private String headline;
    private String summary;
    private String url;
    private NewsSource source;
    private LocalDateTime publishedAt;
    private Sentiment sentiment;
    private double sentimentScore;  // -1.0 to +1.0
    private double confidence;      // 0.0 to 1.0
    private List<String> keywords;

    public NewsArticle() {
        this.keywords = new ArrayList<>();
    }

    public NewsArticle(String symbol, String headline, NewsSource source) {
        this();
        this.symbol = symbol;
        this.headline = headline;
        this.source = source;
        this.publishedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public NewsSource getSource() { return source; }
    public void setSource(NewsSource source) { this.source = source; }

    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }

    public Sentiment getSentiment() { return sentiment; }
    public void setSentiment(Sentiment sentiment) { this.sentiment = sentiment; }

    public double getSentimentScore() { return sentimentScore; }
    public void setSentimentScore(double sentimentScore) { this.sentimentScore = sentimentScore; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }

    /**
     * Get weighted sentiment score (considering source reliability and confidence)
     */
    public double getWeightedScore() {
        return sentimentScore * source.getReliabilityWeight() * confidence;
    }

    /**
     * Check if news is recent (within last N hours)
     */
    public boolean isRecent(int hours) {
        return publishedAt.isAfter(LocalDateTime.now().minusHours(hours));
    }

    @Override
    public String toString() {
        return String.format("%s [%s] %s - %s (%.2f confidence)",
            sentiment.getEmoji(),
            source.getDisplayName(),
            headline,
            sentiment.getDescription(),
            confidence);
    }
}

