-- Keep one stock_fundamentals row per symbol per day instead of overwriting.
-- Existing rows are kept and dated from calculated_at.

BEGIN;

ALTER TABLE stock_fundamentals ADD COLUMN IF NOT EXISTS as_of_date DATE;

UPDATE stock_fundamentals
SET as_of_date = COALESCE(calculated_at, last_updated, CURRENT_TIMESTAMP)::date
WHERE as_of_date IS NULL;

ALTER TABLE stock_fundamentals ALTER COLUMN as_of_date SET NOT NULL;

ALTER TABLE stock_fundamentals DROP CONSTRAINT IF EXISTS stock_fundamentals_pkey;

ALTER TABLE stock_fundamentals ADD PRIMARY KEY (symbol, as_of_date);

CREATE INDEX IF NOT EXISTS idx_stock_fundamentals_date
    ON stock_fundamentals (as_of_date);

-- Latest row per symbol, for queries that only want the current values
CREATE OR REPLACE VIEW stock_fundamentals_latest AS
SELECT DISTINCT ON (symbol) *
FROM stock_fundamentals
ORDER BY symbol, as_of_date DESC;

COMMIT;
