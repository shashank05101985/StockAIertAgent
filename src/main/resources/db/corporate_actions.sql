-- Splits / bonuses used to adjust raw bhavcopy prices when building
-- stock_daily_features. stock_data itself is never modified.
--
-- factor = price before ex-date / price after ex-date
--   Split Rs 10 -> Rs 2 : factor 5
--   Bonus a:b           : factor (a + b) / b   (1:1 -> 2, 4:1 -> 5)
--
-- applied_at is NULL until StockFeatureMain has rebuilt that symbol's
-- feature rows from ex_date onward.
CREATE TABLE IF NOT EXISTS corporate_actions (
    symbol      varchar(30)   NOT NULL,
    ex_date     date          NOT NULL,
    action      varchar(20)   NOT NULL,
    factor      numeric(14,6) NOT NULL CHECK (factor > 0),
    purpose     text,
    created_at  timestamp     NOT NULL DEFAULT now(),
    applied_at  timestamp,
    PRIMARY KEY (symbol, ex_date)
);
