package org.example.reader;

import org.example.model.IntradayCandle;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Reader for intraday candle data (1-min, 5-min, etc.)
 * Supports multiple CSV formats
 */
public class IntradayDataReader {

    private static final DateTimeFormatter[] DATETIME_FORMATS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    private int defaultIntervalMinutes = 5;

    /**
     * Read intraday data from CSV file
     * Expected format: timestamp, open, high, low, close, volume
     */
    public List<IntradayCandle> readIntradayData(String filePath, String symbol) throws IOException {
        List<IntradayCandle> candles = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return candles;
            }

            // Parse header
            String[] header = parseCSVLine(headerLine);
            Map<String, Integer> columnIndex = new HashMap<>();
            for (int i = 0; i < header.length; i++) {
                columnIndex.put(header[i].trim().toUpperCase(), i);
            }

            // Read data rows
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    String[] row = parseCSVLine(line);
                    IntradayCandle candle = parseRow(row, columnIndex, symbol);
                    if (candle != null) {
                        candles.add(candle);
                    }
                } catch (Exception e) {
                    // Skip invalid rows
                }
            }
        }

        // Sort by timestamp
        candles.sort(Comparator.comparing(IntradayCandle::getTimestamp));

        return candles;
    }

    /**
     * Read intraday data for multiple symbols from a directory
     */
    public Map<String, List<IntradayCandle>> readMultipleFiles(String directoryPath) throws IOException {
        Map<String, List<IntradayCandle>> dataMap = new HashMap<>();

        List<Path> csvFiles;
        try (var stream = Files.walk(Paths.get(directoryPath))) {
            csvFiles = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().toLowerCase().endsWith(".csv"))
                .toList();
        }

        for (Path file : csvFiles) {
            String fileName = file.getFileName().toString();
            // Extract symbol from filename (e.g., "RELIANCE_5min.csv" -> "RELIANCE")
            String symbol = fileName.split("[_.]")[0].toUpperCase();

            List<IntradayCandle> candles = readIntradayData(file.toString(), symbol);
            if (!candles.isEmpty()) {
                dataMap.put(symbol, candles);
            }
        }

        return dataMap;
    }

    /**
     * Parse a single row
     */
    private IntradayCandle parseRow(String[] row, Map<String, Integer> columnIndex, String symbol) {
        IntradayCandle candle = new IntradayCandle();
        candle.setSymbol(symbol);
        candle.setIntervalMinutes(defaultIntervalMinutes);

        // Timestamp
        String timestampStr = getColumnValue(row, columnIndex,
            "TIMESTAMP", "DATETIME", "DATE", "TIME", "DATE_TIME");
        if (timestampStr != null) {
            candle.setTimestamp(parseDateTime(timestampStr));
        } else {
            return null;
        }

        // OHLC
        candle.setOpen(parseDouble(getColumnValue(row, columnIndex, "OPEN", "O")));
        candle.setHigh(parseDouble(getColumnValue(row, columnIndex, "HIGH", "H")));
        candle.setLow(parseDouble(getColumnValue(row, columnIndex, "LOW", "L")));
        candle.setClose(parseDouble(getColumnValue(row, columnIndex, "CLOSE", "C", "LTP")));

        // Volume
        candle.setVolume(parseLong(getColumnValue(row, columnIndex,
            "VOLUME", "VOL", "QTY", "QUANTITY", "TRADED_QTY")));

        // Validate
        if (candle.getOpen() <= 0 || candle.getHigh() <= 0 ||
            candle.getLow() <= 0 || candle.getClose() <= 0) {
            return null;
        }

        return candle;
    }

    /**
     * Parse CSV line
     */
    private String[] parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString().trim());

        return fields.toArray(new String[0]);
    }

    /**
     * Get column value by trying multiple possible names
     */
    private String getColumnValue(String[] row, Map<String, Integer> columnIndex, String... names) {
        for (String name : names) {
            Integer index = columnIndex.get(name.toUpperCase());
            if (index != null && index < row.length) {
                String value = row[index].trim();
                if (!value.isEmpty() && !value.equals("-")) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * Parse datetime string
     */
    private LocalDateTime parseDateTime(String str) {
        if (str == null || str.isEmpty()) {
            return LocalDateTime.now();
        }

        str = str.trim();

        for (DateTimeFormatter formatter : DATETIME_FORMATS) {
            try {
                return LocalDateTime.parse(str, formatter);
            } catch (Exception ignored) {
            }
        }

        return LocalDateTime.now();
    }

    /**
     * Parse double value
     */
    private double parseDouble(String value) {
        if (value == null || value.isEmpty() || value.equals("-")) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Parse long value
     */
    private long parseLong(String value) {
        if (value == null || value.isEmpty() || value.equals("-")) {
            return 0L;
        }
        try {
            return Long.parseLong(value.replace(",", "").replaceAll("\\..*", "").trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Set default interval
     */
    public void setDefaultIntervalMinutes(int minutes) {
        this.defaultIntervalMinutes = minutes;
    }

    /**
     * Create sample intraday data for testing
     */
    public Map<String, List<IntradayCandle>> createSampleData(List<String> symbols) {
        Map<String, List<IntradayCandle>> sampleData = new HashMap<>();
        Random random = new Random(42);

        for (String symbol : symbols) {
            List<IntradayCandle> candles = new ArrayList<>();
            double basePrice = 100 + random.nextDouble() * 500;

            // Create data for one trading day (9:15 AM to 3:30 PM)
            LocalDateTime startTime = LocalDateTime.now()
                .withHour(9).withMinute(15).withSecond(0).withNano(0);

            for (int i = 0; i < 75; i++) { // 75 x 5-min candles
                LocalDateTime timestamp = startTime.plusMinutes(i * 5L);

                double open = basePrice * (1 + (random.nextDouble() - 0.5) * 0.005);
                double high = open * (1 + random.nextDouble() * 0.008);
                double low = open * (1 - random.nextDouble() * 0.008);
                double close = low + random.nextDouble() * (high - low);

                long volume = 10000 + random.nextInt(50000);

                // Add some volume spikes
                if (i < 6 || (i > 30 && i < 35)) { // First 30 min and mid-day
                    volume *= 2;
                }

                IntradayCandle candle = new IntradayCandle(symbol, timestamp,
                    open, high, low, close, volume, 5);
                candles.add(candle);

                basePrice = close;
            }

            sampleData.put(symbol, candles);
        }

        return sampleData;
    }
}

