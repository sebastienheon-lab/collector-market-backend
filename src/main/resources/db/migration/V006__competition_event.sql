-- §7.9: specific competition instances/stages with concrete date ranges.
-- Populated by the idempotent reference-data loader (OQ-15), not seeded here — schema vs. reference data are separate concerns.
CREATE TABLE competition_event (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    competition_id  SMALLINT     NOT NULL REFERENCES sport_competition(id),
    label           VARCHAR(100) NOT NULL,
    stage           VARCHAR(50),
    start_date      DATE         NOT NULL,
    end_date        DATE         NOT NULL,
    created_at      TIMESTAMPTZ  DEFAULT now()
);

CREATE INDEX idx_competition_event_dates ON competition_event(start_date, end_date);
