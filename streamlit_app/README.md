# Streamlit UI

Backend must be up on port 8080 (`server.port` in `application.yml`). Public GETs and `POST /api/v1/public/qr/*/sessions` are open; other `/api/v1/public/qr/**` need guest JWT; rest need staff JWT (`Authorization: Bearer`).

One command (API + UI + Postgres + Redis): `./scripts/dev-up.sh` then open http://localhost:8501 (stop: `./scripts/dev-down.sh`).

```bash
cd /home/shloke/Desktop/restaurant-saas
python3 -m pip install -r streamlit_app/requirements-ui.txt
streamlit run streamlit_app/Home.py
```

Optional local (no Docker UI): `API_BASE_URL=http://localhost:8080 streamlit run streamlit_app/Home.py`
