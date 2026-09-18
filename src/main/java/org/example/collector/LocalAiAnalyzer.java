package org.example.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.AiClassification;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class LocalAiAnalyzer {

    private static final String OLLAMA_URL =
        "http://localhost:11434/api/generate";

    private static final String MODEL =
        "qwen3:4b-instruct";

    private final HttpClient client;

    private final ObjectMapper mapper;

    public LocalAiAnalyzer() {

        client = HttpClient.newBuilder()
            .build();

        mapper = new ObjectMapper();
    }

    public AiClassification analyze(
        String symbol,
        String eventType,
        String documentText)
        throws Exception {

        String prompt =
            buildPrompt(
                symbol,
                eventType,
                documentText
            );

        String requestJson =
            mapper.writeValueAsString(
                new OllamaRequest(
                    MODEL,
                    prompt,
                    false
                )
            );

        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(URI.create(
                    OLLAMA_URL
                ))
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
                "Ollama HTTP "
                    + response.statusCode()
                    + ": "
                    + response.body()
            );
        }

        JsonNode root =
            mapper.readTree(
                response.body()
            );

        String aiText =
            root.get("response")
                .asText();

        System.out.println(
            "AI RESPONSE: "
                + aiText
        );

        return mapper.readValue(
            cleanJson(aiText),
            AiClassification.class
        );
    }

    private String buildPrompt(
        String symbol,
        String eventType,
        String documentText) {

        return """
                You are a professional Indian stock market
                corporate announcement analyst.

                Company: %s

                Event type: %s

                Analyze the following NSE corporate announcement.

                DOCUMENT:
                %s

                Determine the actual economic impact on the
                listed company.

                Do NOT assume:
                acquisition = positive
                order = positive
                buyback = positive
                fundraising = positive
                dividend = positive

                Consider:
                - transaction value
                - stake percentage
                - revenue impact
                - profit impact
                - margin impact
                - debt
                - dilution
                - cash flow
                - strategic benefits
                - regulatory risks
                - management commentary

                Return ONLY this JSON:

                {
                  "summary": "...",
                  "sentiment": "POSITIVE|NEGATIVE|NEUTRAL",
                  "sentimentScore": 0,
                  "importanceScore": 0,
                  "reason": "..."
                }

                Summary requirements:
                - 3 to 5 sentences
                - trading focused
                - include important numbers
                - include transaction/order value
                - include percentages
                - include expected benefit or risk
                - no legal boilerplate
                """.formatted(
            symbol,
            eventType,
            documentText
        );
    }

    private String cleanJson(String text) {

        text = text.trim();

        if (text.startsWith("```json")) {
            text = text.substring(7);
        }

        if (text.startsWith("```")) {
            text = text.substring(3);
        }

        if (text.endsWith("```")) {
            text = text.substring(
                0,
                text.length() - 3
            );
        }

        return text.trim();
    }

    private record OllamaRequest(
        String model,
        String prompt,
        boolean stream
    ) {
    }
}
