$ErrorActionPreference = "Continue"
Set-Location $PSScriptRoot

Write-Host "Stopping Docker infra containers..."
docker compose stop postgres minio

Write-Host "Stopping local dev processes (java/node)..."
Get-Process java,node -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

Write-Host "Done."
