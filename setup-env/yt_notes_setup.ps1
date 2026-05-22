# ============================================
# YT Multi-Modal Notes System - Setup Script
# (Assumes Java, Maven, Python, Node, Git pre-installed)
# ============================================
 
#Requires -RunAsAdministrator
 
$ErrorActionPreference = "Continue"
Write-Host "`n=== YT Notes System Setup Starting ===`n" -ForegroundColor Cyan
 
# ---------- 0. Pre-flight Check ----------
Write-Host "[0/6] Checking prerequisites..." -ForegroundColor Yellow
$prereqs = @("java", "mvn", "python", "node", "npm", "git")
$missing = @()
foreach ($tool in $prereqs) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        $missing += $tool
    }
}
if ($missing.Count -gt 0) {
    Write-Host "❌ Missing prerequisites: $($missing -join ', ')" -ForegroundColor Red
    Write-Host "Install them first, then rerun this script." -ForegroundColor Red
    exit 1
}
Write-Host "All prerequisites found ✓" -ForegroundColor Green
 
# ---------- 1. Install Chocolatey ----------
if (-not (Get-Command choco -ErrorAction SilentlyContinue)) {
    Write-Host "`n[1/6] Installing Chocolatey..." -ForegroundColor Yellow
    Set-ExecutionPolicy Bypass -Scope Process -Force
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072
    iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
} else {
    Write-Host "`n[1/6] Chocolatey already installed ✓" -ForegroundColor Green
}
 
# ---------- 2. Install FFmpeg + yt-dlp ----------
Write-Host "`n[2/6] Installing FFmpeg and yt-dlp..." -ForegroundColor Yellow
choco install -y ffmpeg
choco install -y yt-dlp
 
# Refresh PATH
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
 
# ---------- 3. Install Ollama ----------
Write-Host "`n[3/6] Installing Ollama..." -ForegroundColor Yellow
if (-not (Get-Command ollama -ErrorAction SilentlyContinue)) {
    $ollamaInstaller = "$env:TEMP\OllamaSetup.exe"
    Invoke-WebRequest -Uri "https://ollama.com/download/OllamaSetup.exe" -OutFile $ollamaInstaller
    Start-Process -FilePath $ollamaInstaller -ArgumentList "/SILENT" -Wait
    $env:Path += ";$env:LOCALAPPDATA\Programs\Ollama"
} else {
    Write-Host "Ollama already installed ✓" -ForegroundColor Green
}
 
# Start Ollama service
Write-Host "Starting Ollama service..." -ForegroundColor Yellow
Start-Process -FilePath "ollama" -ArgumentList "serve" -WindowStyle Hidden
Start-Sleep -Seconds 5
 
# ---------- 4. Pull Ollama Models ----------
Write-Host "`n[4/6] Pulling Ollama models (~15GB total)..." -ForegroundColor Yellow
 
$models = @(
    "qwen2.5:7b-instruct-q4_K_M",   # Main LLM for notes
    "llama3.1:8b-instruct-q4_K_M",  # Alternative LLM
    "minicpm-v:8b-2.6-q4_0",        # Vision model
    "llava:7b",                      # Backup vision
    "moondream",                     # Lightweight vision
    "nomic-embed-text"               # Embeddings
)
 
foreach ($model in $models) {
    Write-Host "  → Pulling $model..." -ForegroundColor Cyan
    ollama pull $model
}
 
# ---------- 5. Setup Python venv + Whisper Service ----------
Write-Host "`n[5/6] Setting up Whisper Python environment..." -ForegroundColor Yellow
$projectDir = "$env:USERPROFILE\yt-notes-system"
New-Item -ItemType Directory -Force -Path $projectDir | Out-Null
Set-Location $projectDir
 
python -m venv whisper-env
& "$projectDir\whisper-env\Scripts\Activate.ps1"
 
pip install --upgrade pip
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu121
pip install faster-whisper fastapi uvicorn python-multipart
 
# Whisper FastAPI sidecar
$whisperService = @'
from fastapi import FastAPI, UploadFile, File
from faster_whisper import WhisperModel
import tempfile, os, uvicorn
 
app = FastAPI()
# medium model - good for RTX 4050 6GB
model = WhisperModel("medium", device="cuda", compute_type="float16")
 
@app.post("/transcribe")
async def transcribe(file: UploadFile = File(...)):
    with tempfile.NamedTemporaryFile(delete=False, suffix=".mp3") as tmp:
        tmp.write(await file.read())
        path = tmp.name
    segments, info = model.transcribe(path, beam_size=5, word_timestamps=True)
    result = [{"start": s.start, "end": s.end, "text": s.text} for s in segments]
    os.unlink(path)
    return {"language": info.language, "segments": result}
 
@app.get("/health")
def health():
    return {"status": "ok"}
 
if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
'@
$whisperService | Out-File -FilePath "$projectDir\whisper_service.py" -Encoding utf8
 
$startScript = @"
@echo off
cd /d $projectDir
call whisper-env\Scripts\activate.bat
python whisper_service.py
"@
$startScript | Out-File -FilePath "$projectDir\start-whisper.bat" -Encoding ascii
 
# ---------- 6. Install Excalidraw MCP Server ----------
Write-Host "`n[6/6] Installing Excalidraw MCP server..." -ForegroundColor Yellow
npm install -g excalidraw-mcp 2>$null
 
# ---------- Verification ----------
Write-Host "`n=== VERIFICATION ===" -ForegroundColor Cyan
Write-Host "FFmpeg:  " -NoNewline; ffmpeg -version 2>&1 | Select-Object -First 1
Write-Host "yt-dlp:  " -NoNewline; yt-dlp --version
Write-Host "Ollama:  " -NoNewline; ollama --version
Write-Host "`nInstalled Ollama models:" -ForegroundColor Yellow
ollama list
 
Write-Host "`n=== ✅ SETUP COMPLETE ===" -ForegroundColor Green
Write-Host "`nProject directory: $projectDir" -ForegroundColor Cyan
Write-Host "`nTo start Whisper service:" -ForegroundColor Yellow
Write-Host "  $projectDir\start-whisper.bat"
Write-Host "`nOllama:  http://localhost:11434"
Write-Host "Whisper: http://localhost:8000`n"