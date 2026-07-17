# Collector Market — Project Planning Document

> **Status:** Planning phase  
> **Repository:** `collector-market-backend` (this doc lives at `/docs/planning.md`)  
> **Stack:** Java / Spring Boot · PostgreSQL · React  
> **Audience:** Public product for collectors — MVP scope is sports cards (all sports); the platform is named for future collectible categories  
> **MVP:** Search a card and see the current market (live listings, floor price) plus price history that accrues from day one — backfilled with third-party sold data where available

> ⚠️ **v1.1 pivot:** The original plan assumed sold-listing data was available from the eBay Browse API. It is not — sold data is gated behind the limited-release Marketplace Insights API. The data strategy has been revised to a **hybrid model** (see §5).

> **v1.8 rename:** the project is now **Collector Market** (repos: `collector-market-backend`, `collector-market-frontend` to follow). Sports cards remain the explicit MVP scope — the name change doesn't expand it. See §9 for repository structure.

---

## 1. Project Vision

A public web application that lets sports card collectors search any card and immediately see real market data: the current market state (live listings, floor price, ask distribution), observed and inferred sales, trends over time, grade-level price spreads, and volume activity. The goal is to be the fastest, cleanest way to answer the question *"What is this card actually worth right now?"*

At launch, the strongest signal is the **current market view** (what the card is listed for right now, by grade). Historical depth accrues continuously from our own ingestion and is backfilled with third-party sold data where coverage exists. Our forward-accruing, owned dataset is the long-term moat.

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
| F-04 | User can view a price history chart for a selected card, built from (a) our own accrued observations and (b) third-party sold data where available | Must Have |
| F-05 | Price history is filterable by time range (30 / 90 / 180 / 365 days) | Must Have |
| F-06 | Price history is filterable by grade (Raw, PSA 9, PSA 10, BGS 9.5, etc.) | Must Have |
| F-07 | Charts clearly distinguish price types — **SOLD**, **INFERRED_SALE**, and **ASK** (floor) series are never silently mixed | Must Have |
| F-08 | Each data point links back to the original listing where a URL exists | Must Have |
| F-09 | Summary stats shown: last sale, average, high, low, # of sales, current floor | Must Have |
| F-10 | Data refreshes on a scheduled basis (at minimum daily) | Must Have |

> Note: F-07 is a credibility requirement. Asking prices and transaction prices are different signals; mixing them in one undifferentiated line would undermine trust in the product.

### 3.2 Phase 2 — Watchlist & Accounts

| ID | Requirement |
|---|---|
| F-11 | User can register and log in (email + password) |
| F-12 | Logged-in user can add cards to a watchlist |
| F-13 | Watchlist shows latest price and % change since added |
| F-14 | User receives email alert when a watched card moves ±X% |

### 3.3 Phase 3 — Portfolio & Analytics

| ID | Requirement |
|---|---|
| F-15 | User can log cards they own with purchase price |
| F-16 | Portfolio shows total estimated value and P&L |
| F-17 | Market dashboard: top movers, most searched, highest volume |
| F-18 | Grade premium analysis (price delta between Raw → PSA 9 → PSA 10) |
| F-19 | Player news feed correlated with price movement |

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

#### 5.1.2 Third-party sold-data API — historical backfill

**Selected: The Card API** (thecardapi.com) — real sold prices (incl. Best Offer accepted), free-tier self-serve keys, 15M+ transactions, daily indexing. Adopted as the Phase 1 backfill source, **contingent on the OQ-8 evaluation passing** (data provenance, redistribution ToS, per-sport coverage depth, free-tier limits, reliability as a business dependency).

It plays a **dual role** in the project:

1. **Sold-price backfill** — solves the cold-start problem for price history charts.
2. **Card catalog seed (resolves OQ-2)** — its indexed transactions carry card identities (player, year, set, number, grade), giving us a reference dataset to bootstrap the `card` catalog and to fuzzy-match eBay listing titles against. This means we do not depend on eBay titles alone for normalization from day one.

Other surveyed options (for reference):

| Provider | History? | Access | Notes |
|---|---|---|---|
| The Card API | Yes (sold) | Free tier, self-serve | Primary candidate; provenance TBD |
| Card Hedge | Yes (Price History API) | Apply for key; likely paid | Fallback candidate |
| SportsCardsPro / PriceCharting | **No** — current values only | Paid subscription | Useful as reference catalog, not history |
| Card Ladder / Market Movers | Yes (deep) | Consumer apps, no public API | Not integrable |

#### 5.1.3 eBay Marketplace Insights API — official route (long shot, pursue in parallel)

Apply for limited-release access. If granted: ~90 days of true sold data, official and ToS-clean. Do not block MVP on this.

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
    code          VARCHAR(30)  NOT NULL UNIQUE,  -- "baseball", "football", "basketball", "multi"
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

**Retention note:** this table grows fast (listings × days). Plan for partitioning by month or aggregating observations older than N days into daily rollups.

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
  ]
}
```

Events (§7.8–7.9) are included for the card's sport plus `multi`-sport competitions, limited to the requested time range. The frontend renders them as shaded date-range bands behind the price series.

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
| **Data strategy** | Hybrid: own active-listing ingestion (Browse API) + third-party sold-data backfill | Browse API cannot return sold listings; Marketplace Insights is limited-release. Owned forward-accruing dataset is the moat; backfill solves the cold start |
| **Price semantics** | Explicit `price_type` (SOLD / INFERRED_SALE / ASK) on every stored price; series never mixed in UI | Product credibility depends on never presenting asking prices as transaction prices |
| **Sport categorization** | Lookup table (`sport`), not Postgres ENUM | No DDL migration to add a sport; carries UI metadata (`display_name`, `icon_key`, `is_active`); clean FKs. API stays human-readable (`?sport=football`) |
| **Event overlays** | `sport_competition` + `competition_event` tables; shaded date-range bands; delivered inside the price history response | Date ranges beat point markers for tournament context; no extra frontend round-trip; `multi` code handles Olympics across all sports |
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

**Phase 1 (MVP):** Rule-based parser + reference catalog seeded from The Card API
- Bootstrap the `card` catalog from The Card API's indexed card identities (player, year, set, number) — resolves OQ-2
- Extract year (4-digit number), player name (cross-reference the seeded catalog), set name, card number from eBay titles
- Handle common grade patterns: `PSA 10`, `BGS 9.5`, `Gem Mint 10`
- Accept imperfect matching — log unmatched titles for review

**Phase 2:** Fuzzy matching hardening
- Use fuzzy string matching (e.g., Apache Commons Text `JaroWinklerSimilarity`) against the seeded catalog
- Supplement the catalog with additional sources (Beckett or community-maintained datasets) where The Card API coverage is thin

**Phase 3:** AI-assisted classification
- Use Claude API as an internal microservice to classify ambiguous titles
- Only invoke for listings that the rule-based system scores below a confidence threshold

---

## 11. Feature Roadmap

```
Phase 1 — MVP (Search, Current Market & Accruing Price History)
├── Evaluate The Card API (coverage, ToS, limits) — decision gate for backfill + catalog seed
├── Seed card catalog from The Card API identities (resolves OQ-2)
├── Apply for eBay Marketplace Insights access (parallel, non-blocking)
├── eBay Browse API integration + OAuth setup
├── Active-listing poller: daily observations → floor/ask metrics
├── Inferred-sale detection (fixed-price quantity-sold deltas)
├── Third-party sold-data backfill job (if The Card API evaluation passes)
├── Card normalization (rule-based v1)
├── Seed sport lookup table + competitions/events for current season
├── Card search endpoint
├── Current market endpoint (floor, asks, active listings)
├── Price history endpoint + grade filter (separate SOLD / floor series, incl. event bands)
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
| OQ-2 | Will eBay data alone be sufficient for MVP or do we seed with a card catalog? | Normalization complexity | ✅ Resolved | Seed the card catalog from The Card API's indexed card identities (§5.1.2); eBay titles are matched against this reference rather than parsed in isolation. Contingent on OQ-8 passing. |
| OQ-3 | What is the target hosting environment? (Cloud provider, self-hosted?) | Infrastructure planning | 🟡 Partially resolved | Cloud deployment confirmed (not self-hosted). Specific provider deferred — too early in development to commit. Candidates remain Railway, Fly.io, AWS (§9). Revisit before "Deploy to staging" in the Phase 1 roadmap. |
| OQ-4 | Will the frontend be a separate repo or a monorepo with the backend? | Developer workflow | ✅ Resolved | Separate repositories for frontend (React) and backend (Spring Boot). Implication: the REST API contract is the interface between teams/repos — keep §8 authoritative, and consider generating an OpenAPI spec from the backend so the frontend can code against it. |
| OQ-5 | How to handle cards with no sales data yet? (Show market view only, empty history state, or hide?) | UX decision — more common now given accrual model | 🔲 Open | |
| OQ-6 | What is the monetization model? (Free, freemium, subscription?) | Feature gating design | 🔲 Open | |
| OQ-7 | Ingestion strategy: which cards does the poller track? Catalog-driven (seed popular players/sets), demand-driven (user search triggers tracking), or hybrid? | Rate-limit budget, cold-start UX, chicken-and-egg between search and data | 🔲 Open | |
| OQ-8 | The Card API evaluation: data provenance, redistribution ToS, per-sport coverage depth, free-tier limits, reliability as a dependency | Whether sold-price backfill is viable at MVP; OQ-2 depends on it | 🔲 Open | |
| OQ-9 | eBay ToS: are we permitted to store and publicly redisplay listing data long-term? (Separate question from rate limits) | Data retention model; legal exposure | 🔲 Open | |
| OQ-10 | Marketplace Insights application: do we qualify, and on what timeline? | Long-term official sold-data route | 🔲 Open | |
| OQ-11 | listing_observation growth management: partitioning vs. rollup aggregation threshold | Storage cost; query performance | 🔲 Open | |
| OQ-12 | Sport categorization: Postgres ENUM vs. lookup table? | Schema extensibility | ✅ Resolved (restored) | Lookup table `sport` with metadata columns; card FK `sport_id`; API keeps human-readable codes. See §7.2. |
| OQ-13 | Event overlays: separate endpoint or embedded in price history? Points or ranges? | API shape; chart UX | ✅ Resolved (restored) | Shaded date-range bands, delivered inside the price history response. See §7.8–7.9, §8.2. |
| OQ-14 | How do multi-sport events (Olympics) attach to cards? | Event selection logic | ✅ Resolved (restored) | `multi` sport code; `multi` events display on all cards regardless of sport. See §7.8. |
| OQ-15 | Who maintains `competition_event` date ranges? (Manual seed per season vs. an external schedule source) | Data maintenance burden | 🔲 Open | |

---

*Document version 1.8 — Planning phase. v1.1: corrected eBay sold-data assumption; adopted hybrid data strategy (active-listing ingestion + inferred sales + third-party sold-data backfill); added listing_observation model, current-market endpoint, and price-type discipline. v1.2: resolved OQ-2 — card catalog seeded from The Card API identities; normalization strategy updated accordingly. v1.3: restored previously-resolved decisions lost in a doc-version regression — sport lookup table (§7.2), competition event overlays (§7.8–7.9, §8.2), and their resolved OQs (OQ-12..14). v1.4: resolved OQ-1 — start on the free eBay developer tier, reassess at scale. v1.5: restructured the open questions table with Status and Resolution columns. v1.6: OQ-3 partially resolved — cloud deployment confirmed, provider choice deferred. v1.7: resolved OQ-4 — frontend and backend live in separate repositories. v1.8: project renamed to Collector Market; added §9.1 repository structure (private repos, planning doc at collector-market-backend/docs/planning.md).*
