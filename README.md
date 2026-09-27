# Demo Booking — Branch Appointment Scheduling System

An Angular + Spring Boot system for customers to book a branch appointment and receive a
simulated confirmation, packaged with Docker so the whole stack runs with one command.

---

## Contents

- [What this is](#what-this-is)
- [Prerequisites](#prerequisites)
- [Quick start](#quick-start)
- [What you'll see running](#what-youll-see-running)
- [Demo data](#demo-data)
- [Configuration](#configuration)
- [Project structure](#project-structure)
- [Running tests](#running-tests)
- [API documentation](#api-documentation)
- [Stopping and cleaning up](#stopping-and-cleaning-up)
- [Troubleshooting](#troubleshooting)
- [Known limitations](#known-limitations)
- [Full documentation index](#full-documentation-index)

---

## What this is

Customers pick a branch, a service, and an available time slot, then confirm their booking through
a simulated email-with-magic-link flow — no real email/SMS provider is used anywhere; see
[docs/USER-GUIDE.md](docs/USER-GUIDE.md) for a full walkthrough of what that looks like. The
system is not affiliated with, endorsed by, or a real product of any bank — its visual design
draws stylistic inspiration from a public banking site's clean, modern aesthetic, but reuses none
of its actual brand assets (logo, brand font, name).

Docker Compose (below) is the actual deliverable for this engagement, but the architecture — a
stateless backend behind a load balancer, with PostgreSQL and Redis as the only stateful
components — is designed to scale horizontally and deploy across multiple Availability Zones
without a redesign. The pieces that matter once there is more than one backend instance are in
place: rate limits and idempotency keys live in Redis, not in memory; the backend reads the client
address from `X-Forwarded-For` only when the request comes from the trusted proxy; the daily slot
generation takes a Postgres advisory lock, so only one instance runs it at a time; and a Redis
outage has a defined behaviour (see [When Redis is down](#when-redis-is-down)).

## Prerequisites

You need **Docker** and **Docker Compose v2** — nothing else. The frontend and backend are built
inside containers, so you don't need Node.js, Java, or Maven installed locally just to run the
demo (you will need them for local development without Docker — see
[Project structure](#project-structure)).

| Tool | Minimum version | Check with |
|---|---|---|
| Docker Engine / Docker Desktop | 24.x | `docker --version` |
| Docker Compose | v2 (bundled with modern Docker Desktop) | `docker compose version` |

Ports **4200**, **8080**, **5432**, and **6379** must be free on your machine (see
[Configuration](#configuration) to change any of them). Only 4200 is published on all
interfaces; the other three are bound to `127.0.0.1`.

## Quick start

```bash
git clone <this-repo-url> demo-booking
cd demo-booking
cp .env.example .env
# Fill in the required signing secret (the backend refuses to start without one).
# macOS (BSD sed):
sed -i '' "s|^APP_JWT_SIGNING_SECRET=.*|APP_JWT_SIGNING_SECRET=$(openssl rand -base64 32)|" .env
# Linux (GNU sed):
# sed -i "s|^APP_JWT_SIGNING_SECRET=.*|APP_JWT_SIGNING_SECRET=$(openssl rand -base64 32)|" .env
docker compose up --build
```

First run builds both images and runs the database migrations (including seed data — see
[Demo data](#demo-data)) automatically; expect it to take a few minutes the first time, seconds on
subsequent runs. When it's ready:

| Service | URL |
|---|---|
| Web app | http://localhost:4200 |
| API (proxied by Nginx) | http://localhost:4200/api/v1 |
| API docs (Swagger UI, loopback only) | http://127.0.0.1:8080/swagger-ui.html |
| API health check (loopback only) | http://127.0.0.1:8080/actuator/health |

The browser only ever talks to port 4200: Nginx serves the app and proxies `/api` to the backend
over the Compose network, so every API call is same-origin. The backend's own port is published
on `127.0.0.1` only, for Swagger UI and debugging.

Open http://localhost:4200 and you're looking at the landing page described in
[docs/USER-GUIDE.md](docs/USER-GUIDE.md).

## What you'll see running

- Booking a **New Account** appointment end-to-end, including the simulated confirmation-email
  popup and the confirmation screen with your reference code.
- Booking as an **Existing Client** using the [demo data](#demo-data) below, including what the
  "record not found" path looks like if you deliberately mistype a detail.
- **Cancelling** a booking both from the website ("Look up my booking") and by clicking the
  cancellation link in the simulated booking-receipt email.
- Letting a confirmation hold **expire** (wait 1 minute without clicking the link, then click it
  anyway) to see the plain "this link has expired — please book again" outcome.
- **Rescheduling** a confirmed booking to a different date/time, from either the post-confirmation
  screen or "Look up my booking".

## Demo data

A small set of fictional "existing client" records is seeded via a Flyway migration so the
Existing Client flow (see [docs/USER-GUIDE.md §3](docs/USER-GUIDE.md#3-existing-client-flow)) is
testable without a real core-banking system. **None of this is real personal data.**

| Name | Email | ID number | Account number |
|---|---|---|---|
| Thandiwe Nkosi | `thandiwe.demo@example.com` | `9203015800082` | `4051234567` |
| Johan van der Merwe | `johan.demo@example.com` | `8506120123089` | `4059876543` |
| Aisha Patel | `aisha.demo@example.com` | `9711220456081` | `4055551234` |
| Sipho Dlamini | `sipho.demo@example.com` | `8809085300084` | `4053339876` |

To see the "no match found" path, enter any combination **not** listed above exactly (e.g. a typo
in the account number) — the response is deliberately generic regardless of which field is wrong,
so the endpoint can't be used to enumerate valid emails/ID numbers/account numbers.

The full seed dataset — branches, service types, and this same client directory — is documented in
[docs/SEED-DATA.md](docs/SEED-DATA.md).

## Configuration

All configuration is read from environment variables, supplied via a `.env` file at the repo root
(git-ignored; start from `.env.example`, which is committed).

| Variable | Default | Purpose |
|---|---|---|
| `POSTGRES_DB` | `demo_booking` | Database name |
| `POSTGRES_USER` | `demo_booking` | Database user |
| `POSTGRES_PASSWORD` | `changeme` in `.env.example` | Database password. `changeme` is acceptable only for this local demo; set your own value for anything else, and never commit a real one |
| `POSTGRES_PORT` | `5432` | Host port mapped to PostgreSQL (on `127.0.0.1` only) |
| `REDIS_PORT` | `6379` | Host port mapped to Redis (on `127.0.0.1` only) |
| `BACKEND_PORT` | `8080` | Host port mapped to the Spring Boot API (on `127.0.0.1` only; the app itself reaches the API through Nginx) |
| `FRONTEND_PORT` | `4200` | Host port mapped to the Angular app and its `/api` proxy (served by Nginx) |
| `APP_JWT_SIGNING_SECRET` | *(required, generate your own)* | Signs the short-lived appointment-scoped access token issued on confirmation. At least 32 bytes; the backend refuses to start with an empty, short, or `changeme` value |
| `APP_CONFIRMATION_TOKEN_TTL_MINUTES` | `1` | How long a booking hold waits for confirmation before it's released — kept short for easy demoing/testing; a real deployment would likely use 10–15 |
| `APP_RESCHEDULE_MIN_NOTICE_HOURS` | `2` | Minimum-notice window for rescheduling (cancellation has none — it only ever frees capacity) |
| `APP_RATE_LIMIT_BOOKING_CREATE_MAX_ATTEMPTS` | `200` | Booking creations allowed per client IP per window |
| `APP_RATE_LIMIT_LOOKUP_MAX_ATTEMPTS` | `200` | Reference+email lookups allowed per client IP per window |
| `APP_RATE_LIMIT_DIRECTORY_VALIDATION_MAX_ATTEMPTS` | `5` | Existing-client directory checks allowed per client IP per window |
| `APP_RATE_LIMIT_WINDOW` | `PT15M` | The window all three rate limits count over (ISO-8601 duration) |
| `SPRING_PROFILES_ACTIVE` | `docker` | Selects the Docker-specific Spring config profile |

Generate reasonable local secrets rather than leaving the placeholders, e.g.:

```bash
openssl rand -base64 32
```

### Idempotency keys

`POST /api/v1/appointments` accepts an optional `Idempotency-Key` header. A retry with the same key
and body returns the original hold instead of reserving a second slot. The key is honoured for
`app.booking.idempotency-key-ttl`, which defaults to the confirmation TTL
(`APP_CONFIRMATION_TOKEN_TTL_MINUTES`) and may not be longer. A retry after that window creates a
new hold.

### When Redis is down

Redis holds the cache, the rate-limit counters and the idempotency keys. If it becomes unreachable,
the backend keeps serving what it safely can:

| Feature | Behaviour without Redis |
|---|---|
| Branch and service-type cache | Reads fall through to PostgreSQL (logged at WARN) |
| Booking-creation and lookup rate limits | Fail open: requests are allowed (logged at WARN) |
| Existing-client directory rate limit | Fails closed: `503` with `Retry-After: 5`, so the directory can't be probed without a limit |
| `Idempotency-Key` bookings | Fail closed: `503` with `Retry-After: 5`, so a retry can never create a second hold |

New-client bookings without an `Idempotency-Key`, lookups, confirmations, cancellations and
reschedules keep working.

## Project structure

```
demo-booking/
├── docs/                    # User-facing and API documentation (see index below)
├── frontend/                # Angular app, served by Nginx (see frontend/README.md)
├── backend/                 # Spring Boot app
│   └── src/main/java/com/demobooking/
│       ├── booking/         # Appointments: booking, lookup, reschedule, cancellation,
│       │   ├── api/         #   expiry sweep, idempotency, notifications
│       │   └── dto/         #   REST controllers and request/response records
│       ├── branch/          # Branches, service types, time-slot generation
│       │   ├── api/
│       │   └── dto/
│       ├── customer/        # Customers and the existing-client directory check
│       ├── notification/    # Simulated email rendering (no real email is sent)
│       ├── common/          # Error contract (problem+json), rate limiter, shared helpers
│       └── config/          # AppProperties, clock, security, cache, scheduling
├── k6/                      # Load test script
├── .github/workflows/ci.yml # CI pipeline
├── .zap/rules.tsv           # Accepted OWASP ZAP baseline findings
├── docker-compose.yml       # Full local stack
├── .env.example
└── README.md                # This file
```

Running anything outside Docker (the commands below, or [Running tests](#running-tests)) needs
**JDK 25** for the backend and **Node.js 22** for the frontend installed locally — Docker itself
only needs the versions in [Prerequisites](#prerequisites) above, since the images build their own.

To develop against the containerized database/backend while iterating on the frontend locally
(faster rebuild loop than a full container rebuild):

```bash
docker compose up postgres redis backend
cd frontend && npm install && npm start   # ng serve on http://localhost:4200; /api goes to :8080 via proxy.conf.json
```

Equivalently, to iterate on the backend alone against a containerized database:

```bash
docker compose up postgres redis
cd backend && ./mvnw spring-boot:run
```

## Running tests

```bash
# Backend: formatting check, unit and integration tests, coverage (Docker must be running)
cd backend && ./mvnw clean verify

# Frontend: unit tests
cd frontend && npm install && npm test

# End-to-end (Playwright), against the full Docker Compose stack
docker compose up -d --build
cd frontend && npm install && npx playwright install --with-deps chromium && npm run e2e
```

The backend has two tiers of tests:

- **Unit tests** (for example `AppointmentTest`, `AppPropertiesTest`, `SecureTokensTest`) are plain
  JUnit tests of domain rules and helpers, with no Spring context and no database.
- **Integration tests** (`@IntegrationTest`, including the `*ConcurrencyTest` classes) start the
  application against real PostgreSQL and Redis containers through Testcontainers. Anything that
  depends on the database — the overbooking invariant above all — is tested here rather than
  against a mock. The concurrency tests run real parallel work against slot capacity,
  idempotency keys, reschedules, slot generation and the expiry sweep.
  `RedisDownIntegrationTest` covers the [Redis-down behaviour](#when-redis-is-down).

`verify` also runs Spotless (`./mvnw spotless:apply` fixes formatting), an ArchUnit test that fails
on package cycles, and JaCoCo's 80% line-coverage check. The frontend has Vitest unit tests; the
Playwright e2e suite drives the full stack through a browser for the golden paths and their edge
cases (see [frontend/README.md](frontend/README.md)).

**Testcontainers with Colima.** With Colima instead of Docker Desktop, point Testcontainers at
Colima's socket before running Maven:

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

### Continuous integration

`.github/workflows/ci.yml` runs on every pull request: the backend `verify`, the frontend build,
tests and `npm audit`, OWASP Dependency-Check (through Maven, `-Powasp`), Trivy scans of both
images, and the e2e suite against the Compose stack, which includes an axe-core WCAG 2.1 AA scan
of the key pages. An OWASP ZAP baseline scan and a k6 load test run nightly, on pushes to `main`,
and on manual dispatch. Add an
`NVD_API_KEY` repository secret (free from the NVD) so the first Dependency-Check run doesn't take
20–30 minutes.

## API documentation

- **Interactive**: Swagger UI at http://127.0.0.1:8080/swagger-ui.html once the backend is running
  — generated live from the code. The backend port is published on loopback only.
- **Static contract**: [docs/api/openapi.yaml](docs/api/openapi.yaml) — the API contract, kept in
  sync with the code, including every error response.

## Stopping and cleaning up

```bash
docker compose down          # stop containers, keep data (Postgres volume persists)
docker compose down -v       # stop containers and delete all data — fresh start next run
```

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| `port is already allocated` | Something else is using 4200/8080/5432/6379 — stop it, or change the relevant `*_PORT` in `.env` |
| Backend container keeps restarting | Check `docker compose logs backend` — usually a Flyway migration or `.env` misconfiguration (the signing secret is missing or too short) |
| Backend fails with a Flyway `checksum mismatch` or `applied migration not resolved locally` after pulling | Your local database was migrated by an older version of the migrations. `docker compose down -v` (this deletes all local data), then `docker compose up --build` |
| `docker compose` → `unknown command: docker compose` / `unknown flag: --build` | The Compose v2 CLI plugin isn't registered. If you installed `docker` via Homebrew (rather than Docker Desktop), `brew install docker-compose` puts the plugin in Homebrew's own prefix, which Docker's CLI doesn't search — fix with: `mkdir -p ~/.docker/cli-plugins && ln -sfn "$(brew --prefix)/opt/docker-compose/bin/docker-compose" ~/.docker/cli-plugins/docker-compose`, then `docker compose version` to confirm |
| A container is stuck on `health: starting` and never turns healthy | If you've modified a `HEALTHCHECK`, use `127.0.0.1`, not `localhost` — Alpine images resolve `localhost` to `::1` (IPv6) first, and this project's Nginx listens on the IPv4 wildcard only, so a `localhost` healthcheck fails with "connection refused" even though Nginx is up. Spring Boot binds all interfaces, so either would work for the backend; its healthchecks use `127.0.0.1` too, for consistency |
| Frontend loads but API calls fail | The app calls the API through Nginx at `/api` on the same port. Confirm the backend is healthy (`docker compose ps`, or `curl http://127.0.0.1:8080/actuator/health`), then check `docker compose logs frontend` for proxy errors. Changing `FRONTEND_PORT` or `BACKEND_PORT` needs no other change |

## Known limitations

Honest gaps against "production-grade," kept visible rather than assumed away:

- **`/confirm/:token` confirms on a bare page load**, with no separate in-app click. This is a
  deliberate, accepted trade-off for this demo — corporate mail-security scanners that
  auto-`GET` links in an email could, in principle, silently confirm (and burn) a customer's
  single-use link before they open it. `CancellationPage` and `RescheduleConfirmPage` both use
  the safer GET-preview/explicit-click pattern instead; this one page trades that safety margin
  for a true one-click confirmation experience.
- **Rate limiting covers booking creation, lookup, and the existing-client directory check**, but
  not the confirm/cancel/reschedule-confirm token endpoints — acceptable here since those tokens
  are high-entropy (256-bit) and not practically guessable, but a gap against defense-in-depth.
- **No data retention or deletion policy** — cancelled/past appointments and existing-client
  directory records persist indefinitely; a real deployment would need an automated
  retention/anonymization job.
- **No self-service data-subject tooling** (access/correction/deletion requests) — POPIA-relevant
  for a real South African banking deployment, out of scope for a demo using entirely fictional
  seed data.
- **No formal breach-notification procedure, appointed Information Officer, or PIA** — the same
  data-minimization and security-safeguard reasoning throughout this codebase functions as a
  lightweight PIA, but a real deployment would need these formalized.

## Full documentation index

| Document | Purpose |
|---|---|
| [docs/USER-GUIDE.md](docs/USER-GUIDE.md) | Screen-by-screen walkthrough of the app from a customer's point of view |
| [docs/api/openapi.yaml](docs/api/openapi.yaml) | The API contract (OpenAPI 3.0), kept in sync with the code |
| [docs/SEED-DATA.md](docs/SEED-DATA.md) | Realistic demo branches, service types, and the existing-client directory |
| [frontend/README.md](frontend/README.md) | Frontend scripts, local development and e2e test settings |
