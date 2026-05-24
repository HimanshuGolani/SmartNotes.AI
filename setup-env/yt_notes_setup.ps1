# ============================================
# YT Multi-Modal Notes System - Setup Script
# ============================================

#Requires -RunAsAdministrator

$ErrorActionPreference = "Continue"

Write-Host "`n=== YT Notes System Setup Starting ===`n" -ForegroundColor Cyan

# ---------- 0. Pre-flight Check ----------
Write-Host "[0/6] Checking prerequisites..." -ForegroundColor Yellow

$prereqs = @("java","mvn","python","node","npm","git")

$missing = @()

foreach ($tool in $prereqs) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        $missing += $tool
    }
}

if ($missing.Count -gt 0) {
    Write-Host "Missing prerequisites: $($missing -join ', ')" -ForegroundColor Red
    exit 1
}

Write-Host "All prerequisites found" -ForegroundColor Green

# ---------- 1. Chocolatey ----------
if (-not (Get-Command choco -ErrorAction SilentlyContinue)) {

    Write-Host "`n[1/6] Installing Chocolatey..." -ForegroundColor Yellow

    Set-ExecutionPolicy Bypass -Scope Process -Force

    [System.Net.ServicePointManager]::SecurityProtocol =
        [System.Net.ServicePointManager]::SecurityProtocol -bor 3072

    Invoke-Expression (
        (New-Object System.Net.WebClient).DownloadString(
            'https://community.chocolatey.org/install.ps1'
        )
    )

    $env:Path =
        [System.Environment]::GetEnvironmentVariable("Path","Machine") +
        ";" +
        [System.Environment]::GetEnvironmentVariable("Path","User")

}
else {
    Write-Host "`n[1/6] Chocolatey already installed" -ForegroundColor Green
}

# ---------- 2. FFmpeg + yt-dlp ----------
Write-Host "`n[2/6] Installing FFmpeg + yt-dlp..." -ForegroundColor Yellow

choco install -y ffmpeg
choco install -y yt-dlp

$env:Path =
    [System.Environment]::GetEnvironmentVariable("Path","Machine") +
    ";" +
    [System.Environment]::GetEnvironmentVariable("Path","User")

# ---------- 3. Ollama ----------
Write-Host "`n[3/6] Installing Ollama..." -ForegroundColor Yellow

if (-not (Get-Command ollama -ErrorAction SilentlyContinue)) {

    $installer="$env:TEMP\OllamaSetup.exe"

    Invoke-WebRequest `
        -Uri "https://ollama.com/download/OllamaSetup.exe" `
        -OutFile $installer

    Start-Process $installer `
        -ArgumentList "/SILENT" `
        -Wait
}

Start-Process ollama `
    -ArgumentList "serve" `
    -WindowStyle Hidden

Start-Sleep 5

# ---------- 4. Models ----------
Write-Host "`n[4/6] Pulling models..." -ForegroundColor Yellow

$models=@(
"qwen2.5:7b-instruct-q4_K_M",
"llama3.1:8b-instruct-q4_K_M",
"minicpm-v:8b-2.6-q4_0",
"llava:7b",
"moondream",
"nomic-embed-text"
)

foreach($m in $models){
    Write-Host "Pulling $m"
    ollama pull $m
}

# ---------- 5. Whisper ----------
Write-Host "`n[5/6] Whisper setup..." -ForegroundColor Yellow

$projectDir="$env:USERPROFILE\yt-notes-system"

New-Item `
    -ItemType Directory `
    -Force `
    -Path $projectDir | Out-Null

Set-Location $projectDir

python -m venv whisper-env

& "$projectDir\whisper-env\Scripts\Activate.ps1"

pip install --upgrade pip

pip install torch torchvision torchaudio `
--index-url https://download.pytorch.org/whl/cu121

pip install faster-whisper fastapi uvicorn python-multipart

$whisper=@'
from fastapi import FastAPI, UploadFile, File
from faster_whisper import WhisperModel
import tempfile
import os
import uvicorn

app=FastAPI()

model=WhisperModel(
    "medium",
    device="cuda",
    compute_type="float16"
)

@app.post("/transcribe")
async def transcribe(file: UploadFile = File(...)):

    with tempfile.NamedTemporaryFile(
        delete=False,
        suffix=".mp3"
    ) as tmp:

        tmp.write(await file.read())
        path=tmp.name

    segments,info=model.transcribe(
        path,
        beam_size=5,
        word_timestamps=True
    )

    result=[
        {
            "start":s.start,
            "end":s.end,
            "text":s.text
        }
        for s in segments
    ]

    os.unlink(path)

    return {
        "language":info.language,
        "segments":result
    }

if __name__=="__main__":
    uvicorn.run(
        app,
        host="0.0.0.0",
        port=8001
    )
'@

$whisper | Out-File `
"$projectDir\whisper_service.py" `
-Encoding utf8

@"
@echo off
cd /d $projectDir
call whisper-env\Scripts\activate.bat
python whisper_service.py
"@ | Out-File `
"$projectDir\start-whisper.bat"

# ---------- 6. Excalidraw MCP ----------
Write-Host "`n[6/6] Installing MCP..." -ForegroundColor Yellow

npm install -g excalidraw-mcp

Write-Host "`nSETUP COMPLETE" -ForegroundColor Green
Write-Host "Whisper: http://localhost:8001"
Write-Host "Ollama : http://localhost:11434"