# ============================================================
# SmartNotes.ai Frontend Scaffolding Script
# Creates React + Vite (plain JS) project with full folder tree
# ============================================================

$ErrorActionPreference = "Stop"
$ProjectName = "smartnotes-frontend"

Write-Host ""
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  SmartNotes.ai Frontend Setup" -ForegroundColor Cyan
Write-Host "  by Himanshu Golani" -ForegroundColor Gray
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host ""

# ---- 1. Check prerequisites ----
Write-Host "[1/6] Checking prerequisites..." -ForegroundColor Yellow

try {
    $nodeVersion = node --version
    Write-Host "  Node.js found: $nodeVersion" -ForegroundColor Green
} catch {
    Write-Host "  ERROR: Node.js is not installed!" -ForegroundColor Red
    Write-Host "  Download from: https://nodejs.org/" -ForegroundColor Red
    exit 1
}

try {
    $npmVersion = npm --version
    Write-Host "  npm found: v$npmVersion" -ForegroundColor Green
} catch {
    Write-Host "  ERROR: npm is not available!" -ForegroundColor Red
    exit 1
}

# ---- 2. Check if folder already exists ----
Write-Host ""
Write-Host "[2/6] Checking workspace..." -ForegroundColor Yellow

if (Test-Path $ProjectName) {
    Write-Host "  WARNING: Folder '$ProjectName' already exists!" -ForegroundColor Yellow
    $response = Read-Host "  Delete and recreate? (y/N)"
    if ($response -eq "y" -or $response -eq "Y") {
        Remove-Item -Recurse -Force $ProjectName
        Write-Host "  Removed existing folder." -ForegroundColor Green
    } else {
        Write-Host "  Aborted by user." -ForegroundColor Red
        exit 0
    }
}

# ---- 3. Create Vite project (plain JS) ----
Write-Host ""
Write-Host "[3/6] Creating Vite + React project (plain JS)..." -ForegroundColor Yellow
Write-Host "  This may take a minute..." -ForegroundColor Gray

npm create vite@latest $ProjectName -- --template react

if ($LASTEXITCODE -ne 0) {
    Write-Host "  ERROR: Vite project creation failed!" -ForegroundColor Red
    exit 1
}

Set-Location $ProjectName
Write-Host "  Vite project created successfully." -ForegroundColor Green

# ---- 4. Clean default Vite files ----
Write-Host ""
Write-Host "[4/6] Cleaning default Vite boilerplate..." -ForegroundColor Yellow

$filesToRemove = @(
    "src/App.jsx",
    "src/App.css",
    "src/index.css",
    "src/main.jsx",
    "src/assets/react.svg",
    "public/vite.svg"
)

foreach ($file in $filesToRemove) {
    if (Test-Path $file) {
        Remove-Item $file -Force
        Write-Host "  Removed: $file" -ForegroundColor Gray
    }
}

if (Test-Path "src/assets") {
    Remove-Item "src/assets" -Recurse -Force
    Write-Host "  Removed: src/assets/" -ForegroundColor Gray
}

# ---- 5. Create folder structure & empty files ----
Write-Host ""
Write-Host "[5/6] Creating project folder structure..." -ForegroundColor Yellow

# Folders
$folders = @(
    "src/api",
    "src/components",
    "src/hooks",
    "public"
)

foreach ($folder in $folders) {
    if (-not (Test-Path $folder)) {
        New-Item -ItemType Directory -Path $folder -Force | Out-Null
    }
    Write-Host "  Folder: $folder/" -ForegroundColor Cyan
}

# Empty files (you'll paste code into these)
$files = @(
    "package.json",
    "vite.config.js",
    "index.html",
    ".env",
    "public/favicon.svg",
    "src/main.jsx",
    "src/App.jsx",
    "src/index.css",
    "src/api/notesApi.js",
    "src/components/Globe3D.jsx",
    "src/components/WelcomeScreen.jsx",
    "src/components/UrlInputForm.jsx",
    "src/components/ProcessingStatus.jsx",
    "src/components/ResultsViewer.jsx",
    "src/components/PdfViewer.jsx",
    "src/components/ExcalidrawViewer.jsx",
    "src/components/ParticleBackground.jsx",
    "src/hooks/useTypewriter.js"
)

foreach ($file in $files) {
    # Don't overwrite package.json / vite.config.js / index.html that Vite created;
    # we want to wipe them so user pastes the new content.
    if (Test-Path $file) {
        Clear-Content $file -Force
    } else {
        New-Item -ItemType File -Path $file -Force | Out-Null
    }
    Write-Host "  File:   $file" -ForegroundColor Green
}

# ---- 6. Install required dependencies ----
Write-Host ""
Write-Host "[6/6] Installing dependencies..." -ForegroundColor Yellow
Write-Host "  This will take 2-3 minutes..." -ForegroundColor Gray
Write-Host ""

# Production dependencies
$prodDeps = @(
    "@excalidraw/excalidraw@^0.17.6",
    "@react-three/drei@^9.108.4",
    "@react-three/fiber@^8.16.8",
    "axios@^1.7.4",
    "framer-motion@^11.3.21",
    "react@^18.3.1",
    "react-dom@^18.3.1",
    "react-pdf@^9.1.0",
    "three@^0.167.1"
)

# Dev dependencies
$devDeps = @(
    "@vitejs/plugin-react@^4.3.1",
    "vite@^5.4.1"
)

Write-Host "Installing production dependencies..." -ForegroundColor Cyan
npm install $prodDeps

if ($LASTEXITCODE -ne 0) {
    Write-Host "  WARNING: Some production dependencies failed." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Installing dev dependencies..." -ForegroundColor Cyan
npm install -D $devDeps

if ($LASTEXITCODE -ne 0) {
    Write-Host "  WARNING: Some dev dependencies failed." -ForegroundColor Yellow
}

# ---- DONE ----
Set-Location ..

Write-Host ""
Write-Host "==================================================" -ForegroundColor Green
Write-Host "  SETUP COMPLETE!" -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Project created at: " -NoNewline
Write-Host "$(Get-Location)\$ProjectName" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "  1. cd $ProjectName" -ForegroundColor White
Write-Host "  2. Paste the code into each file (all are empty and ready)" -ForegroundColor White
Write-Host "  3. Make sure your Spring Boot backend is running on port 8080" -ForegroundColor White
Write-Host "  4. Run: " -NoNewline -ForegroundColor White
Write-Host "npm run dev" -ForegroundColor Cyan
Write-Host "  5. Open: " -NoNewline -ForegroundColor White
Write-Host "http://localhost:5173" -ForegroundColor Cyan
Write-Host ""
Write-Host "Files awaiting your code:" -ForegroundColor Yellow
foreach ($file in $files) {
    Write-Host "  - $ProjectName\$file" -ForegroundColor Gray
}
Write-Host ""