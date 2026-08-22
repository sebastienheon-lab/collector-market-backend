# Collector Market — Backend Containerization Plan

Package the Spring Boot backend as a Docker image and run it via `docker compose`, with credentials supplied as a Docker secret. **The app only** — Postgres stays on the host.

**Companion docs:** `docs/planning.md` (design source of truth), `docs/implementation-plan.md` (milestones M0–M10). This slots in after M7 and does much of M9's groundwork ahead of time.

---

## Scope & constraints

| Decision | Choice | Why |
|---|---|---|
| What's containerized | App only | Postgres already runs on the host and works; no need to duplicate |
| DB connectivity | `host.docker.internal:5432` | Containers can't reach the host's `localhost`; this is the Docker Desktop-supported hostname |
| Credentials delivery | Docker secret → `/run/secrets/` | Matches the intended staging/prod mechanism, so local and deployed behave identically |
| Build style | Multi-stage, layered jar | Reproducible builds; fast rebuilds since code changes only invalidate the smallest layer |
| Runtime base | `eclipse-temurin:25-jre-alpine` | JRE not JDK (smaller); Alpine for size |

**Known limitation:** in plain (non-Swarm) Compose, "secrets" are host file mounts at a restricted path — convention and isolation, not encryption at rest. Real encrypted secrets need Swarm, Kubernetes, or an external secrets manager. The YAML shape carries over unchanged to Swarm, which is the point of using it now.

---

## Step 1 — Dockerfile

Multi-stage build at repo root.

- [x] **Build stage** on `eclipse-temurin:25-jdk`
  - [x] Copy `pom.xml` **first**, resolve dependencies, *then* copy `src` — so dependency layers cache independently of source changes (uses the `mvnw` wrapper already in the repo, plus `.mvn/`, so no Maven-preinstalled base image is needed)
  - [x] Package the application
  - [x] Extract layers: ~~`java -Djarmode=layertools -jar app.jar extract`~~ → **Spring Boot 4.1 renamed this jarmode.** It's now `java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted` (`--layers` is opt-in now; `--launcher` is required to also pull in the `spring-boot-loader` classes)
- [x] **Runtime stage** on `eclipse-temurin:25-jre-alpine`
  - [x] Create and switch to a non-root user
  - [x] Copy extracted layers in order: `dependencies` → `spring-boot-loader` → `snapshot-dependencies` → `application`
  - [x] `EXPOSE 8080`
  - [x] Entrypoint: `java org.springframework.boot.loader.launch.JarLauncher`

**DoD:** `docker build -t collector-market-backend .` succeeds; a source-only change rebuilds in seconds, not minutes. ✅ Verified — `dependency:go-offline` stays `CACHED` on a source-only change.

---

## Step 2 — `.dockerignore`

- [x] Create at repo root, excluding: `target/`, `.git/`, `.idea/`, `*.md`, `application-credentials.yml`, `.env`, `docs/`
  - **Divergence:** the real file is `src/main/resources/application-credentials.yml`, not repo root. A plain `application-credentials.yml` pattern in `.dockerignore` only matches the *root* of the build context (unlike `.gitignore`'s implicit recursive matching) — it would silently miss the nested file and let it get copied into the build stage's `src/` and baked into the packaged jar. Used `**/application-credentials.yml` instead.

**DoD:** `docker build` context is small; credentials provably absent from the image (`docker run --rm --entrypoint sh <image> -c 'ls -la /app'`). ✅ Verified — also cross-checked `docker history --no-trunc` for leakage.

---

## Step 3 — docker-compose.yml

Single `app` service. No Postgres service.

- [x] `build: .` referencing the Dockerfile
- [x] Port mapping `8080:8080`
- [x] `extra_hosts: - "host.docker.internal:host-gateway"` — no-op on Docker Desktop, makes the same file work on Linux hosts and CI runners
- [x] `restart: unless-stopped`
- [x] Healthcheck against `/actuator/health`
- [x] `SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/collector_market_dev`
- [x] Secret declaration — **adjusted for the real file location and to sidestep Step 4's extension problem:**
  ```yaml
  secrets:
    app_credentials:
      file: ./src/main/resources/application-credentials.yml
  ```
- [x] Service-level secret, mounted with an explicit `target` so the extension survives:
  ```yaml
  secrets:
    - source: app_credentials
      target: application-credentials.yml
  ```
  This mounts at `/run/secrets/application-credentials.yml` — filename matches Spring's own `application-{profile}.yml` convention exactly (the app already has `credentials` in `spring.profiles.include`), which is what makes Step 4 trivial below.

**DoD:** `docker compose up --build` starts the app; `/actuator/health` returns UP from the host. ✅ Verified, including `db: UP` and a live `/api/v1/cards/search` hit against seeded data.

---

## Step 4 — Wire Spring to the mounted secret

Turned out not to be fiddly, because Step 3's `target:` sidesteps the extension problem entirely instead of working around it after the fact.

- [x] Point Spring at the secret via `SPRING_CONFIG_ADDITIONAL_LOCATION=file:/run/secrets/`
- [x] Mind the trailing-slash rule: a location ending in `/` is a directory, without one is a file — used the directory form
- [x] ~~A file without a recognized extension can be silently skipped~~ — moot: the secret is mounted with `target: application-credentials.yml`, so it lands in the directory *as* `application-credentials.yml`. Spring's directory-scan resolves it as the `credentials` profile document (already in `spring.profiles.include`) with zero extra wiring.
- [x] **Verification:** confirmed via `docker compose logs` — `"The following 2 profiles are active: "local", "credentials""` — and `/actuator/health` reporting `db: UP` with a real DB connection.

**Fallback (not needed):** the plan's original entrypoint-script workaround (copy `/run/secrets/app_credentials` → a named file before launching) is unnecessary once the secret's `target` does the renaming for you.

**DoD:** app in the container demonstrably reads credentials from the secret; no credentials in the image or in `docker inspect` output. ✅ Verified — `docker inspect` env vars contain no secret values (only the file path, which is expected for a bind-mounted secret).

---

## Step 5 — Host Postgres accessibility

The container connects over Docker's bridge network, which a default local-only Postgres will refuse.

- [x] ~~Confirm `listen_addresses`...~~ / ~~Confirm `pg_hba.conf`...~~ — **not applicable here.** This machine's dev Postgres runs as its own Docker container (`dev-postgres`, port `5432` published to the host), not as a native install — exactly the case the plan's own footnote calls out as "likely already fine." (There's also a dormant, unused Homebrew Postgres install on this box that *would* need these changes if it were ever the one in use — its `pg_hba.conf` only allows `127.0.0.1`/`::1` today.)
- [x] Verified connectivity indirectly: Flyway validated and ran against `host.docker.internal:5432` on first `docker compose up`, no config changes required.

**DoD:** Flyway migrations run successfully from the containerized app against the host database. ✅ Verified — `Schema "public" is up to date`, 13 migrations validated.

---

## Step 6 — Documentation & hygiene

- [x] README section: `docker compose up --build` as the primary path; the raw `docker run` equivalent as a reference
- [x] Document the credentials prerequisite — `application-credentials.yml` must exist at **`src/main/resources/`** (not repo root — see Step 2/3 divergence) before compose will start
- [x] Confirm `.gitignore` covers `application-credentials.yml` and `.env` — already did, no change needed
- [x] Note the port-conflict caveat: if host Postgres is on 5432 and you later add a Postgres container, they'll collide

**DoD:** someone cloning the repo can get the app running in Docker from the README alone (given credentials and a running Postgres).

---

## Verification checklist

Run through after all steps:

- [x] `docker compose up --build` → app reaches UP
- [x] Flyway migrations apply against host Postgres
- [x] `/actuator/health` returns UP from the host, and **does not** consume eBay quota (M7 invariant — `ebay.lastSuccessfulCallAt: never`, `callsToday` unchanged by the health hit, all cached state)
- [x] A seeded card is retrievable via `/api/v1/cards/search`
- [x] Credentials absent from the image: `docker history` and a shell into the container both show nothing
- [x] Source-only change rebuilds fast (layer caching working) — `dependency:go-offline` stays `CACHED`
- [x] `docker compose down && docker compose up` works without a rebuild

---

## What this sets up for M9

Most of the staging deployment work is done once this lands:

- **Dockerfile** transfers unchanged — same image runs in staging
- **Secret mechanism** is already the target mechanism; staging swaps the compose `file:` source for a Swarm secret or the platform's equivalent
- **Remaining M9 work:** pick the provider (OQ-3), provision managed Postgres, set `SPRING_DATASOURCE_URL` to it, wire the deploy pipeline, add domain/TLS and alerting

---

*Living document — check boxes as you go, edit when reality diverges.*
