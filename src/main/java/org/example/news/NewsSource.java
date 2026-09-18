package org.example.news;

/**
 * Supported News Sources for Stock Market News
 */
public enum NewsSource {

    MINT("Mint", "https://www.livemint.com", 1.0),
    CNBC("CNBC TV18", "https://www.cnbctv18.com", 1.0),
    BUSINESS_STANDARD("Business Standard", "https://www.business-standard.com", 1.0),
    ECONOMIC_TIMES("Economic Times", "https://economictimes.indiatimes.com", 1.0),
    MONEYCONTROL("Moneycontrol", "https://www.moneycontrol.com", 1.2),  // Higher weight - market focused
    REUTERS("Reuters", "https://www.reuters.com", 1.1),
    BLOOMBERG("Bloomberg", "https://www.bloomberg.com", 1.2),
    NSE("NSE Announcements", "https://www.nseindia.com", 1.5),  // Highest weight - official
    BSE("BSE Announcements", "https://www.bseindia.com", 1.5);

    private final String displayName;
    private final String baseUrl;
    private final double reliabilityWeight;  // Weight based on source reliability

    NewsSource(String displayName, String baseUrl, double reliabilityWeight) {
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.reliabilityWeight = reliabilityWeight;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public double getReliabilityWeight() {
        return reliabilityWeight;
    }
}

