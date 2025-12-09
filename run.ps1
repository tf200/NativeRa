# NewRa App - Build and Run Script
# Builds the debug APK and installs/launches on connected emulator or device

$ErrorActionPreference = "Stop"

# Helper functions
function Write-Status { param($msg) Write-Host "[*] $msg" -ForegroundColor Cyan }
function Write-Success { param($msg) Write-Host "[OK] $msg" -ForegroundColor Green }
function Write-Err { param($msg) Write-Host "[X] $msg" -ForegroundColor Red }

Write-Host ""
Write-Host "========================================" -ForegroundColor Magenta
Write-Host "       NewRa App - Build and Run       " -ForegroundColor Magenta
Write-Host "========================================" -ForegroundColor Magenta
Write-Host ""

# Get ADB path (try common locations)
$adbPaths = @(
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    "$env:ANDROID_HOME\platform-tools\adb.exe",
    "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe",
    "adb"
)

$adb = $null
foreach ($path in $adbPaths) {
    if (Get-Command $path -ErrorAction SilentlyContinue) {
        $adb = $path
        break
    }
}

if (-not $adb) {
    Write-Err "ADB not found! Please ensure Android SDK is installed and ADB is in PATH."
    exit 1
}

# Configuration
$packageName = "com.taha.newraapp"
$activityName = "com.taha.newraapp.MainActivity"
$apkPath = "app\build\outputs\apk\debug\app-debug.apk"

# Step 1: Build
Write-Status "Building debug APK..."
& .\gradlew assembleDebug --console=plain
if ($LASTEXITCODE -ne 0) {
    Write-Err "Build failed!"
    exit 1
}
Write-Success "Build successful!"

# Step 2: Check for devices
Write-Status "Checking for connected devices..."
$devices = & $adb devices | Select-String -Pattern "device$" | ForEach-Object { $_.Line.Split()[0] }

if (-not $devices) {
    Write-Err "No devices/emulators connected!"
    Write-Host ""
    Write-Host "To start an emulator, run:" -ForegroundColor Yellow
    Write-Host "  .\start-emulator.ps1" -ForegroundColor Gray
    exit 1
}

$device = $devices | Select-Object -First 1
Write-Success "Found device: $device"

# Step 3: Install APK
Write-Status "Installing APK..."
& $adb -s $device install -r $apkPath
if ($LASTEXITCODE -ne 0) {
    Write-Err "Installation failed!"
    exit 1
}
Write-Success "APK installed!"

# Step 4: Launch app
Write-Status "Launching app..."
& $adb -s $device shell am start -n "$packageName/$activityName"
if ($LASTEXITCODE -ne 0) {
    Write-Err "Failed to launch app!"
    exit 1
}

Write-Host ""
Write-Success "App is running on $device"
Write-Host ""
