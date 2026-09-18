package org.example.collector;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class PythonAiAnalyzer {

    private static final String PYTHON_URL =
            "http://127.0.0.1:8000/analyze";

    private final HttpClient client;
    private final ObjectMapper mapper;

    public PythonAiAnalyzer() {

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        mapper = new ObjectMapper();
    }

    /**
     * Send NSE announcement information to Python.
     *
     * Java sends:
     *   symbol
     *   eventType
     *   pdfLink
     *
     * Python:
     *   downloads PDF
     *   extracts text
     *   runs FinBERT
     *   returns JSON
     */
    public AiClassification analyze(
            String symbol,
            String eventType,
            String pdfLink
    ) throws Exception {

        AnalyzeRequest requestBody =
                new AnalyzeRequest(
                        symbol,
                        eventType,
                        pdfLink
                );

        String requestJson =
                mapper.writeValueAsString(
                        requestBody
                );

        System.out.println(
                "Calling Python AI service..."
        );

        System.out.println(
                "Symbol   : " + symbol
        );

        System.out.println(
                "Event    : " + eventType
        );

        System.out.println(
                "PDF      : " + pdfLink
        );

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(PYTHON_URL)
                        )
                        .timeout(
                                Duration.ofSeconds(120)
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(requestJson)
                        )
                        .build();

        HttpResponse<String> response =
                client.send(
                        request,
                        HttpResponse.BodyHandlers
                                .ofString()
                );

        if (response.statusCode() != 200) {

            throw new RuntimeException(
                    "Python AI HTTP "
                            + response.statusCode()
                            + ": "
                            + response.body()
            );
        }

        String responseJson =
                response.body();

        System.out.println(
                "Python AI RESPONSE:"
        );

        System.out.println(
                responseJson
        );

        return mapper.readValue(
                responseJson,
                AiClassification.class
        );
    }


    // ============================================================
    // REQUEST
    // ============================================================

    public record AnalyzeRequest(

            String symbol,

            String eventType,

            String pdfLink

    ) {
    }


    // ============================================================
    // RESPONSE
    // ============================================================

    public record AiClassification(

            String company,

            Event event,

            String summary,

            Sentiment sentiment,

            String tradingImpact

    ) {
    }


    public record Event(

            String type

    ) {
    }


    public record Sentiment(

            String label,

            double positive,

            double neutral,

            double negative,

            double score

    ) {
    }
}

