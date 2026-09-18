package org.example.collector;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SymbolResolver {

    private final Map<String, String> symbols;

    public SymbolResolver(Map<String, String> symbols) {
        this.symbols = symbols;
    }

    public String resolve(String title, String description) {

        String titleText = normalize(title == null ? "" : title);

        // 1. Try title first
        String symbol = findBestMatch(titleText);

        if (symbol != null) {
            return symbol;
        }

        // 2. Only if title fails, try description
        String descriptionText = normalize(description == null ? "" : description);

        return findBestMatch(descriptionText);
    }

    private String findBestMatch(String text) {

        String bestSymbol = null;
        int bestScore = 0;
        int bestLength = 0;

        for (Map.Entry<String, String> entry : symbols.entrySet()) {

            String companyName = entry.getKey();
            String symbol = entry.getValue();

            int score = partialMatchScore(companyName, text);

            if (score < 2) {
                continue;
            }

            if (score > bestScore || (score == bestScore && companyName.length() > bestLength)) {

                bestScore = score;
                bestLength = companyName.length();
                bestSymbol = symbol;
            }
        }

        return bestSymbol;
    }

    private String normalize(String value) {

        if (value == null || value.isBlank()) {
            return "";
        }

        return value.toUpperCase(Locale.ROOT).replace("&", " AND ").replaceAll("[^A-Z0-9 ]", " ").replaceAll("\\s+",
            " ").trim();
    }

    private int partialMatchScore(String companyName, String text) {

        String[] companyWords = normalize(companyName).split("\\s+");

        String[] textWords = normalize(text).split("\\s+");

        int score = 0;

        for (String companyWord : companyWords) {

            if (companyWord.length() < 3) {
                continue;
            }

            if (isIgnoredWord(companyWord)) {
                continue;
            }

            for (String textWord : textWords) {

                if (textWord.startsWith(companyWord) || companyWord.startsWith(textWord)) {

                    score++;
                    break;
                }
            }
        }

        return score;
    }
    private boolean isIgnoredWord(String word) {

        return Set.of(
            "LIMITED",
            "LTD",
            "PRIVATE",
            "PVT",
            "PUBLIC",
            "COMPANY",
            "CORPORATION",
            "CORP",
            "AND",
            "THE",
            "ETF",
            "FUND",
            "INDEX"
        ).contains(word);
    }
}
