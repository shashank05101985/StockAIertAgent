package org.example.zerodha;

import org.example.analysis.IntradayAnalyzer;
import org.example.analysis.StockAnalyzer;
import org.example.model.*;
import org.example.reader.BhavCopyReader;

import java.io.IOException;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Live Trading System with Zerodha Integration
 *
 * Features:
 * - Pre-market stock selection using daily data
 * - Real-time intraday signals via WebSocket
 * - Automatic entry/exit alerts
 *
 * Usage:
 * 1. Set API credentials in zerodha.properties
 * 2. Run the application
 * 3. Complete Zerodha login (opens browser)
 * 4. System starts monitoring and generating signals
 */
public class ZerodhaLiveTrading {

    private final ZerodhaDataFetcher dataFetcher;
    private final ZerodhaTickerService tickerService;
    private final StockAnalyzer dailyAnalyzer;
    private final IntradayAnalyzer intradayAnalyzer;

    private List<String> watchlist = new ArrayList<>();
    private Map<String, StockSignal> dailySignals = new HashMap<>();
    private Map<String, IntradaySignal> activeSignals = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;
    private boolean isRunning = false;

    // Trading parameters
    private int maxWatchlistSize = 10;
    private double maxRiskPerTrade = 1.0; // 1% of capital
    private double capital = 100000; // Default capital

    public ZerodhaLiveTrading() {
        this.dataFetcher = new ZerodhaDataFetcher();
        this.tickerService = new ZerodhaTickerService();
        this.dailyAnalyzer = new StockAnalyzer();
        this.intradayAnalyzer = new IntradayAnalyzer();
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    /**
     * Initialize with API credentials
     */
    public void initialize(String apiKey, String apiSecret) {
        ZerodhaConfig.getInstance().setCredentials(apiKey, apiSecret);
    }

    /**
     * Set access token (after user login)
     */
    public void setAccessToken(String accessToken) {
        ZerodhaConfig.getInstance().setAccessToken(accessToken);
    }

    /**
     * Set trading capital
     */
    public void setCapital(double capital) {
        this.capital = capital;
    }

    /**
     * Get login URL for user authentication
     */
    public String getLoginUrl() {
        return dataFetcher.getLoginUrl();
    }

    /**
     * Complete authentication with request token
     */
    public void authenticate(String requestToken) throws IOException {
        dataFetcher.generateAccessToken(requestToken);
    }

    /**
     * Start the live trading system
     */
    public void start() {
        System.out.println("\n╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║          ZERODHA LIVE TRADING SYSTEM                             ║");
        System.out.println("║          Real-time Stock Analysis & Signals                      ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝\n");

        if (!dataFetcher.isReady()) {
            System.err.println("❌ API not configured. Please set credentials and authenticate.");
            System.out.println("Login URL: " + getLoginUrl());
            return;
        }

        isRunning = true;

        try {
            // Step 1: Pre-market analysis
            runPreMarketAnalysis();

            // Step 2: Load instrument tokens for watchlist
            loadInstrumentTokens();

            // Step 3: Setup WebSocket callbacks
            setupTickerCallbacks();

            // Step 4: Connect to WebSocket
            tickerService.connect();

            // Step 5: Subscribe to watchlist
            tickerService.subscribeSymbols(watchlist);

            // Step 6: Schedule periodic tasks
            schedulePeriodicTasks();

            System.out.println("\n✓ Live trading system started successfully!");
            System.out.println("Monitoring " + watchlist.size() + " stocks for signals...\n");

        } catch (Exception e) {
            System.err.println("❌ Error starting live trading: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Run pre-market analysis using daily data
     */
    private void runPreMarketAnalysis() throws IOException {
        System.out.println("=".repeat(70));
        System.out.println("PRE-MARKET ANALYSIS");
        System.out.println("=".repeat(70));

        // Option 1: Load from bhav copy files
        BhavCopyReader bhavReader = new BhavCopyReader();
        Map<String, List<StockData>> dailyData;

        // Use sample data for demo
        System.out.println("Loading daily data...");
        dailyData = bhavReader.createSampleData();

        // TODO: Load real data from Zerodha historical API
        // for (String symbol : allSymbols) {
        //     List<StockData> data = dataFetcher.getDailyData(symbol, 30);
        //     dailyData.put(symbol, data);
        // }

        // Analyze all stocks
        List<StockSignal> allSignals = dailyAnalyzer.analyzeAllStocks(dailyData);

        // Filter to get watchlist
        List<StockSignal> buySignals = allSignals.stream()
            .filter(s -> s.getSignalType() == StockSignal.SignalType.STRONG_BUY ||
                        s.getSignalType() == StockSignal.SignalType.BUY ||
                        s.getSignalType() == StockSignal.SignalType.WEAK_BUY)
            .limit(maxWatchlistSize)
            .collect(Collectors.toList());

        // Build watchlist
        watchlist.clear();
        dailySignals.clear();

        System.out.println("\n📋 TODAY'S WATCHLIST:");
        System.out.println("-".repeat(70));

        for (StockSignal signal : buySignals) {
            watchlist.add(signal.getSymbol());
            dailySignals.put(signal.getSymbol(), signal);

            System.out.printf("%-12s | Score: %3d | %-25s | Del: %.1f%%\n",
                signal.getSymbol(),
                signal.getScore(),
                signal.getSignalType().getDescription(),
                signal.getDeliveryPercent());
        }

        if (watchlist.isEmpty()) {
            System.out.println("No strong buy signals found. Using default watchlist.");
            watchlist.addAll(Arrays.asList("RELIANCE", "TCS", "INFY", "HDFCBANK", "ICICIBANK"));
        }

        System.out.println("-".repeat(70));
        System.out.println("Total stocks in watchlist: " + watchlist.size());
    }

    /**
     * Load instrument tokens for watchlist
     */
    private void loadInstrumentTokens() throws IOException {
        System.out.println("\nLoading instrument tokens...");
        dataFetcher.loadInstrumentTokens(watchlist);

        // Set tokens in ticker service
        Map<String, Long> tokens = new HashMap<>();
        for (String symbol : watchlist) {
            long token = dataFetcher.getInstrumentToken(symbol);
            if (token > 0) {
                tokens.put(symbol, token);
            }
        }
        tickerService.setInstrumentTokens(tokens);
    }

    /**
     * Setup WebSocket callbacks
     */
    private void setupTickerCallbacks() {
        // On new tick
        tickerService.setOnTick(tick -> {
            // Process tick (handled in candle callback)
        });

        // On new candle completed
        tickerService.setOnCandle(candle -> {
            // Analyze the new candle
            IntradaySignal signal = intradayAnalyzer.processCandle(candle);

            if (signal != null) {
                activeSignals.put(candle.getSymbol(), signal);

                // Check for actionable signals
                if (signal.getSignalType() == IntradaySignal.SignalType.STRONG_BUY ||
                    signal.getSignalType() == IntradaySignal.SignalType.BUY) {

                    onBuySignal(signal);

                } else if (signal.getSignalType() == IntradaySignal.SignalType.EXIT ||
                          signal.getSignalType() == IntradaySignal.SignalType.STOP_HIT) {

                    onExitSignal(signal);
                }
            }
        });

        // On connect
        tickerService.setOnConnect(() -> {
            System.out.println("✓ WebSocket connected, receiving live data...");
        });

        // On disconnect
        tickerService.setOnDisconnect(() -> {
            System.out.println("⚠️ WebSocket disconnected");
        });

        // On error
        tickerService.setOnError(error -> {
            System.err.println("❌ WebSocket error: " + error);
        });
    }

    /**
     * Handle buy signal
     */
    private void onBuySignal(IntradaySignal signal) {
        System.out.println("\n" + "🔔".repeat(5));
        System.out.println("🟢 BUY SIGNAL: " + signal.getSymbol());
        System.out.println("🔔".repeat(5));
        System.out.println(signal);

        // Calculate position size
        double riskAmount = capital * (maxRiskPerTrade / 100);
        double riskPerShare = signal.getEntryPrice() - signal.getStopLoss();
        int quantity = (int) (riskAmount / riskPerShare);

        System.out.println("\n📊 TRADE SETUP:");
        System.out.println("   Entry Price : ₹" + signal.getEntryPrice());
        System.out.println("   Stop Loss   : ₹" + signal.getStopLoss());
        System.out.println("   Target 1    : ₹" + signal.getTarget1());
        System.out.println("   Target 2    : ₹" + signal.getTarget2());
        System.out.println("   Quantity    : " + quantity + " shares");
        System.out.println("   Risk Amount : ₹" + String.format("%.2f", riskAmount));
        System.out.println("   Investment  : ₹" + String.format("%.2f", quantity * signal.getEntryPrice()));

        // Play sound alert (if available)
        playAlert();
    }

    /**
     * Handle exit signal
     */
    private void onExitSignal(IntradaySignal signal) {
        System.out.println("\n" + "⚠️".repeat(5));
        System.out.println("🔴 EXIT SIGNAL: " + signal.getSymbol());
        System.out.println("⚠️".repeat(5));
        System.out.println(signal);

        // Play sound alert
        playAlert();
    }

    /**
     * Play alert sound
     */
    private void playAlert() {
        try {
            java.awt.Toolkit.getDefaultToolkit().beep();
        } catch (Exception ignored) {
        }
    }

    /**
     * Schedule periodic tasks
     */
    private void schedulePeriodicTasks() {
        // Print status every 5 minutes
        scheduler.scheduleAtFixedRate(() -> {
            if (isRunning) {
                printStatus();
            }
        }, 5, 5, TimeUnit.MINUTES);

        // Check for market close
        scheduler.scheduleAtFixedRate(() -> {
            LocalTime now = LocalTime.now();
            if (now.isAfter(LocalTime.of(15, 30))) {
                System.out.println("\n🔔 Market closed. Stopping live trading...");
                stop();
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * Print current status
     */
    public void printStatus() {
        System.out.println("\n" + "-".repeat(70));
        System.out.println("📊 LIVE STATUS - " + java.time.LocalTime.now().toString().substring(0, 8));
        System.out.println("-".repeat(70));

        for (String symbol : watchlist) {
            ZerodhaTickerService.TickData tick = tickerService.getLatestTick(symbol);
            IntradaySignal signal = activeSignals.get(symbol);

            if (tick != null && signal != null) {
                String signalStr = signal.getSignalType().getDescription();
                System.out.printf("%-10s | LTP: %8.2f | VWAP: %8.2f | Score: %3d | %s\n",
                    symbol,
                    tick.lastPrice,
                    signal.getVwap(),
                    signal.getScore(),
                    signalStr);
            }
        }

        System.out.println("-".repeat(70));
    }

    /**
     * Stop the live trading system
     */
    public void stop() {
        isRunning = false;

        // Disconnect WebSocket
        tickerService.disconnect();

        // Shutdown scheduler
        scheduler.shutdown();

        System.out.println("\n✓ Live trading system stopped.");
    }

    /**
     * Get active signals
     */
    public Map<String, IntradaySignal> getActiveSignals() {
        return new HashMap<>(activeSignals);
    }

    /**
     * Get watchlist
     */
    public List<String> getWatchlist() {
        return new ArrayList<>(watchlist);
    }

    /**
     * Manual method to add symbols to watchlist
     */
    public void addToWatchlist(String symbol) {
        if (!watchlist.contains(symbol)) {
            watchlist.add(symbol);
            try {
                dataFetcher.loadInstrumentTokens(Collections.singletonList(symbol));
                long token = dataFetcher.getInstrumentToken(symbol);
                if (token > 0) {
                    tickerService.setInstrumentTokens(
                        Collections.singletonMap(symbol, token));
                    tickerService.subscribeSymbols(Collections.singletonList(symbol));
                }
            } catch (IOException e) {
                System.err.println("Error adding symbol: " + e.getMessage());
            }
        }
    }

    /**
     * Main entry point
     */
    public static void main(String[] args) {
        ZerodhaLiveTrading trading = new ZerodhaLiveTrading();

        // Check if credentials provided as arguments
        if (args.length >= 2) {
            trading.initialize(args[0], args[1]);
        }

        // If access token provided
        if (args.length >= 3) {
            trading.setAccessToken(args[2]);
        }

        // Start the system
        if (trading.dataFetcher.isReady()) {
            trading.start();

            // Keep running until market close or user interrupt
            try {
                System.out.println("\nPress Ctrl+C to stop...");
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                trading.stop();
            }
        } else {
            // Show instructions for authentication
            System.out.println("╔══════════════════════════════════════════════════════════════════╗");
            System.out.println("║                    ZERODHA API SETUP                             ║");
            System.out.println("╚══════════════════════════════════════════════════════════════════╝");
            System.out.println();
            System.out.println("Step 1: Create zerodha.properties file in src/main/resources/");
            System.out.println("        with your API credentials:");
            System.out.println();
            System.out.println("        api.key=your_api_key");
            System.out.println("        api.secret=your_api_secret");
            System.out.println();
            System.out.println("Step 2: Open this URL in browser to login:");
            System.out.println("        " + trading.getLoginUrl());
            System.out.println();
            System.out.println("Step 3: After login, you'll be redirected with a request_token.");
            System.out.println("        Run: java ZerodhaLiveTrading <api_key> <api_secret> <request_token>");
            System.out.println();
        }
    }
}

