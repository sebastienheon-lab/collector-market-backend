-- M4: staging table for eBay titles the parser/matcher couldn't resolve to a seeded card (§10).
-- matched_card_id starts NULL; a human triaging the queue can backfill it (or create a new
-- canonical card and link to that) rather than the row being deleted - this is how parallels
-- and out-of-seed cards organically enter the catalog.
CREATE TABLE unmatched_listing (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    raw_title         TEXT         NOT NULL,
    observed_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    extracted_fields  JSONB,
    matched_card_id   UUID REFERENCES card(id),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Primary access pattern: the triage queue of not-yet-resolved rows, newest first.
CREATE INDEX idx_unmatched_listing_unresolved
    ON unmatched_listing(observed_at DESC) WHERE matched_card_id IS NULL;
