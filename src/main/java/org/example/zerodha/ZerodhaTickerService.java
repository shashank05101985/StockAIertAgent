package org.example.zerodha;

import okhttp3.*;
import okio.ByteString;
import org.example.model.IntradayCandle;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Zerodha WebSocket Ticker for Real-time Market Data
 *
 * Provides live tick data streaming from Zerodha Kite
 */
public class ZerodhaTickerService {

    private final ZerodhaConfig config;
    private final OkHttpClient client;
    private WebSocket webSocket;

    private boolean isConnected = false;
    private Set<Long> subscribedTokens = new HashSet<>();

    // Tick data storage
    private Map<Long, TickData> latestTicks = new ConcurrentHashMap<>();

    // Symbol to token mapping
    private Map<String, Long> symbolToToken = new HashMap<>();
    private Map<Long, String> tokenToSymbol = new HashMap<>();

    // Candle builders (for building 5-min candles from ticks)
    private Map<String, CandleBuilder> candleBuilders = new ConcurrentHashMap<>();

    // Callbacks
    private Consumer<TickData> onTickCallback;
    private Consumer<IntradayCandle> onCandleCallback;
    private Consumer<String> onErrorCallback;
    private Runnable onConnectCallback;
    private Runnable onDisconnectCallback;

    public ZerodhaTickerService() {
        this.config = ZerodhaConfig.getInstance();
        this.client = new OkHttpClient.Builder().build();
    }

    /**
     * Set symbol to token mapping
     */
    public void setInstrumentTokens(Map<String, Long> tokens) {
        this.symbolToToken = new HashMap<>(tokens);
        for (Map.Entry<String, Long> entry : tokens.entrySet()) {
            tokenToSymbol.put(entry.getValue(), entry.getKey());
        }
    }

    /**
     * Connect to WebSocket
     */
    public void connect() {
        if (!config.hasAccessToken()) {
            throw new IllegalStateException("Access token not set");
        }

        String url = String.format("%s?api_key=%s&access_token=%s",
            ZerodhaConfig.WS_URL, config.getApiKey(), config.getAccessToken());

        Request request = new Request.Builder().url(url).build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                isConnected = true;
                System.out.println("✓ Connected to Zerodha WebSocket");

                if (onConnectCallback != null) {
                    onConnectCallback.run();
                }

                // Resubscribe to tokens
                if (!subscribedTokens.isEmpty()) {
                    subscribe(new ArrayList<>(subscribedTokens));
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                processBinaryMessage(bytes.toByteArray());
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                System.out.println("WebSocket closing: " + reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                isConnected = false;
                System.out.println("WebSocket closed: " + reason);

                if (onDisconnectCallback != null) {
                    onDisconnectCallback.run();
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                isConnected = false;
                String error = "WebSocket error: " + t.getMessage();
                System.err.println(error);

                if (onErrorCallback != null) {
                    onErrorCallback.accept(error);
                }

                // Reconnect after delay
                reconnect();
            }
        });
    }

    /**
     * Reconnect after failure
     */
    private void reconnect() {
        try {
            Thread.sleep(5000);
            if (!isConnected) {
                System.out.println("Attempting to reconnect...");
                connect();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Subscribe to instrument tokens
     */
    public void subscribe(List<Long> tokens) {
        subscribedTokens.addAll(tokens);

        if (!isConnected || webSocket == null) {
            return;
        }

        // Build subscription message
        StringBuilder msg = new StringBuilder("{\"a\":\"subscribe\",\"v\":[");
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) msg.append(",");
            msg.append(tokens.get(i));
        }
        msg.append("]}");

        webSocket.send(msg.toString());

        // Set mode to full
        StringBuilder modeMsg = new StringBuilder("{\"a\":\"mode\",\"v\":[\"full\",[");
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) modeMsg.append(",");
            modeMsg.append(tokens.get(i));
        }
        modeMsg.append("]]}");

        webSocket.send(modeMsg.toString());

        System.out.println("Subscribed to " + tokens.size() + " instruments");
    }

    /**
     * Subscribe by symbols
     */
    public void subscribeSymbols(List<String> symbols) {
        List<Long> tokens = new ArrayList<>();
        for (String symbol : symbols) {
            Long token = symbolToToken.get(symbol);
            if (token != null) {
                tokens.add(token);

                // Initialize candle builder
                candleBuilders.put(symbol, new CandleBuilder(symbol, 5));
            }
        }
        subscribe(tokens);
    }

    /**
     * Unsubscribe from tokens
     */
    public void unsubscribe(List<Long> tokens) {
        subscribedTokens.removeAll(tokens);

        if (!isConnected || webSocket == null) {
            return;
        }

        StringBuilder msg = new StringBuilder("{\"a\":\"unsubscribe\",\"v\":[");
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) msg.append(",");
            msg.append(tokens.get(i));
        }
        msg.append("]}");

        webSocket.send(msg.toString());
    }

    /**
     * Process binary message from WebSocket
     */
    private void processBinaryMessage(byte[] data) {
        if (data.length < 2) return;

        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.BIG_ENDIAN);

        int numberOfPackets = buffer.getShort();

        for (int i = 0; i < numberOfPackets; i++) {
            if (buffer.remaining() < 2) break;

            int packetLength = buffer.getShort();
            if (buffer.remaining() < packetLength) break;

            byte[] packet = new byte[packetLength];
            buffer.get(packet);

            TickData tick = parseTickPacket(packet);
            if (tick != null) {
                latestTicks.put(tick.instrumentToken, tick);

                // Notify callback
                if (onTickCallback != null) {
                    onTickCallback.accept(tick);
                }

                // Update candle builder
                String symbol = tokenToSymbol.get(tick.instrumentToken);
                if (symbol != null && candleBuilders.containsKey(symbol)) {
                    IntradayCandle candle = candleBuilders.get(symbol).addTick(tick);
                    if (candle != null && onCandleCallback != null) {
                        onCandleCallback.accept(candle);
                    }
                }
            }
        }
    }

    /**
     * Parse tick packet
     */
    private TickData parseTickPacket(byte[] packet) {
        if (packet.length < 8) return null;

        ByteBuffer buffer = ByteBuffer.wrap(packet);
        buffer.order(ByteOrder.BIG_ENDIAN);

        TickData tick = new TickData();
        tick.instrumentToken = buffer.getInt();

        // LTP mode (8 bytes)
        if (packet.length >= 8) {
            tick.lastPrice = buffer.getInt() / 100.0;
        }

        // Full mode (44 bytes or more)
        if (packet.length >= 44) {
            tick.lastQuantity = buffer.getInt();
            tick.averagePrice = buffer.getInt() / 100.0;
            tick.volume = buffer.getInt();
            tick.buyQuantity = buffer.getInt();
            tick.sellQuantity = buffer.getInt();
            tick.open = buffer.getInt() / 100.0;
            tick.high = buffer.getInt() / 100.0;
            tick.low = buffer.getInt() / 100.0;
            tick.close = buffer.getInt() / 100.0;
        }

        tick.timestamp = LocalDateTime.now();

        return tick;
    }

    /**
     * Get latest tick for a token
     */
    public TickData getLatestTick(long token) {
        return latestTicks.get(token);
    }

    /**
     * Get latest tick for a symbol
     */
    public TickData getLatestTick(String symbol) {
        Long token = symbolToToken.get(symbol);
        return token != null ? latestTicks.get(token) : null;
    }

    /**
     * Get current candle being built for a symbol
     */
    public IntradayCandle getCurrentCandle(String symbol) {
        CandleBuilder builder = candleBuilders.get(symbol);
        return builder != null ? builder.getCurrentCandle() : null;
    }

    /**
     * Disconnect WebSocket
     */
    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "Client disconnect");
            webSocket = null;
        }
        isConnected = false;
    }

    // Callback setters
    public void setOnTick(Consumer<TickData> callback) {
        this.onTickCallback = callback;
    }

    public void setOnCandle(Consumer<IntradayCandle> callback) {
        this.onCandleCallback = callback;
    }

    public void setOnError(Consumer<String> callback) {
        this.onErrorCallback = callback;
    }

    public void setOnConnect(Runnable callback) {
        this.onConnectCallback = callback;
    }

    public void setOnDisconnect(Runnable callback) {
        this.onDisconnectCallback = callback;
    }

    public boolean isConnected() {
        return isConnected;
    }

    /**
     * Tick data structure
     */
    public static class TickData {
        public long instrumentToken;
        public double lastPrice;
        public int lastQuantity;
        public double averagePrice;
        public long volume;
        public long buyQuantity;
        public long sellQuantity;
        public double open;
        public double high;
        public double low;
        public double close;
        public LocalDateTime timestamp;

        @Override
        public String toString() {
            return String.format("Tick[token=%d, ltp=%.2f, vol=%d, O=%.2f H=%.2f L=%.2f C=%.2f]",
                instrumentToken, lastPrice, volume, open, high, low, close);
        }
    }

    /**
     * Candle builder - builds candles from ticks
     */
    private static class CandleBuilder {
        private final String symbol;
        private final int intervalMinutes;

        private double open, high, low, close;
        private long volume;
        private LocalDateTime candleStart;
        private boolean hasData;

        public CandleBuilder(String symbol, int intervalMinutes) {
            this.symbol = symbol;
            this.intervalMinutes = intervalMinutes;
            reset();
        }

        public IntradayCandle addTick(TickData tick) {
            LocalDateTime now = tick.timestamp;

            // Check if we need to start a new candle
            if (candleStart == null || isNewInterval(now)) {
                IntradayCandle completedCandle = null;

                if (hasData) {
                    // Complete previous candle
                    completedCandle = new IntradayCandle(symbol, candleStart,
                        open, high, low, close, volume, intervalMinutes);
                }

                // Start new candle
                reset();
                candleStart = alignToInterval(now);
                open = tick.lastPrice;
                high = tick.lastPrice;
                low = tick.lastPrice;
                close = tick.lastPrice;
                volume = tick.volume;
                hasData = true;

                return completedCandle;
            }

            // Update current candle
            high = Math.max(high, tick.lastPrice);
            low = Math.min(low, tick.lastPrice);
            close = tick.lastPrice;
            volume = tick.volume; // Use cumulative volume from exchange

            return null;
        }

        public IntradayCandle getCurrentCandle() {
            if (!hasData) return null;
            return new IntradayCandle(symbol, candleStart, open, high, low, close, volume, intervalMinutes);
        }

        private boolean isNewInterval(LocalDateTime now) {
            if (candleStart == null) return true;

            int currentInterval = now.getMinute() / intervalMinutes;
            int candleInterval = candleStart.getMinute() / intervalMinutes;

            return now.getHour() != candleStart.getHour() || currentInterval != candleInterval;
        }

        private LocalDateTime alignToInterval(LocalDateTime time) {
            int alignedMinute = (time.getMinute() / intervalMinutes) * intervalMinutes;
            return time.withMinute(alignedMinute).withSecond(0).withNano(0);
        }

        private void reset() {
            open = high = low = close = 0;
            volume = 0;
            candleStart = null;
            hasData = false;
        }
    }
}

