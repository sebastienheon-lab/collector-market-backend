# Collector Market — Backend

Spring Boot API for Collector Market, a marketplace for collectors to search cards and track price history. MVP scope is sports cards; see the planning doc for full context.

📄 **Planning doc (source of truth):** [`docs/planning/planning.md`](docs/planning/planning.md) — stack, data model, API design, roadmap, and open questions. Keep it updated as decisions are made.

## Stack

- Java 25 + Spring Boot 4.x
- PostgreSQL
- Frontend lives in a separate repo: `collector-market-frontend`

## Running with Docker

Prerequisite: `src/main/resources/application-credentials.yml` must exist (gitignored; holds DB and eBay credentials for the local profile — see `application-local.yml` for the non-secret defaults it layers onto). Postgres must already be reachable from the host on `5432` — the container connects to it via `host.docker.internal`, whether Postgres runs natively or in its own container.

```
docker compose up --build
```

Starts the app on `http://localhost:8080`; `/actuator/health` should report `UP` once Flyway migrations finish. Credentials are supplied via a Compose secret (`./src/main/resources/application-credentials.yml` mounted at `/run/secrets/application-credentials.yml`), never baked into the image.

Equivalent raw `docker run` (for reference — compose is the primary path):

```
docker build -t collector-market-backend .
docker run --rm -p 8080:8080 \
  --add-host host.docker.internal:host-gateway \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/collector_market_dev \
  -e SPRING_CONFIG_ADDITIONAL_LOCATION=file:/run/secrets/ \
  -v "$(pwd)/src/main/resources/application-credentials.yml:/run/secrets/application-credentials.yml:ro" \
  collector-market-backend
```

**Port conflict:** the app expects Postgres on the host's `5432`. If you later add a Postgres service to `docker-compose.yml`, don't also publish it on `5432` — it'll collide with the host instance this setup already depends on.

## Status

Core API implemented — see `docs/planning/implementation-plan.md` for milestone status.
