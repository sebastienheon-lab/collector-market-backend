-- §7.11: persistent daily aggregates computed from listing_observation before raw rows are
-- deleted by the retention job (§5.4, OQ-9). Our observations about the market, not republished listings.
CREATE TABLE market_metric_daily (
    card_id           UUID          NOT NULL REFERENCES card(id),
    grade_source      VARCHAR(20),
    grade_value       VARCHAR(10),
    metric_date       DATE          NOT NULL,
    floor_price       NUMERIC(10,2),          -- MIN ask for the (card, grade) on that day
    median_ask        NUMERIC(10,2),
    active_listings   INT           NOT NULL, -- distinct listings observed
    new_listings      INT           NOT NULL DEFAULT 0,
    delistings        INT           NOT NULL DEFAULT 0,
    computed_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (card_id, grade_source, grade_value, metric_date)
);

CREATE INDEX idx_market_metric_card_date ON market_metric_daily(card_id, metric_date DESC);
