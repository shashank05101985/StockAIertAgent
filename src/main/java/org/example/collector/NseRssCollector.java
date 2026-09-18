package org.example.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.model.StockEvent;
import org.example.news.EventClassifier;

import javax.sql.rowset.spi.XmlReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NseRssCollector {

    private final String feedUrl;

    public NseRssCollector(String feedUrl) {
        this.feedUrl = feedUrl;
    }

    public List<StockEvent> fetch(String fromDate, String toDate) throws Exception {

        ZoneId zone = ZoneId.of("Asia/Kolkata");

        String url = "https://www.nseindia.com/api/corporate-announcements" + "?index=equities" + "&from_date=" + fromDate + "&to_date=" + toDate + "&reqXbrl=false";

        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(
            Duration.ofSeconds(10)).build();

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " + "AppleWebKit/537.36 (KHTML, like Gecko) " + "Chrome/151.0 Safari/537.36").header(
            "Accept", "application/json, text/plain, */*").header("Referer", "https://www.nseindia.com/").GET().build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("NSE HTTP STATUS: " + response.statusCode());

        if (response.statusCode() != 200) {

            throw new IOException("NSE returned HTTP " + response.statusCode());
        }

        String json = response.body();

        ObjectMapper mapper = new ObjectMapper();

        JsonNode root = mapper.readTree(json);

        List<StockEvent> events = new ArrayList<>();

        if (!root.isArray()) {

            System.out.println("Unexpected NSE response: " + json);

            return events;
        }

        for (JsonNode item : root) {

            String symbol = text(item, "symbol");

            String companyName = text(item, "sm_name");

            String subject = text(item, "desc");

            String details = text(item, "attchmntText");

            String attachment = text(item, "attchmntFile");

            String broadcastDate = text(item, "an_dt");

            // -----------------------------------------
            // Skip records without NSE symbol
            // -----------------------------------------

            if (symbol == null || symbol.isBlank()) {

                continue;
            }

            // -----------------------------------------
            // Parse broadcast time
            // -----------------------------------------

            LocalDateTime eventTime = parseNseDate(broadcastDate);

            if (eventTime == null) {
                continue;
            }


            StockEvent event = new StockEvent();

            event.setSymbol(symbol.trim());

            event.setTitle(companyName);

            event.setDescription(details);

            event.setEventTime(eventTime);

            event.setEventDate(eventTime.toLocalDate());

            event.setSource("NSE_API");

            event.setSourceUrl(attachment);

            event.setEventType(subject);

            events.add(event);
        }

        System.out.println("NSE announcements fetched: " + events.size());

        return events;
    }

    private String text(JsonNode node, String field) {

        JsonNode value = node.get(field);

        if (value == null || value.isNull()) {
            return null;
        }

        String result = value.asText();

        return result == null || result.isBlank() ? null : result.trim();
    }

    private LocalDateTime parseNseDate(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalDateTime.parse(
                value.trim(),
                DateTimeFormatter.ofPattern(
                    "dd-MMM-yyyy HH:mm:ss",
                    Locale.ENGLISH
                )
            );

        } catch (DateTimeParseException e) {

            System.out.println(
                "Unable to parse NSE date: " + value
            );

            return null;
        }
    }


}
