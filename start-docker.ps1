#!/usr/bin/env pwsh
# ─────────────────────────────────────────────────────────────────────────────
# Basalt — Docker Compose Full Stack Startup
# Builds and runs all services in Docker containers
# ─────────────────────────────────────────────────────────────────────────────

Write-Host ""
Write-Host "██████╗  █████╗ ███████╗ █████╗ ██╗  ████████╗" -ForegroundColor DarkGray
Write-Host "██╔══██╗██╔══██╗██╔════╝██╔══██╗██║  ╚══██╔══╝" -ForegroundColor DarkGray
Write-Host "██████╔╝███████║███████╗███████║██║     ██║   " -ForegroundColor Cyan
Write-Host "██╔══██╗██╔══██║╚════██║██╔══██║██║     ██║   " -ForegroundColor DarkGray
Write-Host "██████╔╝██║  ██║███████║██║  ██║███████╗██║   " -ForegroundColor DarkGray
Write-Host "╚═════╝ ╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝╚══════╝╚═╝   " -ForegroundColor DarkGray
Write-Host ""
Write-Host "  Basalt AI Assistant — Docker Deployment" -ForegroundColor Cyan
Write-Host "─────────────────────────────────────────────" -ForegroundColor DarkGray
Write-Host ""

# Check if Docker is running
Write-Host "[1/3] Checking Docker..." -ForegroundColor Yellow
try {
    docker info | Out-Null
    Write-Host "  ✓ Docker is running" -ForegroundColor Green
} catch {
    Write-Host "  ✗ Docker is not running. Please start Docker Desktop." -ForegroundColor Red
    exit 1
}

# Build and start all services
Write-Host "[2/3] Building and starting all services..." -ForegroundColor Yellow
Write-Host "  This may take a few minutes on first run..." -ForegroundColor DarkGray
docker compose up -d --build

if ($LASTEXITCODE -ne 0) {
    Write-Host "  ✗ Failed to start services" -ForegroundColor Red
    exit 1
}

Write-Host "  ✓ All services started" -ForegroundColor Green

# Wait for services to be healthy
Write-Host "[3/3] Waiting for services to be ready..." -ForegroundColor Yellow

# Wait for backend
Write-Host "  ⏳ Waiting for backend (may take 1-2 minutes)..." -ForegroundColor DarkGray
$maxWait = 120
$waited = 0
$backendReady = $false

while (-not $backendReady -and $waited -lt $maxWait) {
    Start-Sleep -Seconds 5
    $waited += 5
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/api/actuator/health" `
                                      -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            $backendReady = $true
        }
    } catch {
        # Still waiting
    }
}

if ($backendReady) {
    Write-Host "  ✓ Backend is ready" -ForegroundColor Green
} else {
    Write-Host "  ⚠ Backend health check timed out - check logs with: docker logs basalt-backend" -ForegroundColor Yellow
}

# Check frontend
Start-Sleep -Seconds 3
try {
    $response = Invoke-WebRequest -Uri "http://localhost" -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
    Write-Host "  ✓ Frontend is ready" -ForegroundColor Green
} catch {
    Write-Host "  ⚠ Frontend may still be starting - check logs with: docker logs basalt-frontend" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "════════════════════════════════════════════" -ForegroundColor Green
Write-Host "  🚀 Basalt is running!" -ForegroundColor Green
Write-Host "════════════════════════════════════════════" -ForegroundColor Green
Write-Host ""
Write-Host "  Frontend:  http://localhost:3000" -ForegroundColor Cyan
Write-Host "  Backend:   http://localhost:8080/api" -ForegroundColor Cyan
Write-Host "  Postgres:  localhost:5433" -ForegroundColor DarkGray
Write-Host "  Ollama:    http://localhost:11434" -ForegroundColor DarkGray
Write-Host ""
Write-Host "Useful commands:" -ForegroundColor Yellow
Write-Host "  View logs:       docker compose logs -f" -ForegroundColor DarkGray
Write-Host "  Stop services:   docker compose down" -ForegroundColor DarkGray
Write-Host "  Restart:         docker compose restart" -ForegroundColor DarkGray
Write-Host "  Pull Ollama model: docker exec basalt-ollama ollama pull llama3.2:1b" -ForegroundColor DarkGray
Write-Host ""
