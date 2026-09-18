# Stock Analysis Tool - Short Term Trading

A Java-based stock analysis tool that analyzes **OHLC (Open, High, Low, Close)** data along with **traded and delivered quantities** from NSE Bhav Copy to identify stocks with short-term movement potential.

## Features

- **Multi-criteria Analysis**:
  - Volume breakout detection
  - Delivery percentage analysis (institutional buying indicator)
  - Price trend and momentum analysis
  - Technical indicators (RSI, Bollinger Bands, ATR, SMA/EMA)
  - Candlestick pattern recognition

- **Smart Signal Generation**:
  - Scoring system (0-100) based on multiple factors
  - Signal types: Strong Buy, Buy, Weak Buy, Hold, Avoid
  - Risk:Reward ratio calculation

- **Automatic Levels Calculation**:
  - Entry price
  - Stop-loss (ATR-based and support-based)
  - Three target levels with R:R ratios

## Project Structure

```
src/main/java/org/example/
├── Main.java                          # Entry point with interactive mode
├── model/
│   ├── StockData.java                 # OHLC + Volume data model
│   └── StockSignal.java               # Trading signal with targets
├── analysis/
│   ├── StockAnalyzer.java             # Main analysis engine
│   └── TechnicalIndicators.java       # Technical indicator calculations
└── reader/
    └── BhavCopyReader.java            # CSV file reader for Bhav Copy
```

## Usage

### 1. Demo Mode (No data files needed)
```bash
# Run with sample data
java -cp target/classes org.example.Main demo
```

### 2. With Real Bhav Copy Data
```bash
# Point to directory containing bhav copy CSV files
java -cp target/classes org.example.Main /path/to/bhav/files
```

### Interactive Commands
Once running, you can use:
- `list` - Show all available stock symbols
- `top N` - Show top N stocks by score (e.g., `top 5`)
- `SYMBOL` - Show detailed analysis for a stock (e.g., `RELIANCE`)
- `exit` - Exit the program

## Bhav Copy CSV Format

The tool supports multiple CSV formats. Expected columns include:

| Column | Alternate Names |
|--------|-----------------|
| Symbol | SYMBOL, TCKRSYMB, SCRIP |
| Date | DATE, TIMESTAMP, TRADE_DT |
| Open | OPEN, OPEN_PRICE |
| High | HIGH, HIGH_PRICE |
| Low | LOW, LOW_PRICE |
| Close | CLOSE, CLOSE_PRICE, LAST |
| Volume | TTL_TRD_QNTY, TRADED_QTY, VOLUME |
| Delivery Qty | DELIV_QTY, DELIVERED_QTY |
| Delivery % | DELIV_PER, DELIVERY_PERCENT |

### Sample CSV Format
```csv
SYMBOL,SERIES,DATE,PREV_CLOSE,OPEN,HIGH,LOW,CLOSE,TTL_TRD_QNTY,TURNOVER,DELIV_QTY,DELIV_PER
RELIANCE,EQ,2026-08-03,2450.00,2455.50,2478.00,2442.00,2468.75,8500000,21050000000,4250000,50.00
```

## Analysis Criteria & Scoring

### 1. Volume Analysis (Max 20 points)
- Volume breakout (>1.5x average): +15 points
- Above average volume: +8 points
- Rising volume trend: +5 points

### 2. Delivery Analysis (Max 25 points)
- Very high delivery (≥60%): +20 points
- High delivery (≥50%): +15 points
- Good delivery (≥40%): +10 points
- Delivery breakout: +5 points

### 3. Price Trend (Max 20 points)
- Strong uptrend (Price > SMA5 > SMA10 > SMA20): +12 points
- Price breakout above resistance: +8 points
- Strong momentum: +5 points

### 4. Technical Indicators (Max 20 points)
- RSI in bullish zone (50-70): +8 points
- Above Bollinger middle band: +6 points
- Breakout from consolidation: +6 points

### 5. Candlestick Patterns (Max 15 points)
- Bullish Engulfing/Marubozu: +15 points
- Hammer/Bullish candle: +10 points

### Signal Types
| Score | Signal |
|-------|--------|
| ≥70 | Strong Buy |
| ≥55 | Buy |
| ≥40 | Weak Buy |
| ≥25 | Hold |
| <25 | Avoid |

## Stop-Loss & Target Calculation

### Stop-Loss
The tool uses the **most conservative** of three methods:
1. **ATR-based**: Current Price - (1.5 × ATR)
2. **Support-based**: Below recent support level
3. **Percentage-based**: 3% below current price

### Targets
- **Target 1**: 1.5:1 Risk:Reward
- **Target 2**: 2.5:1 Risk:Reward
- **Target 3**: 4:1 Risk:Reward

Targets are also adjusted based on resistance levels.

## Sample Output

```
╔══════════════════════════════════════════════════════════════╗
║  RELIANCE  -  Score: 75/100
║  Signal: Strong Buy - High Probability
╠══════════════════════════════════════════════════════════════╣
║  Current Price : ₹2468.75
║  Entry Price   : ₹2470.00
║  Stop Loss     : ₹2395.00 (Risk: 3.05%)
╠══════════════════════════════════════════════════════════════╣
║  Target 1      : ₹2580.00 (+4.50%)
║  Target 2      : ₹2655.00 (+7.55%)
║  Target 3      : ₹2770.00 (+12.20%)
║  Risk:Reward   : 1:2.50
╠══════════════════════════════════════════════════════════════╣
║  Delivery %    : 52.00%
║  Volume Change : 85.00% vs Avg
║  Breakouts     : Vol:✓ | Price:✓ | Del:✓
╠══════════════════════════════════════════════════════════════╣
║  Reason: Volume breakout (1.9x avg). High delivery (52.0%). 
║          Strong uptrend. RSI bullish (58.5).
╚══════════════════════════════════════════════════════════════╝
```

## Building

```bash
# Using Maven
mvn clean compile

# Package as JAR
mvn package
```

## Requirements

- Java 17 or higher
- Maven (for building)

## Disclaimer

This tool is for **educational and research purposes only**. Trading in stocks involves risk. Please do your own research and consult with a financial advisor before making any investment decisions.

## License

MIT License

