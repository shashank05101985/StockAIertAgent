package org.example.reader;

import org.example.model.StockData;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Reader for NSE Bhav Copy data
 * Supports multiple date formats and column layouts
 * Uses plain Java CSV parsing (no external dependencies)
 */
public class BhavCopyReader {

    // Common date formats in bhav copy
    private static final DateTimeFormatter[] DATE_FORMATS = {
        DateTimeFormatter.ofPattern("dd-MMM-yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("ddMMMyyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy")
    };

    /**
     * Read single bhav copy CSV file
     */
    public List<StockData> readBhavCopy(String filePath) throws IOException {
        List<StockData> stockDataList = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return stockDataList;
            }

            // Parse header to find column indices
            String[] header = parseCSVLine(headerLine);
            Map<String, Integer> columnIndex = new HashMap<>();
            for (int i = 0; i < header.length; i++) {
                columnIndex.put(header[i].trim().toUpperCase(), i);
            }

            // Process data rows
            String line;
            int rowNum = 1;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                try {
                    String[] row = parseCSVLine(line);
                    StockData data = parseRow(row, columnIndex);
                    if (data != null && isValidStock(data)) {
                        stockDataList.add(data);
                    }
                } catch (Exception e) {
                    // Skip invalid rows
                    System.err.println("Skipping invalid row " + rowNum + ": " + line);
                }
            }
        }

        return stockDataList;
    }

    /**
     * Parse CSV line handling quoted fields
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
     * Read multiple bhav copy files from a directory
     */
    public Map<String, List<StockData>> readMultipleBhavCopies(String directoryPath) throws IOException {
        Map<String, List<StockData>> stockDataMap = new HashMap<>();

        List<Path> csvFiles;
        try (var stream = Files.walk(Paths.get(directoryPath))) {
            csvFiles = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().toLowerCase().endsWith(".csv"))
                .sorted()
                .toList();
        }

        System.out.println("Found " + csvFiles.size() + " CSV files");

        for (Path file : csvFiles) {
            System.out.println("Reading: " + file.getFileName());
            List<StockData> dayData = readBhavCopy(file.toString());

            for (StockData data : dayData) {
                stockDataMap.computeIfAbsent(data.getSymbol(), k -> new ArrayList<>()).add(data);
            }
        }

        // Sort each stock's data by date
        for (List<StockData> dataList : stockDataMap.values()) {
            dataList.sort(Comparator.comparing(StockData::getDate));

            // Set previous close for each day
            for (int i = 1; i < dataList.size(); i++) {
                dataList.get(i).setPreviousClose(dataList.get(i - 1).getClose());
            }
        }

        return stockDataMap;
    }

    /**
     * Parse a single row of bhav copy data
     */
    private StockData parseRow(String[] row, Map<String, Integer> columnIndex) {
        StockData data = new StockData();

        // Symbol column names
        String symbol = getColumnValue(row, columnIndex, "SYMBOL", "TCKRSYMB", "SCRIP", "STOCK");
        if (symbol == null || symbol.isEmpty()) {
            return null;
        }
        data.setSymbol(symbol.trim().toUpperCase());

        // Date column names
        String dateStr = getColumnValue(row, columnIndex, "DATE", "TIMESTAMP", "DATE1", "TRADE_DT", "TRD_DT");
        if (dateStr != null) {
            data.setDate(parseDate(dateStr));
        } else {
            data.setDate(LocalDate.now()); // Default to current date if not found
        }

        // OHLC values
        data.setOpen(parseDouble(getColumnValue(row, columnIndex, "OPEN", "OPEN_PRICE", "OPENPRC")));
        data.setHigh(parseDouble(getColumnValue(row, columnIndex, "HIGH", "HIGH_PRICE", "HIGHPRC", "HI_52_WK")));
        data.setLow(parseDouble(getColumnValue(row, columnIndex, "LOW", "LOW_PRICE", "LOWPRC", "LO_52_WK")));
        data.setClose(parseDouble(getColumnValue(row, columnIndex, "CLOSE", "CLOSE_PRICE", "CLOSEPRC", "LAST")));

        // Previous close
        double prevClose = parseDouble(getColumnValue(row, columnIndex, "PREVCLOSE", "PREV_CLOSE", "PREVIOUS_CLOSE", "PREV_CL_PR"));
        if (prevClose > 0) {
            data.setPreviousClose(prevClose);
        }

        // Volume/Quantity
        data.setTradedQty(parseLong(getColumnValue(row, columnIndex,
            "TTL_TRD_QNTY", "TRADED_QTY", "TOTAL_TRADED_QUANTITY", "TOTTRDQTY",
            "VOLUME", "TOT_TRD_QTY", "QTY", "QUANTITY")));

        // Delivered quantity
        data.setDeliveredQty(parseLong(getColumnValue(row, columnIndex,
            "DELIV_QTY", "DELIVERED_QTY", "DELIVERY_QTY", "DEL_QTY",
            "DELIVERABLE_QTY", "NO_OF_SHARES_DELIVERED")));

        // Delivery percent (if directly available)
        double delPct = parseDouble(getColumnValue(row, columnIndex,
            "DELIV_PER", "DELIVERY_PERCENT", "DEL_PER", "%_DELI_QTY_TO_TRADED_QTY"));
        if (delPct > 0) {
            data.setDeliveryPercent(delPct);
        }

        // Turnover
        data.setTurnover(parseDouble(getColumnValue(row, columnIndex,
            "TURNOVER", "TOTAL_TURNOVER", "TTL_TRD_VAL", "TOTTRDVAL", "VALUE")));

        return data;
    }

    /**
     * Get column value by trying multiple possible column names
     */
    private String getColumnValue(String[] row, Map<String, Integer> columnIndex, String... possibleNames) {
        for (String name : possibleNames) {
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
     * Parse date string with multiple format support
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return LocalDate.now();
        }

        dateStr = dateStr.trim();

        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (Exception ignored) {
            }
        }

        // Try extracting date from filename-like strings
        try {
            if (dateStr.length() >= 8 && dateStr.matches(".*\\d{8}.*")) {
                String digits = dateStr.replaceAll("[^0-9]", "").substring(0, 8);
                return LocalDate.parse(digits, DateTimeFormatter.BASIC_ISO_DATE);
            }
        } catch (Exception ignored) {
        }

        return LocalDate.now();
    }

    /**
     * Parse double value safely
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
     * Parse long value safely
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
     * Filter valid stocks (exclude junk data)
     */
    private boolean isValidStock(StockData data) {
        // Must have valid OHLC
        if (data.getOpen() <= 0 || data.getHigh() <= 0 ||
            data.getLow() <= 0 || data.getClose() <= 0) {
            return false;
        }

        // Must have some volume
        if (data.getTradedQty() <= 0) {
            return false;
        }

        // Symbol should not be index or special
        String symbol = data.getSymbol();
        return !symbol.contains("NIFTY") && !symbol.contains("INDEX") &&
               !symbol.contains("VIX") && !symbol.startsWith("-");
    }

    /**
     * Create sample data for testing (when no files available)
     */
    public Map<String, List<StockData>> createSampleData() {
        Map<String, List<StockData>> sampleData = new HashMap<>();
        Random random = new Random(42);

        String[] symbols = {"RELIANCE", "TCS", "INFY", "HDFCBANK", "ICICIBANK",
                           "BHARTIARTL", "WIPRO", "LT", "SBIN", "TATASTEEL"};

        for (String symbol : symbols) {
            List<StockData> dataList = new ArrayList<>();
            double basePrice = 500 + random.nextDouble() * 2000;

            for (int day = 30; day >= 0; day--) {
                LocalDate date = LocalDate.now().minusDays(day);

                double open = basePrice * (1 + (random.nextDouble() - 0.5) * 0.02);
                double high = open * (1 + random.nextDouble() * 0.03);
                double low = open * (1 - random.nextDouble() * 0.03);
                double close = low + random.nextDouble() * (high - low);

                long tradedQty = 100000 + random.nextInt(900000);
                // Increase volume/delivery for some stocks on recent days
                if (day < 5 && random.nextDouble() > 0.5) {
                    tradedQty *= 2;
                }

                long deliveredQty = (long) (tradedQty * (0.3 + random.nextDouble() * 0.4));
                if (day < 3 && random.nextDouble() > 0.6) {
                    deliveredQty = (long) (tradedQty * (0.5 + random.nextDouble() * 0.3));
                }

                StockData data = new StockData(symbol, date, open, high, low, close, tradedQty, deliveredQty);
                if (!dataList.isEmpty()) {
                    data.setPreviousClose(dataList.get(dataList.size() - 1).getClose());
                }
                dataList.add(data);

                basePrice = close;
            }

            sampleData.put(symbol, dataList);
        }

        return sampleData;
    }
}

