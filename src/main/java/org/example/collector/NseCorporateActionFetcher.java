package org.example.collector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Downloads equity corporate actions from nseindia.com for a date range and
 * writes them as a CSV that NseCorporateActionImporter.parse() understands.
 *
 * NSE's API needs the cookies set by a normal page visit first.
 */
public class NseCorporateActionFetcher {

    private static final String PAGE = "https://www.nseindia.com/companies-listing/corporate-filings-actions";
    private static final String API = "https://www.nseindia.com/api/corporates-corporateActions?index=equities&from_date=%s&to_date=%s";
    private static final String USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Safari/537.36";
    private static final DateTimeFormatter NSE_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public static Path download(LocalDate from, LocalDate to) throws Exception {

        HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

        // Sets the cookies the API checks for.
        client.send(request(PAGE, "text/html").build(), HttpResponse.BodyHandlers.discarding());

        String url = String.format(API, from.format(NSE_DATE), to.format(NSE_DATE));

        HttpResponse<String> response = client.send(
            request(url, "application/json").header("Referer", PAGE).build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new IOException("NSE returned HTTP " + response.statusCode() + " for " + url);
        }

        JsonElement json = JsonParser.parseString(response.body());
        JsonArray rows = json.isJsonArray() ? json.getAsJsonArray()
            : json.getAsJsonObject().getAsJsonArray("data");

        if (rows == null) {
            throw new IOException("Unexpected NSE response: " + response.body().substring(0, Math.min(200, response.body().length())));
        }

        List<String> lines = new ArrayList<>();
        lines.add(csv("SYMBOL", "SERIES", "PURPOSE", "FACE VALUE", "EX-DATE"));

        for (JsonElement e : rows) {
            JsonObject r = e.getAsJsonObject();
            lines.add(csv(text(r, "symbol"), text(r, "series"), text(r, "subject").replace('\n', ' '),
                text(r, "faceVal"), text(r, "exDate")));
        }

        Path file = Files.createTempFile("nse_corporate_actions_", ".csv");
        Files.write(file, lines, StandardCharsets.UTF_8);

        System.out.println("Downloaded " + rows.size() + " NSE corporate actions, ex-dates " + from + " to " + to);

        return file;
    }

    private static HttpRequest.Builder request(String url, String accept) {
        return HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("User-Agent", USER_AGENT)
            .header("Accept", accept)
            .header("Accept-Language", "en-US,en;q=0.9");
    }

    private static String text(JsonObject o, String key) {
        JsonElement v = o.get(key);
        return v == null || v.isJsonNull() ? "" : v.getAsString();
    }

    private static String csv(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (String f : fields) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append('"').append(f.replace("\"", "\"\"")).append('"');
        }
        return sb.toString();
    }
}
