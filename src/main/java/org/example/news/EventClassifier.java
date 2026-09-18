package org.example.news;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.collector.PythonAiAnalyzer;
import org.example.model.StockEvent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;

public class EventClassifier {

    public static Classification classify(StockEvent event) throws IOException, InterruptedException {
        try {

            String text = ((event.getTitle() == null ? "" : event.getTitle()) + " " + (event.getDescription() == null ? "" : event.getDescription())).toLowerCase(
                Locale.ROOT);


            double sentiment = calculateSentiment(text);

            String sentimentLabel;

            if (sentiment >= 0.40) {
                sentimentLabel = "POSITIVE";
            } else if (sentiment <= -0.40) {
                sentimentLabel = "NEGATIVE";
            } else {
                sentimentLabel = "NEUTRAL";
            }

            String eventType = event.getEventType();
            double importance = calculateImportance(eventType, sentiment);

            if (event.getSource()!=null) {
                PythonAiAnalyzer analyzer =
                    new PythonAiAnalyzer();

                PythonAiAnalyzer.AiClassification result =
                    analyzer.analyze(
                        event.getSymbol(),
                        event.getEventType(),
                        event.getSourceUrl()
                    );
                return new Classification(eventType, result.sentiment().label(), result.summary(),sentiment, importance);
            }
            return new Classification(eventType, sentimentLabel, event.getDescription(), sentiment, importance);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    private static boolean containsAny(String text, String... words) {

        for (String word : words) {
            if (text.toLowerCase().contains(word)) {
                return true;
            }
        }

        return false;
    }

    private static double calculateSentiment(String text) {

        double score = 0;

        String[] positive = {"major order", "large order", "contract win", "approval", "approved", "acquisition", "expansion", "growth", "profit", "record", "buyback", "dividend increase", "stake increased"};

        String[] negative = {"loss", "decline", "default", "penalty", "fraud", "resignation", "downgrade", "stake sale", "termination", "cancelled", "cancellation", "investigation"};

        for (String word : positive) {
            if (text.contains(word)) {
                score += 0.15;
            }
        }


        for (String word : negative) {
            if (text.contains(word)) {
                score -= 0.20;
            }
        }
        return Math.max(-1.0, Math.min(1.0, score));
    }

    public static double calculateImportance(String eventType, double sentiment) {

        double score;

        switch (eventType) {

            case "ORDER_WIN":
                score = 0.85;
                break;

            case "ACQUISITION":
                score = 0.90;
                break;

            case "REGULATORY_APPROVAL":
                score = 0.85;
                break;

            case "MERGER":
                score = 0.90;
                break;

            case "FUND_RAISE":
                score = 0.70;
                break;

            case "RESULTS":
                score = 0.75;
                break;

            case "BUYBACK":
                score = 0.65;
                break;

            case "DIVIDEND":
                score = 0.50;
                break;

            case "MANAGEMENT_CHANGE":
                score = 0.45;
                break;

            default:
                score = 0.25;
        }

        if (Math.abs(sentiment) > 0.6) {
            score += 0.10;
        }

        return Math.min(1.0, score);
    }

    public static String getEventType(String text) {
        String eventType = "OTHER";

        if (containsAny(text, "order", "orders", "purchase order", "work order", "contract", "bagged order")) {

            eventType = "ORDER_WIN";

        } else if (containsAny(text, "acquisition", "acquire", "acquired")) {

            eventType = "ACQUISITION";

        } else if (containsAny(text, "approval", "approved", "regulatory approval")) {

            eventType = "REGULATORY_APPROVAL";

        } else if (containsAny(text, "merger", "amalgamation")) {

            eventType = "MERGER";

        } else if (containsAny(text, "fund raising", "fund raise", "fundraising", "preferential issue", "rights issue",
            "qip")) {

            eventType = "FUND_RAISE";

        } else if (containsAny(text, "buyback", "buy back")) {

            eventType = "BUYBACK";

        } else if (containsAny(text, "dividend")) {

            eventType = "DIVIDEND";

        } else if (containsAny(text, "financial results", "quarterly results", "results")) {

            eventType = "RESULTS";

        } else if (containsAny(text, "promoter acquisition", "promoter purchase", "stake increased",
            "stake increase")) {

            eventType = "PROMOTER_BUY";

        } else if (containsAny(text, "stake sale", "stake decreased", "stake decrease")) {

            eventType = "STAKE_DECREASE";

        } else if (containsAny(text, "appointment", "resignation", "director")) {

            eventType = "MANAGEMENT_CHANGE";
        }
        if (containsAny(text, "press release")) {

            eventType = "Press release";

        }
        return eventType;
    }

    public static String downloadAndExtract(String documentUrl) throws IOException, InterruptedException {

        if (documentUrl == null || documentUrl.isBlank()) {

            return null;
        }

        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(documentUrl)).header("User-Agent",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " + "AppleWebKit/537.36 (KHTML, like Gecko) " + "Chrome/151.0 Safari/537.36").header(
            "Referer", "https://www.nseindia.com/").header("Accept", "application/pdf,*/*").GET().build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {

            throw new IOException("NSE document HTTP " + response.statusCode() + " : " + documentUrl);
        }

        byte[] data = response.body();

        try (PDDocument document = Loader.loadPDF(data)) {

            PDFTextStripper stripper = new PDFTextStripper();

            stripper.setSortByPosition(true);

            return stripper.getText(document).trim();
        }
    }

    public static Classification analyzeDocument(String documentText, String eventType) {

        if (documentText == null || documentText.isBlank()) {

            return new Classification(eventType, "NEUTRAL", null, 0.0, calculateImportance(eventType, 0.0));
        }

        String text = documentText.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();

        double score;

        switch (eventType == null ? "OTHER" : eventType.toUpperCase(Locale.ROOT)) {

            case "ACQUISITION":
                score = analyzeAcquisition(text);
                break;

            case "ORDER_WIN":
            case "ORDER":
                score = analyzeOrder(text);
                break;

            case "RESULTS":
                score = analyzeResults(text);
                break;

            case "FUND_RAISE":
            case "FUNDRAISING":
                score = analyzeFundRaise(text);
                break;

            case "CREDIT_RATING":
                score = analyzeCreditRating(text);
                break;

            case "BUYBACK":
                score = analyzeBuyback(text);
                break;

            default:
                score = analyzeGeneric(text);
                break;
        }

        score = Math.max(-1.0, Math.min(1.0, score));

        String sentiment;

        if (score >= 0.25) {
            sentiment = "POSITIVE";
        } else if (score <= -0.25) {
            sentiment = "NEGATIVE";
        } else {
            sentiment = "NEUTRAL";
        }

        double importance = calculateImportance(eventType, score);

        String summary = createSummary(text);

        return new Classification(eventType, sentiment, summary, score, importance);
    }

    private static double analyzeAcquisition(String text) {

        double score = 0.0;

        // Company is acquiring another business
        if (containsAny(text, "ACQUIRE", "ACQUISITION", "ACQUIRES", "ACQUIRED")) {

            score += 0.20;
        }

        // Control / meaningful stake
        if (containsAny(text, "CONTROLLING STAKE", "CONTROL", "MAJORITY STAKE", "MAJORITY")) {

            score += 0.20;
        }

        // Strategic rationale
        if (containsAny(text, "STRATEGIC", "STRATEGIC EXPANSION", "DIVERSIFICATION", "EXPAND", "EXPANSION")) {

            score += 0.15;
        }

        // Explicit expected benefits
        if (containsAny(text, "SYNERGY", "SYNERGIES", "VALUE CREATION", "CREATE VALUE", "MARGIN IMPROVEMENT",
            "STRENGTHEN MARGINS", "PROFITABILITY", "GROWTH POTENTIAL")) {

            score += 0.25;
        }

        // Negative acquisition characteristics
        if (containsAny(text, "LOSS MAKING", "DISTRESSED", "DEFAULT", "INSOLVENCY", "IMPAIRMENT", "REGULATORY ISSUE",
            "SIGNIFICANT DEBT")) {

            score -= 0.30;
        }

        // Financing risk
        if (containsAny(text, "DEBT FINANCED", "BORROWING", "HIGH DEBT", "DEBT BURDEN")) {

            score -= 0.15;
        }

        // If acquisition is explicitly described
        // as beneficial / accretive
        if (containsAny(text, "ACCRETIVE", "EPS ACCRETIVE", "POSITIVE IMPACT", "EXPECTED TO BENEFIT")) {

            score += 0.20;
        }

        return score;
    }

    private static double analyzeOrder(String text) {

        double score = 0.0;

        if (containsAny(text, "ORDER RECEIVED", "ORDER WIN", "ORDER WORTH", "CONTRACT AWARDED", "CONTRACT RECEIVED",
            "LETTER OF AWARD", "LOA")) {

            score += 0.40;
        }

        if (containsAny(text, "REPEAT ORDER", "MAJOR ORDER", "LARGE ORDER", "SIGNIFICANT ORDER")) {

            score += 0.20;
        }

        if (containsAny(text, "REVENUE", "TURNOVER", "EXECUTION", "ORDER BOOK")) {

            score += 0.15;
        }

        if (containsAny(text, "ORDER CANCELLED", "ORDER CANCELED", "CONTRACT TERMINATED", "CONTRACT CANCELLED",
            "CONTRACT CANCELED")) {

            score -= 0.50;
        }

        return score;
    }

    private static double analyzeResults(String text) {

        double score = 0.0;

        if (containsAny(text, "PROFIT INCREASE", "PROFIT GREW", "PROFIT GROWTH", "PAT INCREASE", "REVENUE GROWTH",
            "EBITDA GROWTH", "MARGIN IMPROVEMENT")) {

            score += 0.30;
        }

        if (containsAny(text, "PROFIT DECLINE", "PROFIT DECREASE", "LOSS INCREASE", "REVENUE DECLINE", "EBITDA DECLINE",
            "MARGIN DECLINE")) {

            score -= 0.30;
        }

        if (containsAny(text, "RECORD REVENUE", "RECORD PROFIT", "HIGHEST EVER", "ALL TIME HIGH")) {

            score += 0.25;
        }

        return score;
    }

    private static double analyzeCreditRating(String text) {

        double score = 0.0;

        if (containsAny(text, "RATING UPGRADED", "UPGRADE", "POSITIVE OUTLOOK", "IMPROVED RATING")) {

            score += 0.60;
        }

        if (containsAny(text, "RATING DOWNGRADED", "DOWNGRADE", "NEGATIVE OUTLOOK", "RATING WATCH NEGATIVE")) {

            score -= 0.60;
        }

        return score;
    }

    private static double analyzeFundRaise(String text) {

        double score = 0.0;

        if (containsAny(text, "RAISE CAPITAL", "RAISING CAPITAL", "FUND RAISE", "FUNDRAISING", "QIP",
            "QUALIFIED INSTITUTIONAL PLACEMENT")) {

            score += 0.15;
        }

        if (containsAny(text, "DEBT REPAYMENT", "REDUCE DEBT", "DEBT REDUCTION", "CAPACITY EXPANSION")) {

            score += 0.20;
        }

        if (containsAny(text, "DILUTION", "SIGNIFICANT DILUTION")) {

            score -= 0.20;
        }

        return score;
    }

    private static double analyzeGeneric(String text) {

        double positive = 0;
        double negative = 0;

        positive += countMatches(text, "APPROVAL", "APPROVED", "AWARDED", "GROWTH", "EXPANSION", "IMPROVEMENT",
            "INCREASE", "PROFIT", "REVENUE GROWTH", "STRATEGIC");

        negative += countMatches(text, "DEFAULT", "FRAUD", "PENALTY", "REJECTED", "REJECTION", "TERMINATED",
            "INSOLVENCY", "DECLINE", "DECREASE", "LOSS", "DOWNGRADE");

        if (positive + negative == 0) {
            return 0.0;
        }

        return (positive - negative) / (positive + negative);
    }

    private static int countMatches(String text, String... words) {

        int count = 0;

        for (String word : words) {

            int index = 0;

            while ((index = text.indexOf(word, index)) >= 0) {

                count++;
                index += word.length();
            }
        }

        return count;
    }

    private static String createSummary(String text) {

        if (text == null || text.isBlank()) {

            return null;
        }

        String cleaned = text.replaceAll("\\s+", " ").trim();

        String[] sentences = cleaned.split("(?<=[.!?])\\s+");

        StringBuilder summary = new StringBuilder();

        for (String sentence : sentences) {

            if (sentence.length() < 20) {
                continue;
            }

            if (summary.length() > 0) {
                summary.append(" ");
            }

            summary.append(sentence);

            if (summary.length() >= 1000) {
                break;
            }
        }

        return summary.toString();
    }

    private static double analyzeBuyback(String text) {

        double score = 0.0;

        // --------------------------------------------------
        // Positive: company announces / approves buyback
        // --------------------------------------------------

        if (containsAny(text, "BUYBACK", "BUY-BACK", "SHARE REPURCHASE", "REPURCHASE OF SHARES", "REPURCHASE SHARES")) {

            score += 0.25;
        }

        // Board/shareholders approve the buyback
        if (containsAny(text, "BUYBACK APPROVED", "BUY-BACK APPROVED", "APPROVED THE BUYBACK", "APPROVED BUYBACK",
            "APPROVED THE PROPOSAL")) {

            score += 0.25;
        }

        // --------------------------------------------------
        // Positive: meaningful premium / attractive price
        // --------------------------------------------------

        if (containsAny(text, "PREMIUM TO MARKET PRICE", "PREMIUM OVER MARKET PRICE", "PREMIUM OF", "BUYBACK PRICE")) {

            score += 0.10;
        }

        // --------------------------------------------------
        // Positive: tender offer / reduction in shares
        // --------------------------------------------------

        if (containsAny(text, "TENDER OFFER", "TENDER BASIS", "REDUCE SHARE CAPITAL", "REDUCTION OF SHARE CAPITAL")) {

            score += 0.10;
        }

        // --------------------------------------------------
        // Positive: company has excess cash
        // --------------------------------------------------

        if (containsAny(text, "SURPLUS CASH", "EXCESS CASH", "AVAILABLE CASH", "CASH SURPLUS",
            "STRONG CASH POSITION")) {

            score += 0.15;
        }

        // --------------------------------------------------
        // Negative: buyback cancelled/rejected
        // --------------------------------------------------

        if (containsAny(text, "BUYBACK CANCELLED", "BUYBACK CANCELED", "BUY-BACK CANCELLED", "BUY-BACK CANCELED",
            "BUYBACK WITHDRAWN", "BUYBACK REJECTED", "WITHDRAWAL OF BUYBACK")) {

            score -= 0.60;
        }

        // --------------------------------------------------
        // Negative: excessive debt / financing risk
        // --------------------------------------------------

        if (containsAny(text, "DEBT FINANCED", "BORROWING FOR BUYBACK", "BORROWED FUNDS", "INCREASE DEBT", "HIGH DEBT",
            "DEBT BURDEN")) {

            score -= 0.30;
        }

        // --------------------------------------------------
        // Negative: regulatory problem
        // --------------------------------------------------

        if (containsAny(text, "REGULATORY OBJECTION", "REGULATORY ISSUE", "SEBI REJECTED", "SEBI REJECTION",
            "NOT APPROVED")) {

            score -= 0.40;
        }

        return score;
    }

    public record Classification(String eventType, String sentiment, String summary, double sentimentScore,
                                 double importanceScore) {
    }


}
