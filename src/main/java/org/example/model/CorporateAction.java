package org.example.model;

import java.time.LocalDate;

/**
 * A split or bonus. factor = price before exDate / price after exDate.
 */
public record CorporateAction(String symbol, LocalDate exDate, String action, double factor, String purpose) {
}
