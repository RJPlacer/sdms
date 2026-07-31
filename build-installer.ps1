param(
    [string]$ProjectRoot = $PSScriptRoot,
    [string]$AppName = "Athlete Track",
    [string]$Vendor = "Reniel John H. Placer (BSU)",
    [string]$Description = "Athlete Track - Sports Delegation Management System",
    [string]$Copyright = "Copyright 2026 Reniel John H. Placer (BSU)",
    [string]$MainJar = "sports-delegation-ms-1.0-SNAPSHOT-shaded.jar",
    [string]$MainClass = "com.sdms.AppLauncher",
    [string]$IconPath = "src/main/resources/images/app-logo.ico",
    [string]$OutputDir = "dist",
    [switch]$SkipBuild,
    [switch]$CleanOutput
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Require-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' is not installed or not in PATH."
    }
}

Push-Location $ProjectRoot
try {
    Require-Command "mvn"
    Require-Command "jpackage"

    if (-not $SkipBuild) {
        Write-Host "[1/3] Building shaded JAR with Maven..." -ForegroundColor Cyan
        & mvn -DskipTests package
        if ($LASTEXITCODE -ne 0) {
            throw "Maven build failed with exit code $LASTEXITCODE."
        }
    } else {
        Write-Host "[1/3] Skipping Maven build (-SkipBuild)." -ForegroundColor Yellow
    }

    $targetDir = Join-Path $ProjectRoot "target"
    $jarPath = Join-Path $targetDir $MainJar
    if (-not (Test-Path $jarPath)) {
        $knownJars = Get-ChildItem -Path $targetDir -Filter *.jar -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty Name
        throw "Main JAR not found: $jarPath`nAvailable JARs: $($knownJars -join ', ')"
    }

    $outputPath = Join-Path $ProjectRoot $OutputDir
    if (-not (Test-Path $outputPath)) {
        New-Item -ItemType Directory -Path $outputPath | Out-Null
    }

    if ($CleanOutput) {
        Write-Host "[2/3] Cleaning previous installer files..." -ForegroundColor Cyan
        @("$AppName-*.exe", "SDMS-*.exe") | ForEach-Object {
            Get-ChildItem -Path $outputPath -Filter $_ -ErrorAction SilentlyContinue |
                Remove-Item -Force -ErrorAction SilentlyContinue
        }
    } else {
        Write-Host "[2/3] Keeping existing installer files." -ForegroundColor Yellow
    }

    $jpArgs = @(
        "--type", "exe",
        "--input", "target",
        "--dest", $OutputDir,
        "--name", $AppName,
        "--vendor", $Vendor,
        "--description", $Description,
        "--copyright", $Copyright,
        "--main-jar", $MainJar,
        "--main-class", $MainClass,
        "--win-menu",
        "--win-shortcut"
    )

    $iconFullPath = Join-Path $ProjectRoot $IconPath
    if (Test-Path $iconFullPath) {
        $jpArgs += @("--icon", $IconPath)
    } else {
        Write-Warning "Icon not found at '$IconPath'. Packaging without custom icon."
    }

    Write-Host "[3/3] Running jpackage..." -ForegroundColor Cyan
    & jpackage @jpArgs
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed with exit code $LASTEXITCODE."
    }

    $installers = Get-ChildItem -Path $outputPath -Filter "$AppName-*.exe" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending

    if ($installers) {
        Write-Host "Installer ready:" -ForegroundColor Green
        $installers | Select-Object -First 3 FullName, LastWriteTime | Format-Table -AutoSize
    } else {
        Write-Warning "Packaging completed but no installer was found in '$outputPath'."
    }
}
finally {
    Pop-Location
}