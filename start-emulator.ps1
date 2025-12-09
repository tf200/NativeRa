# NewRa App - Start Emulator Script
# Lists and starts an Android emulator

$ErrorActionPreference = "Stop"

# Get emulator path
$emulatorPaths = @(
    "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe",
    "$env:ANDROID_HOME\emulator\emulator.exe",
    "$env:ANDROID_SDK_ROOT\emulator\emulator.exe",
    "emulator"
)

$emulator = $null
foreach ($path in $emulatorPaths) {
    if (Get-Command $path -ErrorAction SilentlyContinue) {
        $emulator = $path
        break
    }
}

if (-not $emulator) {
    Write-Host "Emulator not found! Please ensure Android SDK is installed." -ForegroundColor Red
    exit 1
}

# List available AVDs
Write-Host ""
Write-Host "Available AVDs:" -ForegroundColor Cyan
Write-Host "===============" -ForegroundColor Cyan
$avds = & $emulator -list-avds
$i = 1
$avdList = @()
foreach ($avd in $avds) {
    if ($avd) {
        Write-Host "  $i. $avd" -ForegroundColor White
        $avdList += $avd
        $i++
    }
}

if ($avdList.Count -eq 0) {
    Write-Host "No AVDs found!" -ForegroundColor Red
    Write-Host "Create one using AVD Manager or sdkmanager." -ForegroundColor Yellow
    exit 1
}

Write-Host ""
$selection = Read-Host "Enter AVD number to start (or press Enter for first)"

if ([string]::IsNullOrEmpty($selection)) {
    $selectedAvd = $avdList[0]
} else {
    $index = [int]$selection - 1
    if ($index -lt 0 -or $index -ge $avdList.Count) {
        Write-Host "Invalid selection!" -ForegroundColor Red
        exit 1
    }
    $selectedAvd = $avdList[$index]
}

Write-Host ""
Write-Host "Starting emulator: $selectedAvd" -ForegroundColor Green
Write-Host "(This may take a minute...)" -ForegroundColor Gray
Write-Host ""

# Start emulator in background
Start-Process -FilePath $emulator -ArgumentList "-avd", $selectedAvd -WindowStyle Hidden

Write-Host "Emulator starting in background!" -ForegroundColor Green
Write-Host "Run .\run.ps1 once the emulator is ready." -ForegroundColor Cyan
