# Build face_recognition packages for Pepper (i386 architecture)
# Prerequisites: Docker Desktop installed and running

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

Write-Host "Building face_recognition for Pepper (i386)..." -ForegroundColor Cyan
Write-Host "This will take 15-30 minutes due to dlib compilation." -ForegroundColor Yellow
Write-Host ""

# Step 1: Build Docker image
Write-Host "[1/3] Building Docker image..." -ForegroundColor Yellow
docker build -t pepper-face-rec-builder .
# $ErrorActionPreference does not stop on native command failures in Windows PowerShell
if ($LASTEXITCODE -ne 0) { Write-Error "Docker build failed (exit code $LASTEXITCODE)"; exit 1 }

# Step 3: Extract artifacts
Write-Host ""
Write-Host "[3/3] Extracting artifacts..." -ForegroundColor Yellow
New-Item -ItemType Directory -Force -Path "output" | Out-Null
docker run --rm -v "${PWD}/output:/output" pepper-face-rec-builder
if ($LASTEXITCODE -ne 0) { Write-Error "Artifact extraction failed (exit code $LASTEXITCODE)"; exit 1 }

Write-Host ""
Write-Host "Build complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Output files in ./output/:" -ForegroundColor Cyan
Write-Host "  - packages.zip"
Write-Host "  - models/"
Write-Host ""
Write-Host "Next step: Run deploy.ps1 to install on Pepper" -ForegroundColor Yellow
