-- M5: poller.daily_cadence_hours wasn't seeded in V009 (that migration scoped itself to the
-- OQ-7 aging thresholds and OQ-15 event health-check knobs only). The Poller (M5) needs it.
INSERT INTO app_setting (key, value, value_type, description) VALUES
    ('poller.daily_cadence_hours', '24', 'INT', 'Hours between polls for DAILY-cadence tracked cards');
