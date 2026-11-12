# One-shot deployment to Pepper: tablet app (signed release APK) + head face recognition server.
#
# Usage (from the project root, Windows PowerShell):
#   .\deploy-pepper.ps1 -TabletIP 10.1.3.120 -HeadIP 10.1.3.132
#
# Options:
#   -Rebuild     Rebuild the release APK before installing (otherwise the last build is reused)
#   -SkipApk     Only deploy the head
#   -SkipHead    Only install the tablet app
#   -InstallKey  Copy this PC's SSH key to the head first (asks the robot password once)
#
# IPs: the tablet IP is shown in the tablet notifications; the head IP is the Wi-Fi address
# of the robot itself (ssh nao@<HeadIP> must work). Both can change after a reboot.

param(
    [string]$TabletIP = "10.1.3.120",
    [string]$HeadIP = "10.1.3.132",
    [switch]$Rebuild,
    [switch]$SkipApk,
    [switch]$SkipHead,
    [switch]$InstallKey
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

function Step($text) { Write-Host ""; Write-Host "== $text" -ForegroundColor Cyan }
function Fail($text) { Write-Host "ERROR: $text" -ForegroundColor Red; exit 1 }
function Ok($text) { Write-Host "OK: $text" -ForegroundColor Green }

# --- Tools ------------------------------------------------------------------
$Sdk = "$env:LOCALAPPDATA\Android\Sdk"
$Adb = "$Sdk\platform-tools\adb.exe"
if (-not $env:JAVA_HOME) {
    $jdk = Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -Filter "*17*" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
}
if ($env:JAVA_HOME) { $env:Path = "$env:JAVA_HOME\bin;$env:Path" }

# --- Tablet: release APK ----------------------------------------------------
if (-not $SkipApk) {
    if (-not (Test-Path $Adb)) { Fail "adb not found at $Adb" }
    if (-not $env:JAVA_HOME) { Fail "JAVA_HOME not set and no JDK 17 found in ~/.jdks" }

    $unsigned = "app\build\outputs\apk\pepper\release\app-pepper-release-unsigned.apk"
    $signed = "app\build\outputs\apk\pepper\release\app-pepper-release-local.apk"

    if ($Rebuild -or -not (Test-Path $unsigned)) {
        Step "Building release APK (a few minutes)"
        # Works around 'Unable to establish loopback connection' (JDK Unix sockets on Windows)
        $env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$env:USERPROFILE\.gradle\tmp"
        New-Item -ItemType Directory -Force "$env:USERPROFILE\.gradle\tmp" | Out-Null
        & .\gradlew.bat assemblePepperRelease --no-daemon -q
        if ($LASTEXITCODE -ne 0) { Fail "Gradle build failed" }
        $env:JAVA_TOOL_OPTIONS = $null
    }

    Step "Signing APK with the local debug keystore (same key as the installed app)"
    $apksigner = Get-ChildItem "$Sdk\build-tools" -Recurse -Filter apksigner.bat | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
    if (-not $apksigner) { Fail "apksigner not found in $Sdk\build-tools" }
    & $apksigner sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $signed $unsigned
    if ($LASTEXITCODE -ne 0) { Fail "Signing failed" }
    & $apksigner verify $signed 2>$null
    if ($LASTEXITCODE -ne 0) { Fail "Signature verification failed" }
    Ok "APK signed"

    Step "Connecting to the tablet ($TabletIP)"
    & $Adb connect "${TabletIP}:5555" | Out-Host
    $state = & $Adb -s "${TabletIP}:5555" get-state 2>$null
    if ($state -ne "device") { Fail "Tablet not reachable over adb. Check the IP and that it is on the same Wi-Fi, then accept the debugging popup on the tablet." }

    Step "Installing the app (about 3-4 minutes over Wi-Fi, data and settings are kept)"
    & $Adb -s "${TabletIP}:5555" install -r $signed | Out-Host
    if ($LASTEXITCODE -ne 0) { Fail "adb install failed (INSTALL_FAILED_UPDATE_INCOMPATIBLE means a different signing key: do NOT uninstall, ask first)" }
    Ok "App installed on the tablet"
}

# --- Head: face recognition server ------------------------------------------
if (-not $SkipHead) {
    $FaceDir = Join-Path $Root "tools\pepper-face-recognition"
    if (-not (Test-Path "$FaceDir\output\packages.zip")) {
        Fail "tools\pepper-face-recognition\output\packages.zip missing. Run tools\pepper-face-recognition\build.ps1 first (Docker Desktop must be running)."
    }

    if ($InstallKey) {
        Step "Installing this PC's SSH key on the head (type the robot password once)"
        $keyFile = "$env:USERPROFILE\.ssh\id_ed25519"
        if (-not (Test-Path "$keyFile.pub")) { ssh-keygen -t ed25519 -N '""' -f $keyFile | Out-Host }
        $pub = (Get-Content "$keyFile.pub" -Raw).Trim()
        ssh nao@$HeadIP "mkdir -p ~/.ssh && chmod 700 ~/.ssh && touch ~/.ssh/authorized_keys && grep -qxF '$pub' ~/.ssh/authorized_keys || echo '$pub' >> ~/.ssh/authorized_keys; chmod 600 ~/.ssh/authorized_keys"
    }

    Step "Checking SSH access to the head ($HeadIP)"
    ssh -o BatchMode=yes -o ConnectTimeout=8 -o StrictHostKeyChecking=accept-new nao@$HeadIP "echo ready" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Passwordless SSH not available: the robot password will be asked several times." -ForegroundColor Yellow
        Write-Host "Tip: rerun with -InstallKey to type it only once." -ForegroundColor Yellow
    } else {
        Ok "SSH key works"
        $tools = ssh nao@$HeadIP "command -v curl >/dev/null && echo curl-ok; command -v python2 >/dev/null && echo py2-ok; command -v python3 >/dev/null && echo py3-ok"
        foreach ($t in "curl-ok", "py2-ok", "py3-ok") {
            if ($tools -notcontains $t) { Fail "Head is missing '$($t -replace '-ok','')' required by run_server.sh" }
        }
    }

    Step "Deploying packages, models and scripts to the head"
    & powershell -NoProfile -ExecutionPolicy Bypass -File "$FaceDir\deploy.ps1" -PepperIP $HeadIP
    if ($LASTEXITCODE -ne 0) { Fail "Head deployment failed" }

    Step "Restarting the face recognition server on the head"
    ssh nao@$HeadIP "pkill -f face_recognition_server.py; pkill -f camera_daemon.py; pkill -f run_server.sh; sleep 1; sed -i 's/\r$//' /home/nao/face_data/run_server.sh; chmod +x /home/nao/face_data/run_server.sh; nohup bash /home/nao/face_data/run_server.sh > /home/nao/face_data/server.log 2>&1 &"
    $up = $false
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 2
        $resp = ssh nao@$HeadIP "curl -s -m 2 http://127.0.0.1:5000/settings"
        if ($LASTEXITCODE -eq 0 -and $resp) { $up = $true; break }
    }
    if (-not $up) {
        Write-Host "Server did not answer on port 5000 within 60 s. Last lines of server.log:" -ForegroundColor Red
        ssh nao@$HeadIP "tail -40 /home/nao/face_data/server.log"
        exit 1
    }
    Ok "Face recognition server running (port 5000)"
}

# --- Restart the app so it reconnects to everything ---------------------------
if (-not $SkipApk) {
    Step "Restarting the app on the tablet"
    & $Adb -s "${TabletIP}:5555" shell am force-stop ch.fhnw.pepper_realtime.pepper
    & $Adb -s "${TabletIP}:5555" shell monkey -p ch.fhnw.pepper_realtime.pepper -c android.intent.category.LAUNCHER 1 | Out-Null
    Ok "App restarted"
}

Write-Host ""
Write-Host "Done. On the tablet, open People & Faces: Faces and Settings should load, Live should show people." -ForegroundColor Green
