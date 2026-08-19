# Restaurant SaaS (backend MVP)

Java 25 + Spring Boot 4.1 modular monolith. Money in paise (`long`). Qty `NUMERIC(19,4)`.

## Run tests

```bash
cd backend
./gradlew test
```

Docker required (Testcontainers: Postgres 17 + Redis 7).

## Run (Docker: API + Kadai POS + Postgres + Redis)

```bash
./scripts/dev-up.sh
```

UI http://localhost:8501 · API http://localhost:8080 · Postgres host `127.0.0.1:5433` · Redis host `127.0.0.1:6380` · stop: `./scripts/dev-down.sh`

The active UI is a Vite React app in `frontend/`; the Spring Boot API is in `backend/`. Compose serves the frontend through nginx on **8501** and proxies `/api` → `api:8080`.

### Local Vite (API already on :8080)

```bash
cd frontend && npm install && npm run dev
```

Vite http://localhost:5173 proxies `/api` → `http://localhost:8080`. Guest QR route: `/t/:token`. Staff: `/login` then Floor / Menu / Orders / Kitchen / Billing / Inventory / Reports / Settings.

Compose publishes Postgres `5433→5432` and Redis `6380→6379` so they do not clash with local services. The `api` container still uses `postgres:5432` and `redis:6379` on the Docker network.

## Run locally (Gradle, infra only in Docker)

```bash
docker compose up postgres redis -d
# create restaurant_app via Flyway on first api boot; set APP_FLYWAY_USER=restaurant_owner
# host ports are 5433/6380 (container ports stay 5432/6379)
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/restaurant \
SPRING_DATA_REDIS_PORT=6380 \
cd backend
./gradlew bootRun
```
