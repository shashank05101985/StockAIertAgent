package org.example.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import org.example.model.AiClassification;

public class GeminiAnalyzer {

    private final Client client;
    private final ObjectMapper mapper = new ObjectMapper();

    public GeminiAnalyzer() {

        String apiKey = System.getenv("");

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY environment variable is missing");
        }

        client = Client.builder().apiKey(apiKey).build();
    }

    public AiClassification analyze(String symbol, String eventType, String documentText) throws Exception {

        String prompt = buildPrompt(symbol, eventType, documentText);

        GenerateContentConfig config = GenerateContentConfig.builder().temperature(0.1f).responseMimeType(
            "application/json").build();

        GenerateContentResponse response = client.models.generateContent("gemini-2.5-flash", prompt, config);

        String json = response.text();

        return mapper.readValue(json, AiClassification.class);
    }

    private String buildPrompt(String symbol, String eventType, String documentText) {

        return """
            You are a professional Indian stock-market
            corporate announcement analyst.
            
            Analyze this NSE corporate announcement.
            
            COMPANY:
            %s
            
            EVENT TYPE:
            %s
            
            DOCUMENT:
            %s
            
            Determine the economic impact on the listed company.
            
            IMPORTANT:
            Do NOT assume that acquisition, order, buyback,
            fundraising or dividend is automatically positive.
            
            Consider:
            - transaction value
            - stake percentage
            - revenue impact
            - profit impact
            - margin impact
            - debt
            - dilution
            - cash flow
            - strategic benefit
            - regulatory risk
            - management commentary
            - whether the event is material
            
            SENTIMENT:
            POSITIVE = beneficial economic impact
            NEGATIVE = harmful economic impact
            NEUTRAL = unclear, routine or immaterial
            
            SENTIMENT SCORE:
            0 = extremely negative
            50 = neutral
            100 = extremely positive
            
            IMPORTANCE SCORE:
            0 = irrelevant/routine
            50 = moderately important
            100 = highly price-sensitive
            
            SUMMARY:
            Create a concise 3-5 sentence trading-oriented summary.
            
            Include important:
            - numbers
            - percentages
            - transaction values
            - order values
            - expected benefits
            - risks
            
            Do NOT include:
            - CIN
            - registered office
            - contact information
            - generic legal boilerplate
            - director details unless material
            
            Return ONLY JSON:
            
            {
              "summary": "...",
              "sentiment": "POSITIVE",
              "sentimentScore": 88,
              "importanceScore": 95,
              "reason": "..."
            }
            """.formatted(symbol, eventType, documentText);
    }

}
