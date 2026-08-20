# Restaurant SaaS

Java 25 + Spring Boot 4.1 modular monolith backend with a Streamlit UI. Money in paise (`long`). Qty `NUMERIC(19,4)`.

## Run tests

```bash
cd backend
./gradlew test
```

Docker is required for the integration test stack and the easiest local startup path.

## Run With Docker

macOS / Linux:

```bash
./scripts/dev-up.sh
```

Windows PowerShell:

```powershell
.\scripts\dev-up.ps1
```

UI http://localhost:8501 · API http://localhost:8080 · Postgres host `127.0.0.1:5433` · Redis host `127.0.0.1:6380`

Stop:

```bash
./scripts/dev-down.sh
```

```powershell
.\scripts\dev-down.ps1
```

Compose starts:
- `backend/` as the Spring Boot API on `:8080`
- `streamlit_app/` as the UI on `:8501`
- Postgres 17 on host port `5433`
- Redis 7 on host port `6380`

The UI talks to the API through `API_BASE_URL`, which Compose points at `http://api:8080`.

## Run Locally Without Docker UI

First start only the infra containers:

```bash
docker compose up postgres redis -d
```

Then start the backend with Java 25:

```bash
cd backend
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/restaurant \
SPRING_DATA_REDIS_PORT=6380 \
APP_FLYWAY_USER=restaurant_owner \
APP_FLYWAY_PASSWORD=owner \
./gradlew bootRun
```

On Windows PowerShell:

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:5433/restaurant"
$env:SPRING_DATA_REDIS_PORT="6380"
$env:APP_FLYWAY_USER="restaurant_owner"
$env:APP_FLYWAY_PASSWORD="owner"
cd backend
.\gradlew.bat bootRun
```

Then start the Streamlit UI:

```bash
python -m pip install -r streamlit_app/requirements-ui.txt
streamlit run streamlit_app/Home.py
```

Optional:

```bash
API_BASE_URL=http://localhost:8080 streamlit run streamlit_app/Home.py
```
