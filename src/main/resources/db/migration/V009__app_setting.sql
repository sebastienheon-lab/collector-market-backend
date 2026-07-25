-- §7.12: runtime-tunable operational knobs (read via a short-TTL cache; not for
-- compliance-adjacent values like retention windows — those live in application.yml).
CREATE TABLE app_setting (
    key          VARCHAR(100) PRIMARY KEY,
    value        TEXT         NOT NULL,
    value_type   VARCHAR(20)  NOT NULL,       -- "INT", "STRING", "DURATION"
    description  TEXT,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by   VARCHAR(100)                 -- audit: who last touched it
);

-- OQ-7 aging thresholds (§7.10: "start conservative")
INSERT INTO app_setting (key, value, value_type, description) VALUES
    ('tracking.decay_after_days', '14', 'INT', 'Days without engagement before a SEARCHED card demotes to DECAYED'),
    ('tracking.pause_after_days', '60', 'INT', 'Days without engagement before a DECAYED card demotes to PAUSED');

-- OQ-15 event health-check knobs
INSERT INTO app_setting (key, value, value_type, description) VALUES
    ('events.min_future_events', '2', 'INT', 'Minimum future events per active competition before the weekly health check warns'),
    ('events.check_horizon_months', '6', 'INT', 'Look-ahead window (months) for the events health check');
