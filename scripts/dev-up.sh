#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose up --build --wait "$@"
echo "UI  http://localhost:8501  (Streamlit app)"
echo "API http://localhost:8080"
echo "Postgres host 127.0.0.1:5433 (container 5432)"
echo "Redis    host 127.0.0.1:6380 (container 6379)"
