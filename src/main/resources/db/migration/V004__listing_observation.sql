-- §7.4: one row per tracked listing per observation day (ask data).
-- Source for floor price, ask distribution, listing counts, and sold-quantity deltas (inferred sales).
CREATE TABLE listing_observation (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id             UUID          NOT NULL REFERENCES card(id),
    external_listing_id VARCHAR(100)  NOT NULL,    -- eBay item id
    observed_at         TIMESTAMPTZ   NOT NULL,
    ask_price           NUMERIC(10,2) NOT NULL,
    listing_format      VARCHAR(20)   NOT NULL,    -- "FIXED_PRICE", "AUCTION"
    quantity_sold       INT,                       -- estimated sold quantity (fixed-price only)
    grade_source        VARCHAR(20),
    grade_value         VARCHAR(10),
    external_url        TEXT,
    raw_title           TEXT,
    created_at          TIMESTAMPTZ   DEFAULT now(),
    UNIQUE (external_listing_id, observed_at)
);

CREATE INDEX idx_listing_obs_card_time ON listing_observation(card_id, observed_at DESC);
