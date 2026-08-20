$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

docker compose up --build --wait $args

Write-Host "UI  http://localhost:8501"
Write-Host "API http://localhost:8080"
Write-Host "Postgres host 127.0.0.1:5433 (container 5432)"
Write-Host "Redis    host 127.0.0.1:6380 (container 6379)"
