-- §7.3: individual transactions (SOLD + INFERRED_SALE). ASK observations live in listing_observation (V004).
CREATE TABLE price_snapshot (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id         UUID          NOT NULL REFERENCES card(id),
    sale_price      NUMERIC(10,2) NOT NULL,
    sold_at         TIMESTAMPTZ   NOT NULL,
    price_type      VARCHAR(20)   NOT NULL,    -- "SOLD", "INFERRED_SALE"
    source          VARCHAR(30)   NOT NULL,    -- "EBAY_BROWSE_INFERRED", "THECARDAPI", "EBAY_INSIGHTS", ...
    grade_source    VARCHAR(20),               -- "PSA", "BGS", "SGC", "RAW"
    grade_value     VARCHAR(10),               -- "10", "9.5", "9", "RAW"
    platform        VARCHAR(30)   NOT NULL,    -- "EBAY", "PWCC", etc.
    external_id     VARCHAR(100),              -- platform listing/transaction id (dedup key)
    external_url    TEXT,                      -- link to original listing
    raw_title       TEXT,                      -- original listing title (for audit)
    created_at      TIMESTAMPTZ   DEFAULT now()
);

-- Dedup: re-running ingestion or overlapping backfills must not duplicate transactions
CREATE UNIQUE INDEX uq_price_snapshot_ext
    ON price_snapshot(source, external_id, sold_at) WHERE external_id IS NOT NULL;

CREATE INDEX idx_price_snapshot_card_sold ON price_snapshot(card_id, sold_at DESC);
CREATE INDEX idx_price_snapshot_sold_at   ON price_snapshot(sold_at DESC);
