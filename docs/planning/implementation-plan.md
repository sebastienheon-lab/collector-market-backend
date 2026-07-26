# Collector Market — Backend Implementation Plan

Living checklist for taking the Phase 1 MVP from planning to staging. Each milestone has a Definition of Done — check boxes as you go, and this file doubles as project status.

**Companion:** `docs/planning.md` (source of truth for design). References like §5.4 or OQ-7 point there.

---

## How to use this document

- Milestones are ordered by dependency; complete roughly in sequence.
- Some can run in parallel (noted per-milestone).
- Each task is a checkbox. Commit this file when you check things off — it lives in git alongside the code and is part of the decision journal.
- If a task turns out wrong or missing, edit the plan. Living document, not a contract.

---

## M0 — Environment Setup

**Objective:** local dev machine ready to build and run Spring Boot 3 + Postgres.

### Software to install

- [x] **Java 25 JDK** (Eclipse Temurin recommended) — verify: `java --version`
- [x] **Maven 3.9+** — verify: `mvn --version` (bundled with IntelliJ if you use it)
- [x] **Docker Desktop** (for local Postgres; native install works too)
- [x] **Git** — verify: `git --version`
- [x] **IntelliJ IDEA Community** (free, best-in-class for Java) or **VS Code + Extension Pack for Java**
- [x] **HTTPie** / **Postman** / `curl` for API testing
- [x] **DBeaver** or **pgAdmin** (optional — DB browsing)

### Accounts

- [x] **eBay developer account** at https://developer.ebay.com (OQ-1: free tier)
  - [x] Create an application; capture App ID (Client ID) and Cert ID (Client Secret)
  - [x] Record the daily Browse API call quota — this is the OQ-7 ingestion budget
  - [x] Store credentials in a password manager; **never commit them**

### Local infrastructure

- [x] Postgres 16 running on port 5432 (via `docker-compose.yml`)
- [x] Database `collector_market_dev` created
- [x] User `collector` with `ALL PRIVILEGES` on the `collector_market_dev` database (not a superuser)

### Verification

- [x] `java --version` → 25
- [x] `mvn --version` → Maven 3.9+ on JDK 25
- [x] `docker ps` shows the Postgres container
- [x] `psql "postgres://collector@localhost:5432/collector_market_dev"` connects

**DoD:** a fresh clone + `mvn spring-boot:run` (after M1) would work end-to-end.

---

## M1 — Project Bootstrap

**Prereqs:** M0.
**Objective:** `collector-market-backend` is a runnable Spring Boot app with a health check.

- [x] Generate project via [Spring Initializr](https://start.spring.io/) — Spring Boot 3.x, Java 25, Maven
- [x] Starter dependencies: Spring Web, Spring WebFlux, Spring Data JPA, Spring Cache, Flyway, PostgreSQL Driver, Actuator, Validation, Configuration Processor, Lombok (optional)
- [x] Commit generated skeleton to the backend repo
- [x] Add `docker-compose.yml` for local Postgres
- [x] `application.yml`: DB connection, Flyway settings, actuator exposure, logging levels
- [x] `application-local.yml` template (gitignored) for developer overrides
- [x] Verify `/actuator/health` returns 200
- [x] Update README with local run instructions

**DoD:** `mvn spring-boot:run` starts the app; `curl localhost:8080/actuator/health` returns 200.

---

## M2 — Database Schema (Flyway migrations)

**Prereqs:** M1.
**Objective:** All Phase 1 tables from §7 exist via versioned migrations.

Migrations (one file per table for readable git history):

- [x] `V001__sport.sql` — §7.2 + seed rows (baseball, football, basketball, hockey, soccer, multi)
- [x] `V002__card.sql` — §7.1
- [x] `V003__price_snapshot.sql` — §7.3 (incl. unique dedup index)
- [x] `V004__listing_observation.sql` — §7.4
- [x] `V005__sport_competition.sql` — §7.8 + seed rows (World Cup, NFL Playoffs, UCL, Olympics, NBA Playoffs, MLB Postseason)
- [x] `V006__competition_event.sql` — §7.9
- [x] `V007__card_tracking.sql` — §7.10
- [x] `V008__market_metric_daily.sql` — §7.11
- [x] `V009__app_setting.sql` — §7.12 + defaults (OQ-7 thresholds, OQ-15 event health-check knobs)

Reference-data loader (OQ-15):

- [x] Idempotent startup loader (separate from Flyway) for `sport_competition` and `competition_event`
- [x] Seed files under `src/main/resources/seeds/` (YAML preferred)

**DoD:** `mvn flyway:migrate` produces the full schema; `SELECT count(*) FROM sport` returns 6 (baseball, football, basketball, hockey, soccer, multi — soccer added to cover the FIFA World Cup / UEFA Champions League seed competitions in §7.8).

---

## M3 — eBay Browse API Client

**Prereqs:** M1. Can run in parallel with M2.
**Objective:** Authenticated WebClient wrapper that respects rate limits.

- [x] OAuth 2.0 Client Credentials flow (cache token, auto-refresh before expiry)
- [x] `WebClient` bean: base URL, timeouts, exponential backoff on 429/5xx
- [x] `EbayBrowseClient`: `searchItems(query, category, filters)` and `getItem(itemId)`
- [x] Daily call counter with midnight reset — hard-stop when approaching the OQ-1 free-tier limit
- [x] Response mapping to internal DTOs (avoid leaking eBay JSON shape into services)
- [x] Recorded fixtures for integration tests (do not burn live quota in CI)
- [x] Log every call with a request-cost tag

**DoD:** `EbayBrowseClient.searchItems("2018 Topps Ohtani", tradingCardsCategoryId)` returns parsed DTOs from the live API in a smoke test.

---

## M4 — Card Catalog & Normalization

**Prereqs:** M2.
**Objective:** Rule-based parser + hand-curated seed catalog (§10 Phase 1, OQ-2).

- [x] Hand-curate initial seed catalog (~a few hundred rows: rookies of the last 3 years in top 4 US sports) as YAML — 159 rows, reviewed and committed
- [x] Seed loader for the `card` table
- [x] Title parser: extract year (4-digit), player name (lookup against catalog + player list), set name, card number
- [x] Grade normalizer: recognize `PSA 10`, `BGS 9.5`, `SGC 10`, `Gem Mint 10`, raw
- [x] Match strategy: exact catalog hit → canonical card ID; unmatched → logged for triage
- [x] `unmatched_listing` staging table (not in §7 — added as `V011__unmatched_listing.sql`; V010 was already taken by M3's api_call_counter)
- [x] Unit tests with representative eBay title fixtures (pull real examples)

**DoD:** a batch of 50 representative eBay titles yields canonical card IDs for the seeded scope and logged unmatched rows for the rest.

---

## M5 — Ingestion Pipeline

**Prereqs:** M2, M3, M4.
**Objective:** Phase 1 roadmap ingestion jobs from §11 wired up end-to-end.

- [x] `CardTracker` service — reads/writes `card_tracking` per §7.10 lifecycle
  - [x] ~~Insert on user search (`SEARCHED`, `DAILY`)~~ — **changed by session decision:** search no longer inserts tracking rows; `CardTracker.recordEngagement` is a no-op UPDATE, ready for M6's price-history endpoint to call on click-through. Full demand-driven tracking (inserting on search) waits for Phase 2 accounts.
  - [x] Seed cards loaded at startup (`SEED`, `DAILY`)
  - [x] Watchlist upsert — stub for Phase 2 (`CardTracker.upsertWatchlisted`, implemented per §7.10 semantics but not called from anywhere yet)
- [x] `Poller` — `@Scheduled` job
  - [x] Selects cards due to poll based on tier + `last_polled_at` + cadence (cadence hours read from `app_setting`, not hardcoded)
  - [x] Calls `EbayBrowseClient.searchItems`
  - [x] Writes `listing_observation` rows
- [x] `InferredSaleDetector`
  - [x] Detects `quantity_sold` deltas between consecutive fixed-price observations
  - [x] Writes `price_snapshot` with `price_type = INFERRED_SALE`
- [x] `DailyAggregator`
  - [x] Computes one `market_metric_daily` row per (card, grade, day) from that day's observations
  - [x] **Runs before** the retention job on the same day — `NightlyPipelineJob` is one `@Scheduled` method calling both in sequence, not two separately-scheduled jobs
- [x] `RetentionJob` — part of the nightly pipeline (not separately scheduled — see above)
  - [x] Delete `listing_observation` rows older than `retention.listing-observation-days` (default 7)
  - [x] Nullify linkback fields on `price_snapshot` older than `retention.price-snapshot-linkback-days` (default 30)
- [x] `AgingJob` — nightly, 03:30 UTC
  - [x] `SEARCHED` → `DECAYED` after `tracking.decay_after_days`
  - [x] `DECAYED` → `PAUSED` (poll_cadence, not tier) after `tracking.pause_after_days`
- [x] Events health check — weekly, Monday 04:00 UTC
  - [x] Warn (log only, no external notifications in MVP) when any active competition has < `events.min_future_events` events within `events.check_horizon_months` months

**DoD:** verified end-to-end via Testcontainers (`IngestionPipelineIntegrationTest`) against a real Postgres: poll (mocked eBay client) → `listing_observation` fills up → an inferred quantity-sold delta produces `price_snapshot` rows → next-day aggregation populates `market_metric_daily` → retention prunes both `listing_observation` and `price_snapshot` linkback fields correctly. Also verified live against dev-postgres with the full 159-card seed: `CardTracker` seed-tracks all of them (`SEED`/`DAILY`) cleanly on every boot.

---

## M6 — REST API Endpoints

**Prereqs:** M2 (schema). Can start in parallel with M5 (returns empty results until data lands).
**Objective:** All Phase 1 endpoints from §8.

- [ ] `GET /api/v1/cards/search?q=&sport=&page=&size=` (§8.1)
  - [ ] Postgres full-text search or `pg_trgm` GIN index on `player_name` to meet <1s NFR (note: `CREATE EXTENSION pg_trgm` requires an admin role, not the app user — run once per environment)
  - [ ] Records `card_tracking` engagement on hit (drives OQ-7 demand layer)
- [ ] `GET /api/v1/cards/{cardId}` (§8.4)
- [ ] `GET /api/v1/cards/{cardId}/market?grade=` (§8.3)
- [ ] `GET /api/v1/cards/{cardId}/prices?days=&grade=` (§8.2)
  - [ ] `sales`, `floorHistory`, `events`, `relatedCards` arrays per spec
  - [ ] `relatedCards` populated only when primary series is empty (F-11, OQ-5)
  - [ ] **Never** mix SOLD / INFERRED_SALE / ASK in one array (F-07)
- [ ] `GET /api/v1/cards/{cardId}/grades?days=` (§8.5)
- [ ] Springdoc OpenAPI spec at `/v3/api-docs` and `/swagger-ui.html`
- [ ] Global exception handler → RFC 7807 problem+json responses
- [ ] Jakarta Validation on inputs (bad `days`, unknown `sport`, etc.)
- [ ] CORS configured for the separate frontend origin (OQ-4)

**DoD:** all endpoints respond correctly against seeded data; OpenAPI JSON reachable; contract tests for F-07 and F-11 pass.

---

## M7 — Cross-Cutting Concerns

**Prereqs:** M5, M6.
**Objective:** production-shape polish.

- [ ] Structured logging (JSON in prod, human-readable in dev)
- [ ] Micrometer metrics: eBay call count/quota headroom, job durations, error rates per endpoint
- [ ] Caffeine cache on popular search queries + card detail lookups (§9)
- [ ] Config profiles: `local`, `staging`, `prod`
- [ ] Secrets via env vars only (never in `application.yml`)
- [ ] Health indicators for DB and eBay client
- [ ] Graceful shutdown so in-flight jobs complete

**DoD:** metrics show eBay call count rising as expected; logs queryable; caching demonstrably reduces DB load on repeated searches.

---

## M8 — Testing & CI

**Prereqs:** runs alongside M4–M6.
**Objective:** enough coverage that credibility invariants can't silently break.

- [ ] Unit tests: normalizer, inferred-sale detection, aging tier transitions, grade parser
- [ ] Integration tests: each endpoint against Testcontainers Postgres
- [ ] **Contract test:** F-07 — SOLD, INFERRED_SALE, ASK never appear together in one series
- [ ] **Contract test:** F-11 — empty primary series returns non-null `relatedCards` (when siblings exist)
- [ ] Fixture-based eBay client tests (zero live calls in CI)
- [ ] GitHub Actions workflow: `mvn verify` on every PR
- [ ] Branch protection on `main`: require green CI

**DoD:** `mvn verify` green in CI; the two contract tests fail loudly if the underlying invariants break.

---

## M9 — Staging Deployment

**Prereqs:** M1–M8. **Blocked on:** OQ-3 provider decision.
**Objective:** public URL, real eBay traffic, real managed Postgres.

- [ ] Resolve OQ-3 (Railway / Fly.io / AWS — pick one)
- [ ] Managed Postgres provisioned
- [ ] Platform secrets: eBay App ID/Cert ID, DB URL
- [ ] Dockerfile for the Spring Boot app
- [ ] Deploy pipeline (GitHub Actions → provider)
- [ ] Domain + TLS
- [ ] Uptime pings + error alerting (Sentry or platform-native)
- [ ] Runbook: how to restart, check job status, inspect logs, rotate eBay creds

**DoD:** staging URL live; endpoints reachable; scheduled jobs verifiably run daily on the deployed instance.

---

## M10 — Frontend Handoff

**Objective:** `collector-market-frontend` can start against a stable backend.

- [ ] OpenAPI spec exported and linked from the frontend repo's README
- [ ] Seed a small demo dataset in staging so frontend devs see real responses
- [ ] Document staging API URL, CORS setup, and sample requests

**DoD:** cloning `collector-market-frontend` and pointing it at staging returns real data.

---

## Deferred / out of scope for MVP

- Phase 2: user accounts, watchlist, price alerts (F-12..F-15)
- Phase 3: portfolio, dashboard, analytics, news (F-16..F-20)
- OQ-6: monetization (revisit before Phase 2)
- OQ-10: eBay Marketplace Insights (revisit if inferred-sales coverage proves thin)
- Auction inferred-sales (§5.1.1 — explicitly out of scope)
- Admin UI for `app_setting` (SQL editing is fine at MVP scale)

---

*Living document — edit as reality diverges from the plan. Cross-reference planning.md for the "why" behind any step.*
