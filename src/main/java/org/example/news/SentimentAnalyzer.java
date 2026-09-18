package org.example.news;

import org.example.news.NewsArticle.Sentiment;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Sentiment Analyzer for Stock Market News
 *
 * Analyzes headlines and article text to determine positive/negative sentiment
 * Uses keyword-based analysis with market-specific vocabulary
 */
public class SentimentAnalyzer {

    // Positive keywords with weights
    private static final Map<String, Double> POSITIVE_KEYWORDS = new HashMap<>();

    // Negative keywords with weights
    private static final Map<String, Double> NEGATIVE_KEYWORDS = new HashMap<>();

    // Neutral/Noise words to ignore
    private static final Set<String> STOP_WORDS = new HashSet<>();

    // Intensifiers that amplify sentiment
    private static final Map<String, Double> INTENSIFIERS = new HashMap<>();

    // Negation words that flip sentiment
    private static final Set<String> NEGATIONS = new HashSet<>();

    static {
        initializeKeywords();
    }

    private static void initializeKeywords() {
        // === VERY POSITIVE (2.0) ===
        POSITIVE_KEYWORDS.put("surge", 2.0);
        POSITIVE_KEYWORDS.put("soar", 2.0);
        POSITIVE_KEYWORDS.put("skyrocket", 2.0);
        POSITIVE_KEYWORDS.put("breakthrough", 2.0);
        POSITIVE_KEYWORDS.put("blockbuster", 2.0);
        POSITIVE_KEYWORDS.put("record high", 2.0);
        POSITIVE_KEYWORDS.put("all-time high", 2.0);
        POSITIVE_KEYWORDS.put("massive growth", 2.0);
        POSITIVE_KEYWORDS.put("exceptional", 2.0);
        POSITIVE_KEYWORDS.put("outperform", 2.0);
        POSITIVE_KEYWORDS.put("beat estimates", 2.0);
        POSITIVE_KEYWORDS.put("exceeds expectations", 2.0);

        // === POSITIVE (1.0-1.5) ===
        POSITIVE_KEYWORDS.put("rise", 1.2);
        POSITIVE_KEYWORDS.put("rises", 1.2);
        POSITIVE_KEYWORDS.put("gain", 1.2);
        POSITIVE_KEYWORDS.put("gains", 1.2);
        POSITIVE_KEYWORDS.put("growth", 1.3);
        POSITIVE_KEYWORDS.put("profit", 1.5);
        POSITIVE_KEYWORDS.put("profitable", 1.5);
        POSITIVE_KEYWORDS.put("bullish", 1.5);
        POSITIVE_KEYWORDS.put("upgrade", 1.5);
        POSITIVE_KEYWORDS.put("buy", 1.3);
        POSITIVE_KEYWORDS.put("strong buy", 1.8);
        POSITIVE_KEYWORDS.put("outperformer", 1.5);
        POSITIVE_KEYWORDS.put("rally", 1.5);
        POSITIVE_KEYWORDS.put("rallies", 1.5);
        POSITIVE_KEYWORDS.put("recovery", 1.3);
        POSITIVE_KEYWORDS.put("rebound", 1.3);
        POSITIVE_KEYWORDS.put("bounce", 1.2);
        POSITIVE_KEYWORDS.put("up", 1.0);
        POSITIVE_KEYWORDS.put("higher", 1.2);
        POSITIVE_KEYWORDS.put("increase", 1.2);
        POSITIVE_KEYWORDS.put("positive", 1.3);
        POSITIVE_KEYWORDS.put("optimistic", 1.3);
        POSITIVE_KEYWORDS.put("beat", 1.4);
        POSITIVE_KEYWORDS.put("beats", 1.4);
        POSITIVE_KEYWORDS.put("expansion", 1.3);
        POSITIVE_KEYWORDS.put("dividend", 1.2);
        POSITIVE_KEYWORDS.put("bonus", 1.3);
        POSITIVE_KEYWORDS.put("buyback", 1.4);
        POSITIVE_KEYWORDS.put("acquisition", 1.2);
        POSITIVE_KEYWORDS.put("partnership", 1.2);
        POSITIVE_KEYWORDS.put("deal", 1.1);
        POSITIVE_KEYWORDS.put("contract", 1.2);
        POSITIVE_KEYWORDS.put("order", 1.1);
        POSITIVE_KEYWORDS.put("orders", 1.1);
        POSITIVE_KEYWORDS.put("approval", 1.3);
        POSITIVE_KEYWORDS.put("approved", 1.3);
        POSITIVE_KEYWORDS.put("launch", 1.1);
        POSITIVE_KEYWORDS.put("launches", 1.1);
        POSITIVE_KEYWORDS.put("success", 1.4);
        POSITIVE_KEYWORDS.put("successful", 1.4);
        POSITIVE_KEYWORDS.put("strong", 1.2);
        POSITIVE_KEYWORDS.put("robust", 1.3);
        POSITIVE_KEYWORDS.put("healthy", 1.2);
        POSITIVE_KEYWORDS.put("momentum", 1.2);
        POSITIVE_KEYWORDS.put("breakout", 1.5);
        POSITIVE_KEYWORDS.put("accumulation", 1.4);

        // === VERY NEGATIVE (-2.0) ===
        NEGATIVE_KEYWORDS.put("crash", 2.0);
        NEGATIVE_KEYWORDS.put("crashes", 2.0);
        NEGATIVE_KEYWORDS.put("plunge", 2.0);
        NEGATIVE_KEYWORDS.put("plunges", 2.0);
        NEGATIVE_KEYWORDS.put("collapse", 2.0);
        NEGATIVE_KEYWORDS.put("tank", 2.0);
        NEGATIVE_KEYWORDS.put("tanks", 2.0);
        NEGATIVE_KEYWORDS.put("fraud", 2.0);
        NEGATIVE_KEYWORDS.put("scam", 2.0);
        NEGATIVE_KEYWORDS.put("scandal", 2.0);
        NEGATIVE_KEYWORDS.put("bankruptcy", 2.0);
        NEGATIVE_KEYWORDS.put("bankrupt", 2.0);
        NEGATIVE_KEYWORDS.put("default", 2.0);
        NEGATIVE_KEYWORDS.put("crisis", 1.8);
        NEGATIVE_KEYWORDS.put("disaster", 2.0);

        // === NEGATIVE (-1.0 to -1.5) ===
        NEGATIVE_KEYWORDS.put("fall", 1.2);
        NEGATIVE_KEYWORDS.put("falls", 1.2);
        NEGATIVE_KEYWORDS.put("drop", 1.2);
        NEGATIVE_KEYWORDS.put("drops", 1.2);
        NEGATIVE_KEYWORDS.put("decline", 1.3);
        NEGATIVE_KEYWORDS.put("declines", 1.3);
        NEGATIVE_KEYWORDS.put("loss", 1.5);
        NEGATIVE_KEYWORDS.put("losses", 1.5);
        NEGATIVE_KEYWORDS.put("bearish", 1.5);
        NEGATIVE_KEYWORDS.put("downgrade", 1.5);
        NEGATIVE_KEYWORDS.put("sell", 1.3);
        NEGATIVE_KEYWORDS.put("underperform", 1.4);
        NEGATIVE_KEYWORDS.put("weak", 1.3);
        NEGATIVE_KEYWORDS.put("weakness", 1.3);
        NEGATIVE_KEYWORDS.put("down", 1.0);
        NEGATIVE_KEYWORDS.put("lower", 1.2);
        NEGATIVE_KEYWORDS.put("decrease", 1.2);
        NEGATIVE_KEYWORDS.put("negative", 1.3);
        NEGATIVE_KEYWORDS.put("pessimistic", 1.3);
        NEGATIVE_KEYWORDS.put("miss", 1.4);
        NEGATIVE_KEYWORDS.put("misses", 1.4);
        NEGATIVE_KEYWORDS.put("missed", 1.4);
        NEGATIVE_KEYWORDS.put("disappointing", 1.5);
        NEGATIVE_KEYWORDS.put("disappoints", 1.5);
        NEGATIVE_KEYWORDS.put("concern", 1.2);
        NEGATIVE_KEYWORDS.put("concerns", 1.2);
        NEGATIVE_KEYWORDS.put("risk", 1.1);
        NEGATIVE_KEYWORDS.put("risks", 1.1);
        NEGATIVE_KEYWORDS.put("warning", 1.4);
        NEGATIVE_KEYWORDS.put("caution", 1.2);
        NEGATIVE_KEYWORDS.put("volatile", 1.2);
        NEGATIVE_KEYWORDS.put("volatility", 1.2);
        NEGATIVE_KEYWORDS.put("uncertainty", 1.3);
        NEGATIVE_KEYWORDS.put("slowdown", 1.4);
        NEGATIVE_KEYWORDS.put("slump", 1.5);
        NEGATIVE_KEYWORDS.put("slumps", 1.5);
        NEGATIVE_KEYWORDS.put("tumble", 1.4);
        NEGATIVE_KEYWORDS.put("tumbles", 1.4);
        NEGATIVE_KEYWORDS.put("slide", 1.2);
        NEGATIVE_KEYWORDS.put("slides", 1.2);
        NEGATIVE_KEYWORDS.put("pressure", 1.1);
        NEGATIVE_KEYWORDS.put("layoff", 1.4);
        NEGATIVE_KEYWORDS.put("layoffs", 1.4);
        NEGATIVE_KEYWORDS.put("cut", 1.1);
        NEGATIVE_KEYWORDS.put("cuts", 1.1);
        NEGATIVE_KEYWORDS.put("debt", 1.2);
        NEGATIVE_KEYWORDS.put("lawsuit", 1.3);
        NEGATIVE_KEYWORDS.put("investigation", 1.3);
        NEGATIVE_KEYWORDS.put("probe", 1.3);
        NEGATIVE_KEYWORDS.put("penalty", 1.4);
        NEGATIVE_KEYWORDS.put("fine", 1.3);
        NEGATIVE_KEYWORDS.put("violation", 1.3);
        NEGATIVE_KEYWORDS.put("breakdown", 1.3);
        NEGATIVE_KEYWORDS.put("selling", 1.2);
        NEGATIVE_KEYWORDS.put("distribution", 1.1);

        // Intensifiers
        INTENSIFIERS.put("very", 1.5);
        INTENSIFIERS.put("highly", 1.5);
        INTENSIFIERS.put("extremely", 2.0);
        INTENSIFIERS.put("significantly", 1.5);
        INTENSIFIERS.put("substantially", 1.5);
        INTENSIFIERS.put("sharply", 1.8);
        INTENSIFIERS.put("strongly", 1.5);
        INTENSIFIERS.put("massively", 2.0);
        INTENSIFIERS.put("huge", 1.8);
        INTENSIFIERS.put("major", 1.3);
        INTENSIFIERS.put("big", 1.2);

        // Negations
        NEGATIONS.add("not");
        NEGATIONS.add("no");
        NEGATIONS.add("never");
        NEGATIONS.add("neither");
        NEGATIONS.add("nobody");
        NEGATIONS.add("nothing");
        NEGATIONS.add("nowhere");
        NEGATIONS.add("hardly");
        NEGATIONS.add("barely");
        NEGATIONS.add("doesn't");
        NEGATIONS.add("don't");
        NEGATIONS.add("didn't");
        NEGATIONS.add("won't");
        NEGATIONS.add("wouldn't");
        NEGATIONS.add("couldn't");
        NEGATIONS.add("shouldn't");
        NEGATIONS.add("isn't");
        NEGATIONS.add("aren't");
        NEGATIONS.add("wasn't");
        NEGATIONS.add("weren't");
        NEGATIONS.add("haven't");
        NEGATIONS.add("hasn't");
        NEGATIONS.add("hadn't");
        NEGATIONS.add("without");
        NEGATIONS.add("lack");
        NEGATIONS.add("lacking");
        NEGATIONS.add("fails");
        NEGATIONS.add("failed");
        NEGATIONS.add("unable");

        // Stop words
        STOP_WORDS.addAll(Arrays.asList(
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "must", "shall", "can", "need", "dare",
            "ought", "used", "to", "of", "in", "for", "on", "with", "at", "by",
            "from", "as", "into", "through", "during", "before", "after",
            "above", "below", "between", "under", "again", "further", "then",
            "once", "here", "there", "when", "where", "why", "how", "all", "each",
            "few", "more", "most", "other", "some", "such", "only", "own", "same",
            "so", "than", "too", "very", "just", "also", "now", "today", "said",
            "says", "according", "report", "reports", "reported", "news"
        ));
    }

    /**
     * Analyze sentiment of a news headline
     */
    public SentimentResult analyzeHeadline(String headline) {
        return analyze(headline, 1.2); // Headlines have higher weight
    }

    /**
     * Analyze sentiment of article text
     */
    public SentimentResult analyzeText(String text) {
        return analyze(text, 1.0);
    }

    /**
     * Main sentiment analysis
     */
    public SentimentResult analyze(String text, double textWeight) {
        if (text == null || text.isEmpty()) {
            return new SentimentResult(0, 0, Sentiment.NEUTRAL);
        }

        String normalizedText = text.toLowerCase();
        String[] words = normalizedText.split("\\s+");

        double positiveScore = 0;
        double negativeScore = 0;
        int matchedKeywords = 0;
        boolean negationActive = false;
        double intensifierMultiplier = 1.0;

        for (int i = 0; i < words.length; i++) {
            String word = words[i].replaceAll("[^a-z]", "");

            if (word.isEmpty() || STOP_WORDS.contains(word)) {
                continue;
            }

            // Check for negation
            if (NEGATIONS.contains(word)) {
                negationActive = true;
                continue;
            }

            // Check for intensifier
            if (INTENSIFIERS.containsKey(word)) {
                intensifierMultiplier = INTENSIFIERS.get(word);
                continue;
            }

            // Check positive keywords
            if (POSITIVE_KEYWORDS.containsKey(word)) {
                double score = POSITIVE_KEYWORDS.get(word) * intensifierMultiplier * textWeight;
                if (negationActive) {
                    negativeScore += score; // Negated positive = negative
                } else {
                    positiveScore += score;
                }
                matchedKeywords++;
                negationActive = false;
                intensifierMultiplier = 1.0;
            }

            // Check negative keywords
            else if (NEGATIVE_KEYWORDS.containsKey(word)) {
                double score = NEGATIVE_KEYWORDS.get(word) * intensifierMultiplier * textWeight;
                if (negationActive) {
                    positiveScore += score * 0.5; // Negated negative = mild positive
                } else {
                    negativeScore += score;
                }
                matchedKeywords++;
                negationActive = false;
                intensifierMultiplier = 1.0;
            }

            // Check multi-word phrases
            else if (i < words.length - 1) {
                String phrase = word + " " + words[i + 1].replaceAll("[^a-z]", "");

                if (POSITIVE_KEYWORDS.containsKey(phrase)) {
                    double score = POSITIVE_KEYWORDS.get(phrase) * intensifierMultiplier * textWeight;
                    positiveScore += negationActive ? -score : score;
                    matchedKeywords++;
                    i++; // Skip next word
                } else if (NEGATIVE_KEYWORDS.containsKey(phrase)) {
                    double score = NEGATIVE_KEYWORDS.get(phrase) * intensifierMultiplier * textWeight;
                    negativeScore += negationActive ? -score : score;
                    matchedKeywords++;
                    i++; // Skip next word
                }

                negationActive = false;
                intensifierMultiplier = 1.0;
            }
        }

        // Calculate final score (-1 to +1)
        double totalMagnitude = positiveScore + negativeScore;
        double netScore;

        if (totalMagnitude == 0) {
            netScore = 0;
        } else {
            netScore = (positiveScore - negativeScore) / totalMagnitude;
        }

        // Adjust to -1 to +1 range
        netScore = Math.max(-1, Math.min(1, netScore));

        // Calculate confidence based on matched keywords
        double confidence = Math.min(1.0, matchedKeywords / 5.0);

        // Determine sentiment category
        Sentiment sentiment = categorizeSentiment(netScore);

        return new SentimentResult(netScore, confidence, sentiment);
    }

    /**
     * Categorize numerical score into sentiment enum
     */
    private Sentiment categorizeSentiment(double score) {
        if (score >= 0.6) {
            return Sentiment.VERY_POSITIVE;
        } else if (score >= 0.2) {
            return Sentiment.POSITIVE;
        } else if (score <= -0.6) {
            return Sentiment.VERY_NEGATIVE;
        } else if (score <= -0.2) {
            return Sentiment.NEGATIVE;
        } else {
            return Sentiment.NEUTRAL;
        }
    }

    /**
     * Analyze a news article
     */
    public void analyzeArticle(NewsArticle article) {
        // Analyze headline (higher weight)
        SentimentResult headlineResult = analyzeHeadline(article.getHeadline());

        // Analyze summary if available
        SentimentResult summaryResult = null;
        if (article.getSummary() != null && !article.getSummary().isEmpty()) {
            summaryResult = analyzeText(article.getSummary());
        }

        // Combine scores
        double combinedScore;
        double combinedConfidence;

        if (summaryResult != null) {
            // Weighted average: headline 60%, summary 40%
            combinedScore = headlineResult.score * 0.6 + summaryResult.score * 0.4;
            combinedConfidence = headlineResult.confidence * 0.6 + summaryResult.confidence * 0.4;
        } else {
            combinedScore = headlineResult.score;
            combinedConfidence = headlineResult.confidence;
        }

        article.setSentimentScore(combinedScore);
        article.setConfidence(combinedConfidence);
        article.setSentiment(categorizeSentiment(combinedScore));
    }

    /**
     * Result of sentiment analysis
     */
    public static class SentimentResult {
        public final double score;        // -1.0 to +1.0
        public final double confidence;   // 0.0 to 1.0
        public final Sentiment sentiment;

        public SentimentResult(double score, double confidence, Sentiment sentiment) {
            this.score = score;
            this.confidence = confidence;
            this.sentiment = sentiment;
        }

        @Override
        public String toString() {
            return String.format("%s (score=%.2f, confidence=%.2f)",
                sentiment.getDescription(), score, confidence);
        }
    }
}

