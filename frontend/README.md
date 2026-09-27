# Frontend

The Angular app for Demo Booking: standalone components, signals, Angular Material, and strict
TypeScript and template checking. In Docker Compose it is built into an Nginx image that serves
the app and proxies `/api` to the backend. See the [root README](../README.md) for the whole stack.

You need **Node.js 22** for everything below.

## Scripts

| Command | What it does |
|---|---|
| `npm start` | `ng serve` on http://localhost:4200. Requests to `/api` are proxied to the backend on `localhost:8080` (see `proxy.conf.json`), so start the backend first. |
| `npm test` | Unit tests with Vitest through `ng test`. Add `-- --watch=false` for a single run, as CI does. |
| `npm run build` | Production build into `dist/frontend/browser`. Fails if the initial bundle breaks its budget in `angular.json`. |
| `npm run e2e` | Playwright end-to-end tests (`playwright test`) against a running stack. There is no `ng e2e` target. |

### Local development against the containerised backend

```bash
# from the repo root
docker compose up -d postgres redis backend
cd frontend && npm install && npm start
```

### End-to-end tests

The Playwright suite in `e2e/` drives a real browser against the full stack. It doesn't start the
stack itself, so bring it up first:

```bash
# from the repo root
docker compose up -d --build
cd frontend
npm install
npx playwright install --with-deps chromium
npm run e2e
```

| Variable | Default | Purpose |
|---|---|---|
| `E2E_BASE_URL` | `http://localhost:4200` | Where the browser opens the app |
| `E2E_API_BASE_URL` | `http://localhost:4200/api/v1` | Where the tests call the API directly, to set up data |

Each spec file books at its own seeded branch (`E2E_BRANCH` in `e2e/helpers.ts`), so the parallel
workers never compete for a slot. The suite cleans up after itself: a fixture (`e2e/fixtures.ts`)
cancels every appointment a test confirmed, and holds it never confirmed are released by the
backend's expiry sweep within the 1-minute confirmation TTL. Repeated runs therefore don't fill
the calendar. `a11y.spec.ts` scans the key pages with axe-core for WCAG 2.1 AA violations.

The existing-client directory check allows 5 attempts per client IP per 15 minutes, and the suite
uses some of them. If repeated runs start failing with `429`, clear the counters:
`docker compose exec redis sh -c "redis-cli --scan --pattern 'rate-limit:*' | xargs -r redis-cli del"`.

## Layout

```
src/app/
├── app.routes.ts      # Routes; every page except the landing page is lazy-loaded
├── core/
│   ├── http/          # apiUrl() and apiErrorDetail() (reads problem+json errors)
│   ├── models/        # API types
│   ├── pipes/         # status label and time formatting
│   └── services/      # HTTP services and the simulated inbox hand-off
├── pages/             # One folder per route; browse/ holds its step components
├── components/        # Shared components (appointment management, simulated email)
└── shared/            # Slot picker and date helpers
```
