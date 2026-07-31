-- M6: Postgres-native card search. Trigram similarity on card.player_name meets the <1s search
-- NFR (§4) without a separate search engine.
--
-- pg_trgm is a TRUSTED extension since PostgreSQL 13: any role holding CREATE on the current
-- database can install it (no superuser needed). The app user `collector` has ALL PRIVILEGES on
-- collector_market_dev (M0), so this migration runs cleanly as the app user in dev and in CI
-- (Testcontainers Postgres runs as superuser). In a locked-down managed Postgres that denies even
-- trusted-extension creation to the app role, an admin must run `CREATE EXTENSION pg_trgm;` once
-- per environment BEFORE this migration; the IF NOT EXISTS below then no-ops.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- GIN + gin_trgm_ops backs both similarity() ranking and the `%` (similar-to) operator used by
-- the search query.
CREATE INDEX IF NOT EXISTS idx_card_player_name_trgm
    ON card USING gin (player_name gin_trgm_ops);
