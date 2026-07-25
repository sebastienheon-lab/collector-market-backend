-- §7.10: poller inclusion & priority tiers (OQ-7). One row per card ever tracked;
-- absence of a row means "not tracked."
CREATE TABLE card_tracking (
    card_id             UUID PRIMARY KEY REFERENCES card(id),
    tier                VARCHAR(20)  NOT NULL,   -- "SEED", "WATCHLISTED", "SEARCHED", "DECAYED"
    poll_cadence        VARCHAR(20)  NOT NULL,   -- "DAILY", "WEEKLY", "PAUSED"
    last_polled_at      TIMESTAMPTZ,
    last_engagement_at  TIMESTAMPTZ  NOT NULL,   -- most recent search or watchlist add
    added_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_card_tracking_cadence_polled ON card_tracking(poll_cadence, last_polled_at);
CREATE INDEX idx_card_tracking_engagement     ON card_tracking(last_engagement_at DESC);
