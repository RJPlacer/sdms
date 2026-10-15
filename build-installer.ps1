param(
    [string]$ProjectRoot = $PSScriptRoot,
    [string]$AppName = "Athlete Track",
    [string]$Vendor = "Reniel John H. Placer (BSU)",
    [string]$Description = "Athlete Track - Sports Delegation Management System",
    [string]$Copyright = "Copyright 2026 Reniel John H. Placer (BSU)",
    [string]$AppVersion = "",
    [string]$MainJar = "",
    [string]$MainClass = "com.sdms.AppLauncher",
    [string]$IconPath = "src/main/resources/images/app-logo.ico",
    [string]$OutputDir = "dist",
    [ValidateSet("exe", "msi")]
    [string]$PackageType = "exe",
    [switch]$SkipBuild,
    [switch]$CleanOutput
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$UpgradeUuid = "a03536e0-1159-34a8-bf96-230f62f7a54d"

function Require-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' is not installed or not in PATH."
    }
}

function Enable-WixToolset {
    $programFilesX86 = [Environment]::GetFolderPath(
        [Environment+SpecialFolder]::ProgramFilesX86)
    $candidates = @(
        (Join-Path $env:ProgramFiles "WiX Toolset v5.0\bin"),
        (Join-Path $env:ProgramFiles "WiX Toolset v4.0\bin"),
        (Join-Path $programFilesX86 "WiX Toolset v3.14\bin"),
        (Join-Path $programFilesX86 "WiX Toolset v3.11\bin")
    )
    foreach ($candidate in $candidates) {
        if ((Test-Path (Join-Path $candidate "wix.exe")) -or
            ((Test-Path (Join-Path $candidate "candle.exe")) -and
             (Test-Path (Join-Path $candidate "light.exe")))) {
            $env:Path = "$candidate;$env:Path"
            return
        }
    }

    if ((Get-Command "wix.exe" -ErrorAction SilentlyContinue) -or
        ((Get-Command "candle.exe" -ErrorAction SilentlyContinue) -and
         (Get-Command "light.exe" -ErrorAction SilentlyContinue))) {
        return
    }

    throw "WiX Toolset 3.11-5.x is required. Install WiX 5 CLI, then run this script again."
}

function Enable-WixExtensions {
    $wix = Get-Command "wix.exe" -ErrorAction SilentlyContinue
    if (-not $wix) {
        return
    }

    $version = (& $wix.Source --version).Trim()
    if ($version -notmatch '^5\.') {
        return
    }

    $extensions = (& $wix.Source extension list --global 2>$null) -join "`n"
    if ($extensions -notmatch 'WixToolset\.Util\.wixext\s+5\.0\.2') {
        Write-Host "Installing the required WiX packaging extension..." -ForegroundColor Cyan
        & $wix.Source extension add --global WixToolset.Util.wixext/5.0.2
        if ($LASTEXITCODE -ne 0) {
            throw "Could not install the WiX Util extension."
        }
    }
}

Push-Location $ProjectRoot
try {
    Require-Command "mvn"
    Require-Command "jpackage"
    Enable-WixToolset
    Enable-WixExtensions

    [xml]$pom = Get-Content -LiteralPath (Join-Path $ProjectRoot "pom.xml") -Raw
    $artifactId = [string]$pom.project.artifactId
    if ([string]::IsNullOrWhiteSpace($AppVersion)) {
        $AppVersion = [string]$pom.project.properties.revision
        if ([string]::IsNullOrWhiteSpace($AppVersion)) {
            $AppVersion = [string]$pom.project.version
        }
    }
    if ($AppVersion -notmatch '^\d+\.\d+\.\d+$') {
        throw "AppVersion must use three numeric parts, for example 1.1.0."
    }
    if ([string]::IsNullOrWhiteSpace($MainJar)) {
        $MainJar = "$artifactId-$AppVersion-shaded.jar"
    }

    if (-not $SkipBuild) {
        Write-Host "[1/3] Building Athlete Track $AppVersion..." -ForegroundColor Cyan
        & mvn "-Drevision=$AppVersion" package
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
        @("$AppName-*.$PackageType", "SDMS-*.$PackageType") | ForEach-Object {
            Get-ChildItem -Path $outputPath -Filter $_ -ErrorAction SilentlyContinue |
                Remove-Item -Force -ErrorAction SilentlyContinue
        }
    } else {
        Write-Host "[2/3] Keeping existing installer files." -ForegroundColor Yellow
    }

    # Package only the runnable JAR, never test reports, databases, or other target contents.
    $packageInputDir = Join-Path $targetDir "installer-input-$AppVersion"
    New-Item -ItemType Directory -Path $packageInputDir -Force | Out-Null
    Copy-Item -LiteralPath $jarPath -Destination (Join-Path $packageInputDir $MainJar) -Force
    $unexpectedInputs = Get-ChildItem -LiteralPath $packageInputDir -Force |
        Where-Object { $_.Name -ne $MainJar }
    if ($unexpectedInputs) {
        throw "Installer input directory contains unexpected files. Use a fresh build directory."
    }

    $jpArgs = @(
        "--type", $PackageType,
        "--input", $packageInputDir,
        "--dest", $OutputDir,
        "--name", $AppName,
        "--vendor", $Vendor,
        "--description", $Description,
        "--copyright", $Copyright,
        "--app-version", $AppVersion,
        "--main-jar", $MainJar,
        "--main-class", $MainClass,
        "--win-upgrade-uuid", $UpgradeUuid,
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

    $installers = Get-ChildItem -Path $outputPath -Filter "$AppName-*.$PackageType" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending

    if ($installers) {
        Write-Host "Installer ready:" -ForegroundColor Green
        $installers | Select-Object -First 3 FullName, LastWriteTime | Format-Table -AutoSize
        Write-Host "This installer upgrades an existing Athlete Track installation in place." -ForegroundColor Green
    } else {
        Write-Warning "Packaging completed but no installer was found in '$outputPath'."
    }
}
finally {
    Pop-Location
}
