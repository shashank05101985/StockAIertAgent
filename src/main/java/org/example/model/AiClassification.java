package org.example.model;


public record AiClassification(
    String summary,
    String sentiment,
    double sentimentScore,
    double importanceScore,
    String reason
) {
}
