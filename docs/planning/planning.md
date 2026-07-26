# Collector Market — Project Planning Document

> **Status:** Planning phase  
> **Repository:** `collector-market-backend` (this doc lives at `/docs/planning.md`)  
> **Stack:** Java / Spring Boot · PostgreSQL · React  
> **Audience:** Public product for collectors — MVP scope is sports cards (all sports); the platform is named for future collectible categories  
> **MVP:** Search a card and see the current market (live listings, floor price) plus a forward-accruing history of our own observations (floor over time and inferred sales)

> ⚠️ **v1.1 pivot:** The original plan assumed sold-listing data was available from the eBay Browse API. It is not — sold data is gated behind the limited-release Marketplace Insights API. The data strategy has been revised to a **hybrid model** (see §5).

> **v1.8 rename:** the project is now **Collector Market** (repos: `collector-market-backend`, `collector-market-frontend` to follow). Sports cards remain the explicit MVP scope — the name change doesn't expand it. See §9 for repository structure.

---

## 1. Project Vision

A public web application that lets sports card collectors search any card and immediately see real market data: the current market state (live listings, floor price, ask distribution), observed and inferred sales, trends over time, grade-level price spreads, and volume activity. The goal is to be the fastest, cleanest way to answer the question *"What is this card actually worth right now?"*

At launch, the strongest signal is the **current market view** (what the card is listed for right now, by grade). Historical depth accrues from our own ingestion — floor price over time and inferred sales from fixed-price quantity deltas. No third-party sold-data backfill: after evaluating the available options (see §5.1.2), we determined that shipping without deep historical depth is preferable to building on a legally undefensible dependency. Our persistent dataset is entirely computed by us — daily market aggregates and detected transactions — not stored raw listing data (see §5.4). That's the moat.

---

## 2. User Personas

| Persona | Goal | Key Need |
|---|---|---|
| **Casual Collector** | Know if a card is worth buying or selling | Simple price history, no noise |
| **Active Trader** | Spot trends and act quickly | Price movement %, volume signals |
| **Portfolio Holder** | Track collection value over time | Watchlist, portfolio valuation |
| **New Collector** | Understand the market | Clear UI, grade explanations |

---

## 3. Functional Requirements

### 3.1 MVP — Phase 1

| ID | Requirement | Priority |
|---|---|---|
| F-01 | User can search a card by player name, year, set, and card number | Must Have |
| F-02 | System returns a list of matching cards with disambiguation | Must Have |
| F-03 | User can view a **current market view** for a selected card: active listings, floor (lowest ask), median ask, listing count — per grade | Must Have |
| F-04 | User can view a price history chart for a selected card, built from our own accrued observations (floor over time, inferred sales). Depth grows from the point of first tracking; no deep historical backfill in MVP | Must Have |
| F-05 | Price history is filterable by time range (30 / 90 / 180 / 365 days) | Must Have |
| F-06 | Price history is filterable by grade (Raw, PSA 9, PSA 10, BGS 9.5, etc.) | Must Have |
| F-07 | Charts clearly distinguish price types — **SOLD**, **INFERRED_SALE**, and **ASK** (floor) series are never silently mixed | Must Have |
| F-08 | Each data point links back to the original listing where a URL exists, within the source-retention window (see §5.4) | Must Have |
| F-09 | Summary stats shown: last sale, average, high, low, # of sales, current floor | Must Have |
| F-10 | Data refreshes on a scheduled basis (at minimum daily) | Must Have |
| F-11 | Cards with no sold history show an explicit empty state (not a broken chart) plus related-card suggestions; related cards are never presented as this card's price estimate | Must Have |

> Note: F-07 is a credibility requirement. Asking prices and transaction prices are different signals; mixing them in one undifferentiated line would undermine trust in the product. F-11 extends the same principle to empty states.

### 3.2 Phase 2 — Watchlist & Accounts

| ID | Requirement |
|---|---|
| F-12 | User can register and log in (email + password) |
| F-13 | Logged-in user can add cards to a watchlist |
| F-14 | Watchlist shows latest price and % change since added |
| F-15 | User receives email alert when a watched card moves ±X% |

### 3.3 Phase 3 — Portfolio & Analytics

| ID | Requirement |
|---|---|
| F-16 | User can log cards they own with purchase price |
| F-17 | Portfolio shows total estimated value and P&L |
| F-18 | Market dashboard: top movers, most searched, highest volume |
| F-19 | Grade premium analysis (price delta between Raw → PSA 9 → PSA 10) |
| F-20 | Player news feed correlated with price movement |

---

## 4. Non-Functional Requirements

| Category | Requirement |
|---|---|
| **Performance** | Card search results return in < 1 second |
| **Performance** | Price history chart loads in < 2 seconds |
| **Availability** | 99.5% uptime target |
| **Scalability** | Architecture must handle growing historical data (time-series volume) |
| **Data freshness** | eBay sold data updated at least every 24 hours |
| **Security** | No sensitive card/user data exposed in public API responses |
| **Compliance** | eBay API usage must respect their ToS (rate limits, attribution) |

---

## 5. Data Sources & Integrations

### 5.1 Data Strategy — Hybrid Model (revised)

> **Correction from v1.0:** The eBay Browse API does **not** return sold/completed listings. It is designed for active listings only. Sold-price data is gated behind the **Marketplace Insights API**, a limited-release API restricted to approved business partners (and covering only ~90 days of history). The original `filter=soldItemsOnly:true` assumption was wrong. The strategy below replaces it.

The revised approach combines four sources, in priority order:

#### 5.1.1 eBay Browse API — active listings (owned dataset, accrues forward)

- **Endpoint:** `GET /buy/browse/v1/item_summary/search` + `GET /buy/browse/v1/item/{item_id}`
- **Authentication:** OAuth 2.0 Client Credentials (app-level, no user login needed)
- **Rate limits:** Must be respected — use a queue/backoff strategy
- **Data available:** Ask price, listing title, condition, item URL, seller, quantity info

What we build from it:

1. **Market-state metrics (per card, per grade):** floor price (lowest active ask), median ask, active listing count, new listings/day, delistings/day. Floor price over time is a legitimate, defensibly-sourced price-evolution series available from day one.
2. **Inferred sales:** for fixed-price listings, poll the item detail daily and watch the estimated sold quantity. When it increments, record a real transaction at a known price (`price_type = INFERRED_SALE`). Auctions cannot be reliably captured this way (the listing disappears at end; last observed bid underestimates the final price) — auction inference is out of scope for MVP.

**eBay App registration required at:** https://developer.ebay.com

#### 5.1.2 Third-party sold-data APIs — evaluated and rejected for MVP

**Decision:** no third-party sold-data source in MVP. See OQ-8 for rationale — The Card API was the leading candidate but its ToS shifts marketplace-compliance risk to the caller, which is incompatible with a public product. Other surveyed options (below) share the same underlying problem (unlicensed scraping) or are inaccessible.

Sold-price data in MVP therefore comes exclusively from our own inferred-sales pipeline (§5.1.1). This weakens the launch pitch — no deep historical backfill — but the alternative was a legally undefensible dependency on a scraped source.

Surveyed options (retained for reference):

| Provider | History? | Access | Notes |
|---|---|---|---|
| The Card API | Yes (sold) | Free tier | Evaluated, rejected (OQ-8): ToS disclaims marketplace-compliance |
| Card Hedge | Yes (Price History API) | Apply for key; likely paid | Same underlying provenance concern; also paid |
| SportsCardsPro / PriceCharting | **No** — current values only | Paid subscription | Not history; not useful for backfill |
| Card Ladder / Market Movers | Yes (deep) | Consumer apps, no public API | Not integrable |

#### 5.1.3 eBay Marketplace Insights API — deferred (OQ-10)

The only known route to sold-price data with clean provenance. **Not pursued for MVP** — first iteration ships on Browse API alone. Documented here so a future iteration can pick it up: limited-release API, application required, if granted provides ~90 days of true sold data with ToS-clean redistribution.

#### 5.1.4 Data-type discipline

Every stored price carries a `price_type` (`SOLD` | `INFERRED_SALE` | `ASK`) and a `source`. The API and UI must always distinguish these series (see F-07). Sold data is the gold standard; ask/floor data is a supplementary signal, never presented as transaction prices.

### 5.2 Future Integrations (Phase 2+)

| Source | Data | Notes |
|---|---|---|
| PSA Population Report | Graded card supply (how many exist at each grade) | No official API — may require scraping |
| Mavin.io | Supplementary price aggregation | Third-party aggregator |
| PWCC / Alt / Goldin | High-end auction results | No public APIs currently |

### 5.3 Card Name Normalization (Critical Challenge)

eBay listing titles are free-text and inconsistent. The same card may appear as:

- `"2018 Topps Update Shohei Ohtani RC #US1 PSA 10"`
- `"Ohtani Rookie Topps 2018 Graded 10 Gem Mint"`
- `"Shohei Ohtani 2018 Topps Update US1 Rookie Card"`

A normalization/parsing layer is required to group listings into a canonical card identity. This is the hardest technical problem in the app and should be designed early.

**Approach options:**
- Rule-based parser (regex + known set/year dictionaries)
- Fuzzy matching against a reference card catalog
- AI-assisted classification (Claude API as an internal service)
- Combination of the above

### 5.4 Data Retention Policy (OQ-9)

eBay's Browse API terms permit retaining listing data "as required" for the operational purpose, then require disposal. Our windows below are policy, not statute — revisit if terms change or if a specific window proves too short in practice.

| Data | Retention | Purpose the window serves |
|---|---|---|
| `listing_observation` rows (raw ask + listing metadata) | **7 days rolling** | Enough to compute daily aggregates and detect inferred sales across consecutive polls, with a buffer for missed runs. Deleted by nightly job after that. |
| `price_snapshot.external_url`, `raw_title`, `external_id` (linkback + audit fields) | **30 days rolling** | Supports F-08 (linkback to source) for a useful window; most eBay listings themselves expire well within this. Nullified by nightly job after that; the price/date/card/grade tuple is retained permanently. |
| `market_metric_daily` (derived aggregates) | **Permanent** | Our observations *about* the market on a given day. Not republished listing data. |
| `price_snapshot` (transaction tuples: price, date, card, grade) | **Permanent** | Synthesized records of transactions we detected; not raw listing data. |

**Enforcement:** a single retention job runs nightly, deleting expired `listing_observation` rows and nullifying expired linkback fields on `price_snapshot`.

**Where the values live:** retention windows are Spring `@ConfigurationProperties` under the `retention.*` prefix in `application.yml`, with defaults shipped at 7 and 30 days as above. **Not** in the `app_setting` table (§7.12) — deliberately. Retention has compliance implications and belongs in code review, not a runtime settings screen where a mistyped "70" for "7" wouldn't get caught. Environment-specific overrides via env vars are supported (standard Spring config precedence) but still require a deploy to change. See §9 for the general rule on what goes where.

**Trade-off accepted:** older sales lose their linkback to source (F-08). This is unavoidable given the retention constraint and is documented in F-08.

---

## 6. System Architecture

```
┌──────────────────────────────────────────────────────────┐
│                        Frontend                          │
│                  React SPA (Vite/CRA)                    │
│         Search · Price Chart · Watchlist · Portfolio     │
└─────────────────────────┬────────────────────────────────┘
                          │ REST / JSON
┌─────────────────────────▼────────────────────────────────┐
│                   Spring Boot API                        │
│                                                          │
│  Controllers → Services → Repositories                   │
│                                                          │
│  ┌──────────────┐   ┌────────────────┐                  │
│  │ Search       │   │ Price/Trend    │                  │
│  │ Service      │   │ Service        │                  │
│  └──────────────┘   └────────────────┘                  │
│  ┌──────────────┐   ┌────────────────┐                  │
│  │ eBay Client  │   │ Scheduler      │                  │
│  │ (WebClient)  │   │ (@Scheduled)   │                  │
│  └──────────────┘   └────────────────┘                  │
│  ┌──────────────┐   ┌────────────────┐                  │
│  │ Normalizer   │   │ Cache Layer    │                  │
│  │ Service      │   │ (Spring Cache) │                  │
│  └──────────────┘   └────────────────┘                  │
└────────────┬─────────────────────┬───────────────────────┘
             │                     │
┌────────────▼───────┐   ┌────────▼────────────┐
│    PostgreSQL       │   │  eBay Browse API     │
│  - cards           │   │  (active listings)   │
│  - price_snapshots │   ├─────────────────────┤
│  - listing_        │   │  3rd-party sold-data │
│    observations    │   │  API (backfill)      │
│  - users           │   └─────────────────────┘
│  - watchlist_items │
└────────────────────┘
```

**Ingestion services:** the Scheduler drives two jobs — (1) an *active-listing poller* that snapshots tracked listings daily (computes floor/ask metrics, detects sold-quantity increments → inferred sales) and (2) a *backfill job* that imports third-party sold data per card. Which cards get polled is governed by the ingestion strategy decision (OQ-7).

---

## 7. Data Model

### 7.1 `card` — Canonical card catalog

```sql
CREATE TABLE card (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_name     VARCHAR(100)  NOT NULL,
    year            SMALLINT      NOT NULL,
    brand           VARCHAR(100),              -- "Topps", "Panini Prizm", etc.
    set_name        VARCHAR(150),              -- "Update Series", "Mosaic"
    card_number     VARCHAR(30),               -- "#US1", "#/99"
    sport_id        SMALLINT      NOT NULL REFERENCES sport(id),
    is_rookie       BOOLEAN       DEFAULT FALSE,
    parallel        VARCHAR(100),              -- "Silver Prizm", "Gold", NULL = base
    print_run       INT,                       -- NULL = unlimited
    created_at      TIMESTAMPTZ   DEFAULT now(),
    updated_at      TIMESTAMPTZ   DEFAULT now()
);
```

### 7.2 `sport` — Sport lookup table

> **Restored decision (originally §7.1 update in the prior doc version):** sports are modeled as a **lookup table, not a Postgres ENUM**. Rationale: adding a sport requires no DDL migration, the table carries UI metadata, and FK relationships stay clean. The public API continues to accept human-readable sport codes (e.g., `?sport=football`) — internal IDs are an implementation detail and never appear in API responses.

```sql
CREATE TABLE sport (
    id            SMALLINT     PRIMARY KEY,
    code          VARCHAR(30)  NOT NULL UNIQUE,  -- "baseball", "football", "basketball", "hockey", "soccer", "multi"
    display_name  VARCHAR(50)  NOT NULL,         -- "Baseball", "Football"
    icon_key      VARCHAR(50),                   -- frontend icon reference
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE
);
```

The special code `multi` exists for cross-sport contexts (e.g., the Olympics as a competition spanning all sports — see §7.8).

### 7.3 `price_snapshot` — Individual transactions (sold + inferred)

Stores transaction events only (`SOLD` from third-party/official sources, `INFERRED_SALE` from our own quantity-tracking). Ask-price observations live in `listing_observation` (§7.4).

```sql
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
```

**Retention (see §5.4):** the transaction tuple (`sale_price`, `sold_at`, `card_id`, grade, `price_type`, `source`) is **permanent** — this is a synthesized record of a transaction we detected, not republished listing data. The linkback and audit fields (`external_url`, `raw_title`, `external_id`) are **transient** — nullified 30 days after `sold_at` by a nightly job. F-08 linkback is guaranteed only within that window.

### 7.4 `listing_observation` — Daily active-listing snapshots (ask data)

One row per tracked listing per observation day. Source for floor price, ask distribution, listing counts, and the sold-quantity deltas that produce inferred sales.

```sql
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
```

**Derived metrics (computed, or materialized later if needed):** per card/grade/day — floor (MIN ask), median ask, active count. Inferred sale detection: `quantity_sold` increment between consecutive observations of the same listing → insert into `price_snapshot` with `price_type = 'INFERRED_SALE'`.

**Retention (see §5.4):** this table is **transient** — rows are deleted 7 days after `observed_at` by a nightly job. Daily aggregates are extracted into `market_metric_daily` (§7.11) before deletion. Total size stays bounded regardless of how long the product runs.

### 7.5 `user_account` — Registered users (Phase 2)

```sql
CREATE TABLE user_account (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255)  NOT NULL,
    display_name    VARCHAR(100),
    created_at      TIMESTAMPTZ   DEFAULT now()
);
```

### 7.6 `watchlist_item` — User watchlists (Phase 2)

```sql
CREATE TABLE watchlist_item (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID          NOT NULL REFERENCES user_account(id),
    card_id         UUID          NOT NULL REFERENCES card(id),
    grade_filter    VARCHAR(10),               -- watch a specific grade or NULL = all
    added_at        TIMESTAMPTZ   DEFAULT now(),
    UNIQUE(user_id, card_id)
);
```

### 7.7 `portfolio_item` — User collections (Phase 3)

```sql
CREATE TABLE portfolio_item (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID          NOT NULL REFERENCES user_account(id),
    card_id         UUID          NOT NULL REFERENCES card(id),
    grade_source    VARCHAR(20),
    grade_value     VARCHAR(10),
    purchase_price  NUMERIC(10,2),
    purchase_date   DATE,
    notes           TEXT,
    created_at      TIMESTAMPTZ   DEFAULT now()
);
```

### 7.8 `sport_competition` — Named recurring competitions

> **Restored decision (originally §7.7–7.8 in the prior doc version):** price evolution charts overlay major sports events as **shaded date-range bands** (not point markers) — date-range shading gives collectors visual context for price movements during tournaments. Two tables: `sport_competition` holds the named recurring competitions; `competition_event` holds specific instances/stages with concrete date ranges.

Seed competitions: FIFA World Cup, NFL Playoffs, UEFA Champions League, Olympics, NBA Playoffs, MLB Postseason.

**v1.18 correction:** FIFA World Cup and UEFA Champions League are soccer competitions, but §7.2's original sport seed list (baseball, football, basketball, multi) had no `soccer` row — `football` there means American football (NFL), consistent with OQ-7's "top 4 US sports" framing. Added `soccer` (and `hockey`, already implied by "top 4 US sports" but missing from the illustrative list) to the `sport` seed. World Cup and UCL map to `soccer`; NFL Playoffs maps to `football`.

```sql
CREATE TABLE sport_competition (
    id          SMALLINT     PRIMARY KEY,
    sport_id    SMALLINT     NOT NULL REFERENCES sport(id),  -- Olympics → sport 'multi'
    name        VARCHAR(100) NOT NULL,                       -- "FIFA World Cup", "NBA Playoffs"
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    UNIQUE (sport_id, name)
);
```

### 7.9 `competition_event` — Specific competition instances/stages

```sql
CREATE TABLE competition_event (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    competition_id  SMALLINT     NOT NULL REFERENCES sport_competition(id),
    label           VARCHAR(100) NOT NULL,     -- "2026 FIFA World Cup", "2026 NBA Playoffs"
    stage           VARCHAR(50),               -- optional: "Group Stage", "Finals"
    start_date      DATE         NOT NULL,
    end_date        DATE         NOT NULL,
    created_at      TIMESTAMPTZ  DEFAULT now()
);

CREATE INDEX idx_competition_event_dates ON competition_event(start_date, end_date);
```

**Selection rule for a card's chart:** return events whose competition belongs to the card's sport **plus** all `multi`-sport events (e.g., Olympics display across all cards), intersected with the requested time range. Events are delivered **inside the existing price history response** (§8.2) — no separate endpoint.

**Maintenance (OQ-15):** rows are seeded from a versioned reference file in the repo, loaded idempotently at app startup, and updated via PR when new seasons are announced. A weekly health check warns when any active competition has too few future events on the books (see OQ-15 for defaults).

### 7.10 `card_tracking` — Poller inclusion & priority tiers (OQ-7)

Governs which cards the active-listing poller touches on any given day. One row per card that has ever been tracked; absence of a row means "not tracked."

```sql
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
```

**Lifecycle:**

- **Seed cards** are loaded at startup with `tier = 'SEED'`, `poll_cadence = 'DAILY'`. They never age out.
- **User search** on an untracked card inserts a row with `tier = 'SEARCHED'`, `poll_cadence = 'DAILY'`, `last_engagement_at = now()`. **v1.19 correction (M5):** deferred — search does not insert tracking rows in Phase 1. `CardTracker.recordEngagement` only touches `last_engagement_at` on an already-tracked card (called by M6's price-history endpoint on click-through); it's a no-op otherwise. Full demand-driven tracking (search inserting `SEARCHED` rows) waits for Phase 2 accounts, when there's a real signal (an account) behind the engagement.
- **Watchlist add** upserts to `tier = 'WATCHLISTED'` (or keeps `SEED` if already seeded), `poll_cadence = 'DAILY'`.
- **Nightly aging job:** `SEARCHED` cards whose `last_engagement_at` is older than N days without further engagement demote to `DECAYED` with `poll_cadence = 'WEEKLY'`. A further threshold demotes to `PAUSED`. Removing a watchlist entry demotes to `SEARCHED` (not straight to `DECAYED` — user was interested recently).
- **Historical data is never deleted** when a card is demoted or paused — `price_snapshot` and `listing_observation` rows remain.

The specific N-day thresholds are tuning parameters, not schema. Start conservative (e.g., 14 days to decay, 60 to pause) and adjust once real engagement data is available. Stored as runtime-editable values in `app_setting` (§7.12) — `tracking.decay_after_days`, `tracking.pause_after_days` — so tuning doesn't require a redeploy.

### 7.11 `market_metric_daily` — Derived market aggregates (OQ-9)

Persistent daily aggregates computed from `listing_observation` before the raw observations are deleted (§5.4). This is the permanent record of market state over time — our observations *about* the market on a given day, not republished listing data.

```sql
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
```

**Population:** the poller's daily job computes one row per (card, grade, day) from that day's `listing_observation` rows, then the retention job removes the raw rows a few days later (§5.4). `floorHistory` in the price history response (§8.2) reads from this table, not from `listing_observation`.

**Growth:** bounded and predictable — cards actively tracked × grade tiers × days. With OQ-7 aging demoting dormant cards out of polling, the row-add rate naturally throttles over time. Partitioning by month is straightforward if it ever becomes necessary; keep it as a single table until measurements justify otherwise.

### 7.12 `app_setting` — Runtime-tunable operational knobs

Key-value store for values that need to change during operation without a redeploy. Read via a short-TTL cache (e.g., 60s) so a settings change propagates within a minute.

```sql
CREATE TABLE app_setting (
    key          VARCHAR(100) PRIMARY KEY,
    value        TEXT         NOT NULL,
    value_type   VARCHAR(20)  NOT NULL,       -- "INT", "STRING", "DURATION"
    description  TEXT,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by   VARCHAR(100)                 -- audit: who last touched it
);
```

**What lives here (illustrative, not exhaustive):**

| Key | Purpose | OQ |
|---|---|---|
| `tracking.decay_after_days` | Days without engagement before `SEARCHED` → `DECAYED` | OQ-7 |
| `tracking.pause_after_days` | Days without engagement before `DECAYED` → `PAUSED` | OQ-7 |
| `poller.daily_cadence_hours` | Interval between polls for `DAILY` cadence cards | OQ-7 |
| `seed.refresh_interval_days` | How often the hand-curated seed is re-checked | OQ-2 |
| `events.min_future_events` | Minimum future events per active competition before health check warns | OQ-15 |
| `events.check_horizon_months` | Look-ahead window for the events health check | OQ-15 |

**What does NOT live here (see §9):** retention windows (§5.4 — code-reviewed for compliance), API endpoints/URLs, credentials, feature flags with revenue implications. When in doubt, prefer `application.yml`.

---

## 8. API Design (REST Endpoints)

### 8.1 Card Search

```
GET /api/v1/cards/search?q={query}&sport={sport}&page={page}&size={size}
```

The `sport` parameter accepts human-readable codes from `sport.code` (e.g., `?sport=football`) — never internal IDs.

Response:
```json
{
  "results": [
    {
      "id": "uuid",
      "playerName": "Shohei Ohtani",
      "year": 2018,
      "setName": "Topps Update",
      "cardNumber": "US1",
      "sport": "baseball",
      "isRookie": true,
      "latestSalePrice": 245.00,
      "latestSaleDate": "2026-07-01"
    }
  ],
  "totalCount": 12,
  "page": 0,
  "size": 20
}
```

### 8.2 Price History

```
GET /api/v1/cards/{cardId}/prices?days={30|90|180|365}&grade={RAW|PSA10|PSA9|ALL}
```

Response — transaction series and floor series are returned as **separate arrays**, never merged (F-07):
```json
{
  "cardId": "uuid",
  "grade": "PSA10",
  "timeRangeDays": 90,
  "summary": {
    "lastSalePrice": 245.00,
    "lastSaleDate": "2026-07-01",
    "averagePrice": 231.50,
    "highPrice": 310.00,
    "lowPrice": 180.00,
    "salesCount": 47,
    "currentFloorPrice": 259.99
  },
  "sales": [
    {
      "soldAt": "2026-07-01T14:32:00Z",
      "salePrice": 245.00,
      "priceType": "SOLD",
      "source": "THECARDAPI",
      "externalUrl": "https://ebay.com/itm/..."
    },
    {
      "soldAt": "2026-06-28T09:10:00Z",
      "salePrice": 240.00,
      "priceType": "INFERRED_SALE",
      "source": "EBAY_BROWSE_INFERRED",
      "externalUrl": "https://ebay.com/itm/..."
    }
  ],
  "floorHistory": [
    { "date": "2026-07-01", "floorPrice": 259.99, "medianAsk": 289.00, "activeListings": 14 }
  ],
  "events": [
    {
      "competition": "MLB Postseason",
      "label": "2026 MLB Postseason",
      "stage": null,
      "startDate": "2026-09-29",
      "endDate": "2026-11-01"
    }
  ],
  "relatedCards": [
    {
      "cardId": "uuid",
      "relation": "SAME_CARD_DIFFERENT_GRADE",
      "grade": "PSA9",
      "latestSalePrice": 168.00,
      "latestSaleDate": "2026-07-05"
    },
    {
      "cardId": "uuid",
      "relation": "SAME_SET_SAME_PLAYER",
      "cardNumber": "US2",
      "latestSalePrice": 92.00,
      "latestSaleDate": "2026-07-03"
    }
  ]
}
```

Events (§7.8–7.9) are included for the card's sport plus `multi`-sport competitions, limited to the requested time range. The frontend renders them as shaded date-range bands behind the price series.

**`relatedCards` (F-11, OQ-5).** Populated only when the primary series is empty for the requested grade/time-range. Phase 1 similarity rule: same player, same year — adjacent grades of the same card first, then adjacent cards in the same set. `relation` codes: `SAME_CARD_DIFFERENT_GRADE`, `SAME_SET_SAME_PLAYER`. Related cards are context, never presented as this card's price estimate. When both `sales` and `floorHistory` are empty *and* `relatedCards` is empty, the frontend shows the "Not tracked yet" state with a watchlist CTA.

### 8.3 Current Market View

```
GET /api/v1/cards/{cardId}/market?grade={RAW|PSA10|PSA9|ALL}
```

Returns the live market state per grade: floor price, median ask, active listing count, and the current active listings (price, format, URL). This is the primary "what is it worth right now" surface at launch.

### 8.4 Card Detail

```
GET /api/v1/cards/{cardId}
```

### 8.5 Grade Breakdown (Phase 1+)

```
GET /api/v1/cards/{cardId}/grades?days={90}
```

Returns average price per grade level to show the grade premium spread.

---

## 9. Key Technical Decisions

| Decision | Choice | Rationale |
|---|---|---|
| **Data strategy** | Own active-listing ingestion (Browse API) + inferred sales from quantity deltas. No third-party sold-data backfill in MVP | Browse API cannot return sold listings; Marketplace Insights is limited-release; evaluated third-party aggregators (OQ-8) but their scraped provenance shifts marketplace-compliance risk to us. Trade slower cold-start for legally defensible sourcing |
| **Price semantics** | Explicit `price_type` (SOLD / INFERRED_SALE / ASK) on every stored price; series never mixed in UI | Product credibility depends on never presenting asking prices as transaction prices |
| **Sport categorization** | Lookup table (`sport`), not Postgres ENUM | No DDL migration to add a sport; carries UI metadata (`display_name`, `icon_key`, `is_active`); clean FKs. API stays human-readable (`?sport=football`) |
| **Event overlays** | `sport_competition` + `competition_event` tables; shaded date-range bands; delivered inside the price history response | Date ranges beat point markers for tournament context; no extra frontend round-trip; `multi` code handles Olympics across all sports |
| **Configuration split** | Retention windows and other compliance-adjacent values in `application.yml` (code-reviewed); operational tuning knobs in `app_setting` table (runtime-editable) | Retention values getting mistyped in a settings UI is a real risk given eBay ToS is the underlying constraint. Tuning knobs (aging thresholds, cadences) genuinely need adjustment against live data and shouldn't require a redeploy |
| **Language/Framework** | Java 21 + Spring Boot 3.x | Team familiarity |
| **Database** | PostgreSQL | Relational + time-series queries work well; no need for a dedicated TSDB at MVP scale |
| **HTTP Client** | Spring WebClient | Non-blocking; handles eBay rate limits better |
| **DB Migrations** | Flyway | Version-controlled schema changes |
| **Caching** | Spring Cache + Caffeine | In-memory cache for frequent searches (popular cards) |
| **Scheduler** | Spring `@Scheduled` | Simple ingestion jobs; upgrade to Quartz if complexity grows |
| **Auth (Phase 2)** | Spring Security + JWT | Standard for stateless REST APIs |
| **Frontend** | React + Recharts | Recharts handles time-series price charts cleanly |
| **Deployment** | Cloud (provider TBD — see OQ-3) | Cloud confirmed; provider deferred until closer to staging. Candidates: Railway, Fly.io, AWS |

### 9.1 Repository Structure

- **`collector-market-backend`** — Spring Boot API. This planning document lives at `/docs/planning.md` and is the source of truth for stack, data model, API design, and phase context (OQ-4: resolved, separate repos).
- **`collector-market-frontend`** — React SPA. Added when frontend work begins; README there should point back to the backend repo's `/docs` for product/architecture context, since docs live in one place.
- Both repos are **private** for now; revisit if open-sourcing or external contributors become relevant.
- Doc revisions are committed individually with descriptive messages (e.g., "v1.7: resolve OQ-4 — separate repos") so `git log` doubles as a decision journal — this replaces the ad hoc versioning notes at the bottom of this file once the repo is the system of record.

---

## 10. Card Normalization Strategy

This is the highest-risk component. Recommended phased approach:

**Phase 1 (MVP):** Rule-based parser + small hand-curated seed catalog
- Ship a small hand-curated `card` catalog (a few hundred rows — same rookies-of-the-last-3-years scope as the OQ-7 tracking seed). Seeded manually because OQ-8 rejection means no third-party catalog source.
- Extract year (4-digit number), player name (cross-reference the seeded catalog + a maintained player-name list), set name, card number from eBay titles
- Handle common grade patterns: `PSA 10`, `BGS 9.5`, `Gem Mint 10`
- Accept imperfect matching — log unmatched titles for review; the catalog grows as unmatched titles are triaged into new canonical cards
- **Trade-off accepted:** normalization coverage will be thin outside the seeded scope at launch. Cards outside the seed will show the OQ-5 empty state until manually catalogued or matched by future phases.

**Phase 2:** Fuzzy matching + catalog expansion
- Use fuzzy string matching (e.g., Apache Commons Text `JaroWinklerSimilarity`) against the accreting catalog
- Supplement with community-maintained datasets (Beckett-style, if legally sourceable) as the catalog grows

**Phase 3:** AI-assisted classification
- Use Claude API as an internal microservice to classify ambiguous titles
- Only invoke for listings that the rule-based system scores below a confidence threshold

---

## 11. Feature Roadmap

```
Phase 1 — MVP (Search, Current Market & Accruing History)
├── Hand-curate initial card catalog seed (same scope as OQ-7 tracking seed)
├── eBay Browse API integration + OAuth setup
├── Active-listing poller: daily observations → floor/ask metrics
├── card_tracking model + tier-based poll scheduler (seed + demand-driven)
├── Nightly aging job (SEARCHED → DECAYED → PAUSED)
├── Nightly retention job (delete expired listing_observation; nullify expired linkback fields)
├── Daily market_metric_daily aggregation job (compute before listing_observation deletion)
├── Inferred-sale detection (fixed-price quantity-sold deltas)
├── Card normalization (rule-based v1)
├── Seed sport lookup table + competitions/events for current season
├── Idempotent reference-data loader (startup) + weekly events health check
├── Card search endpoint
├── Current market endpoint (floor, asks, active listings)
├── Price history endpoint + grade filter (SOLD via inferred sales / floor series, incl. event bands)
├── React frontend: search page + market view + price chart (with competition date-range bands)
└── Deploy to staging

Phase 2 — User Accounts & Watchlist
├── User registration & login (JWT)
├── Watchlist CRUD
├── Price alert emails (Spring Mail)
└── Grade premium breakdown view

Phase 3 — Portfolio & Market Dashboard
├── Portfolio tracker (buy price, P&L)
├── Market dashboard: top movers, most searched
├── Advanced analytics (volume trends, grade arbitrage)
└── News feed correlation
```

---

## 12. Open Questions & Decisions Needed

| # | Question | Impact | Status | Resolution |
|---|---|---|---|---|
| OQ-1 | Which eBay API tier / plan to register for? | Data ingestion frequency; caps how many listings the poller can track daily | ✅ Resolved | Start on the free developer tier; reassess if/when the poller hits daily call limits. The free-tier quota is the hard budget for the OQ-7 ingestion strategy (verify exact daily limit at app registration). |
| OQ-2 | Will eBay data alone be sufficient for MVP or do we seed with a card catalog? | Normalization complexity | ✅ Resolved | Rule-based parser + small hand-curated seed catalog. Seed scope matches the OQ-7 tracking seed (a few hundred cards: rookies of the last 3 years in top 4 US sports). See §10 Phase 1. Trade-off accepted: normalization coverage will be thin outside the seeded scope at launch; unmatched titles are logged and triaged into new canonical cards over time. Catalog grows organically as user demand drives new cards into tracking. |
| OQ-3 | What is the target hosting environment? (Cloud provider, self-hosted?) | Infrastructure planning | 🟡 Partially resolved | Cloud deployment confirmed (not self-hosted). Specific provider deferred — too early in development to commit. Candidates remain Railway, Fly.io, AWS (§9). Revisit before "Deploy to staging" in the Phase 1 roadmap. |
| OQ-4 | Will the frontend be a separate repo or a monorepo with the backend? | Developer workflow | ✅ Resolved | Separate repositories for frontend (React) and backend (Spring Boot). Implication: the REST API contract is the interface between teams/repos — keep §8 authoritative, and consider generating an OpenAPI spec from the backend so the frontend can code against it. |
| OQ-5 | How to handle cards with no sales data yet? | UX decision — more common now given accrual model | ✅ Resolved | Never hide the card. Handle two variants explicitly: **(a) no sold history but active listings exist** — show the current market view normally; the price history chart clearly states "no confirmed sales in this window" and offers related cards for context. **(b) No history AND no active listings** — show "Not tracked yet" state with a watchlist CTA (ties to OQ-7 demand-driven ingestion) plus related cards. **Similarity rule for Phase 1:** same player, same year — adjacent grades of the same card first, then adjacent cards in the same set. Reuses existing catalog data, no new scoring logic. Related cards are labeled as related, never presented as this card's price estimate (same credibility principle as F-07). **API shape:** embed a `relatedCards` array in the existing price history response (§8.2) when the primary series is empty — one round-trip, no new endpoint, consistent with how events are delivered. |
| OQ-6 | What is the monetization model? (Free, freemium, subscription?) | Feature gating design | ⏸ Deferred | Too early to commit — decision depends on MVP traction and which features prove valuable. Revisit before Phase 2 planning (watchlist/accounts). Implication for now: build MVP as fully free, avoid feature-gating scaffolding that would need to be undone. |
| OQ-7 | Ingestion strategy: which cards does the poller track? Catalog-driven (seed popular players/sets), demand-driven (user search triggers tracking), or hybrid? | Rate-limit budget, cold-start UX, chicken-and-egg between search and data | ✅ Resolved | **Hybrid, with priority tiers.** (1) **Seed layer** — a small, curated set polled daily from launch (rookies of the last 3 years in the top 4 US sports, ~few hundred cards). Small enough to leave headroom in the OQ-1 free-tier budget. (2) **Demand layer** — cards enter the tracked set on user search or watchlist add (OQ-5 CTA). Watchlist is a stronger engagement signal than search. (3) **Priority tiers** — seeded + watchlisted → daily. Recently-searched → daily for N days, decay to weekly if no further engagement. Dormant → drop out of polling, keep historical observations. (4) **Aging** — a nightly job promotes/demotes cards between tiers based on `last_engagement_at`. Requires a new `card_tracking` model (§7.10). Naturally bounds `listing_observation` growth (helps OQ-11). Initial seed selection is a taste call — keep it small and defensible; let the demand layer fill in the long tail. |
| OQ-8 | The Card API evaluation: data provenance, redistribution ToS, per-sport coverage depth, free-tier limits, reliability as a dependency | Whether sold-price backfill is viable at MVP; OQ-2 depends on it | ❌ Rejected | **Evaluated and rejected.** Free tier (5k records/day, 3-day lookback) is technically workable, but ToS §7 explicitly disclaims that use of the data complies with eBay or other marketplace terms and shifts compliance risk to us. The underlying data is scraped from public marketplace sources without licensing arrangements. Not defensible for a public product; also creates a business-continuity risk (source could disappear if the aggregator gets pushed back on). Consequence: OQ-2 reopens; no sold-price backfill in MVP. Sold data will accrue only from our own inferred-sales pipeline (§5.1.1) until/unless OQ-10 (Marketplace Insights) lands. |
| OQ-9 | eBay ToS: are we permitted to store and publicly redisplay listing data long-term? (Separate question from rate limits) | Data retention model; legal exposure | ✅ Resolved | **No long-term storage of raw listing data.** eBay's terms permit retention "as required" for the operational purpose, then require disposal. We define our own windows tied to purpose (see §5.4). **Architectural split:** (a) **Raw listing data** (`listing_observation` rows, `raw_title`/`external_url`/`external_id` fields) is **transient** — kept only long enough to compute daily aggregates and detect inferred sales, then deleted or nullified. (b) **Derived data** (daily floor/median/count aggregates in `market_metric_daily` §7.11, and price/timestamp/card/grade tuples in `price_snapshot`) is **permanent** — these are our observations *about* the market, not republished listings. Consequence: F-08 relaxes to linkback within the retention window only. Strengthens the moat: our persistent dataset is entirely computed by us, not scraped content. Also eases OQ-11 significantly (`listing_observation` no longer grows unbounded). |
| OQ-10 | Marketplace Insights application: do we qualify, and on what timeline? | Long-term official sold-data route | ⏸ Deferred | Not pursued for MVP. First iteration ships on Browse API alone (active listings + inferred sales from quantity deltas). Revisit if/when (a) inferred-sales coverage proves too thin to be useful, (b) users demand deep historical data, or (c) the product reaches a scale where the limited-release application has better odds. Consequence: **sold data in MVP comes exclusively from inferred sales.** No fallback source; if fixed-price inference is thin for a card category, the price history for that category will be sparse. |
| OQ-11 | listing_observation growth management: partitioning vs. rollup aggregation threshold | Storage cost; query performance | ✅ Resolved | Resolved as a consequence of OQ-9. `listing_observation` is now transient (7-day rolling window, §5.4), so unbounded growth is no longer a concern. Permanent storage is `market_metric_daily` (§7.11), which grows predictably at cards × grades × days and is bounded further by OQ-7 aging. Partitioning deferred until measurements justify it. |
| OQ-12 | Sport categorization: Postgres ENUM vs. lookup table? | Schema extensibility | ✅ Resolved (restored) | Lookup table `sport` with metadata columns; card FK `sport_id`; API keeps human-readable codes. See §7.2. |
| OQ-13 | Event overlays: separate endpoint or embedded in price history? Points or ranges? | API shape; chart UX | ✅ Resolved (restored) | Shaded date-range bands, delivered inside the price history response. See §7.8–7.9, §8.2. |
| OQ-14 | How do multi-sport events (Olympics) attach to cards? | Event selection logic | ✅ Resolved (restored) | `multi` sport code; `multi` events display on all cards regardless of sport. See §7.8. |
| OQ-15 | Who maintains `competition_event` date ranges? (Manual seed per season vs. an external schedule source) | Data maintenance burden | ✅ Resolved | **Manual maintenance, via PR.** ~5–6 new event rows per year across the seeded competitions — burden too small to justify an external integration (sport-specific APIs, mostly paid, added moving parts). Events live as reference data in the repo (`seeds/competition_events.yml` or `.sql`), added as PRs when seasons are announced; `git log` doubles as an audit trail. **Loader:** an idempotent reference-data loader runs on app startup, upserting into `competition_event`. Kept separate from Flyway migrations (schema vs. reference data are different concerns). **Forgetting-safety:** a weekly scheduled job warns if any active competition has fewer than N future events within the next M months (defaults in `app_setting`); logs a warning for MVP, upgrades to an alert once observability is in place. **Stage granularity:** MVP uses one date-range band per competition instance; splitting stages (Group / Knockouts / Finals) is a Phase 2+ refinement if user feedback justifies it. |

---

*Document version 1.19 — Planning phase. v1.1: corrected eBay sold-data assumption; adopted hybrid data strategy (active-listing ingestion + inferred sales + third-party sold-data backfill); added listing_observation model, current-market endpoint, and price-type discipline. v1.2: resolved OQ-2 — card catalog seeded from The Card API identities; normalization strategy updated accordingly. v1.3: restored previously-resolved decisions lost in a doc-version regression — sport lookup table (§7.2), competition event overlays (§7.8–7.9, §8.2), and their resolved OQs (OQ-12..14). v1.4: resolved OQ-1 — start on the free eBay developer tier, reassess at scale. v1.5: restructured the open questions table with Status and Resolution columns. v1.6: OQ-3 partially resolved — cloud deployment confirmed, provider choice deferred. v1.7: resolved OQ-4 — frontend and backend live in separate repositories. v1.8: project renamed to Collector Market; added §9.1 repository structure (private repos, planning doc at collector-market-backend/docs/planning.md). v1.9: resolved OQ-5 — explicit empty state + related cards (same player, adjacent grades/cards); added F-11 and relatedCards field to price history response. v1.10: OQ-6 deferred — monetization decision revisited before Phase 2. v1.11: resolved OQ-7 — hybrid ingestion (seed + demand + tiers); added card_tracking model (§7.10); noted partial relief for OQ-11. v1.12: rejected OQ-8 (The Card API — ToS incompatible with public product); reopened OQ-2; adopted accrual-only data model; MVP framing, F-04, §5.1.2, §10 normalization, and roadmap all updated accordingly. v1.13: OQ-10 deferred — MVP ships on Browse API alone; Marketplace Insights not pursued for first iteration. v1.14: OQ-2 closed — rule-based parser + hand-curated seed catalog (already detailed in §10 Phase 1). v1.15: resolved OQ-9 with retention policy (§5.4); added market_metric_daily table (§7.11); relaxed F-08; closed OQ-11 as a consequence; roadmap gained retention + aggregation jobs. v1.16: formalized configuration split — retention windows in application.yml, operational tuning knobs in new app_setting table (§7.12); added decision row in §9. v1.17: closed OQ-15 — competition events maintained manually via PR with reference-data loader + weekly health check. All planning-phase open questions now resolved, partially resolved, deferred, or rejected. v1.18: found during M2 implementation — §7.8's seed competitions include FIFA World Cup and UEFA Champions League (soccer), but §7.2's sport seed list had no `soccer` row; added `soccer` (and `hockey`) to the §7.2 sport seed list to match V001's actual migration. v1.19: decided during M5 implementation — §7.10's "user search inserts a SEARCHED row" deferred to Phase 2; Phase 1's CardTracker only lets an already-tracked card's last_engagement_at be touched (by M6's price-history endpoint), since search alone isn't a strong enough signal without an account behind it.*
