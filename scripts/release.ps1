# ============================================================
# Fialka Android — Local Release Signing Script
# ============================================================
# Prerequisites:
#   - Android SDK installed (apksigner in build-tools)
#   - GnuPG (gpg) installed and configured
#   - GitHub CLI (gh) installed and authenticated
#   - version.properties at project root
#
# Usage:
#   .\scripts\release.ps1
#
# Steps performed:
#   1. Read version from version.properties
#   2. Download unsigned APK artifact from GitHub Actions (latest tag run)
#   3. Sign APK with apksigner (local keystore)
#   4. Compute SHA256 hash
#   5. GPG clearsign the hash file
#   6. Create (or update) GitHub Release with signed APK + hash files
# ============================================================

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ── Configuration ──────────────────────────────────────────────────────────────
# Reads RELEASE_STORE_FILE / RELEASE_KEY_ALIAS from local.properties (gitignored).
# Fallback: keystore expected one level above the repo root.

$LocalPropsFile = "$PSScriptRoot\..\local.properties"
$LocalProps = @{}
if (Test-Path $LocalPropsFile) {
    Get-Content $LocalPropsFile | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
        $k, $v = $_ -split '=', 2
        $LocalProps[$k.Trim()] = $v.Trim()
    }
}

$KeystorePath = if ($LocalProps['RELEASE_STORE_FILE']) {
    $LocalProps['RELEASE_STORE_FILE']
} else {
    # Default: fialka-release.p12 one level above the repo root
    Resolve-Path "$PSScriptRoot\..\.." -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.Path "fialka-release.p12" }
}

$KeyAlias = if ($LocalProps['RELEASE_KEY_ALIAS']) { $LocalProps['RELEASE_KEY_ALIAS'] } else { "fialka" }
$GpgKeyId = "0845E4B46420A2EA"
$Repo     = "FialkaApp/Fialka-Android"

# Find latest apksigner in Android SDK build-tools
$BuildToolsRoot = "$env:LOCALAPPDATA\Android\Sdk\build-tools"
$ApkSigner = Get-ChildItem "$BuildToolsRoot\*\apksigner.bat" |
             Sort-Object { [Version]($_.Directory.Name) } -Descending |
             Select-Object -First 1 -ExpandProperty FullName

if (-not $ApkSigner) {
    Write-Error "apksigner not found in $BuildToolsRoot"
    exit 1
}

# ── Read version ───────────────────────────────────────────────────────────────

$VersionProps = Get-Content "$PSScriptRoot\..\version.properties" |
                Where-Object { $_ -match '=' } |
                ForEach-Object { $k, $v = $_ -split '=', 2; @{ Key=$k.Trim(); Value=$v.Trim() } }

$VersionName = ($VersionProps | Where-Object { $_.Key -eq 'VERSION_NAME' }).Value
$VersionCode = ($VersionProps | Where-Object { $_.Key -eq 'VERSION_CODE' }).Value

if (-not $VersionName) { Write-Error "VERSION_NAME not found in version.properties"; exit 1 }

$TagName     = "v$VersionName"
$ApkName     = "fialka-$VersionName.apk"
$WorkDir     = "$env:TEMP\fialka-release-$VersionName"

Write-Host "==> Fialka $VersionName (versionCode $VersionCode)" -ForegroundColor Cyan

# ── Create working directory ──────────────────────────────────────────────────

if (Test-Path $WorkDir) { Remove-Item $WorkDir -Recurse -Force }
New-Item -ItemType Directory -Path $WorkDir | Out-Null

# ── Download unsigned APK from GitHub Actions ──────────────────────────────────

Write-Host "`n==> Downloading unsigned APK for $TagName ..." -ForegroundColor Yellow

$ArtifactName = "fialka-unsigned-$TagName"

# Find the workflow run triggered by this tag
$RunId = gh run list --repo $Repo --event push --branch $TagName `
         --json databaseId --jq '.[0].databaseId' 2>$null

if (-not $RunId) {
    # Fall back: find latest successful run on any ref
    $RunId = gh run list --repo $Repo --status success `
             --json databaseId --jq '.[0].databaseId'
}

if (-not $RunId) {
    Write-Error "No completed workflow run found. Ensure CI has completed for $TagName."
    exit 1
}

gh run download $RunId --repo $Repo --name $ArtifactName --dir $WorkDir

$UnsignedApk = Get-ChildItem "$WorkDir\*.apk" | Select-Object -First 1 -ExpandProperty FullName

if (-not $UnsignedApk) {
    Write-Error "No APK found after artifact download in $WorkDir"
    exit 1
}

Write-Host "   Downloaded: $UnsignedApk" -ForegroundColor Gray

# ── Sign the APK ──────────────────────────────────────────────────────────────

$SignedApk = Join-Path $WorkDir $ApkName

Write-Host "`n==> Signing APK with apksigner ..." -ForegroundColor Yellow

$StorePassword = Read-Host "Keystore password" -AsSecureString
$BstrPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($StorePassword)
$PlainPassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto($BstrPtr)

# Pass password via stdin — never exposed in process listing / event logs
"$PlainPassword`n$PlainPassword" | & $ApkSigner sign `
    --ks $KeystorePath `
    --ks-key-alias $KeyAlias `
    --ks-pass stdin `
    --key-pass stdin `
    --v1-signing-enabled false `
    --v2-signing-enabled true `
    --v3-signing-enabled true `
    --v4-signing-enabled true `
    --out $SignedApk `
    $UnsignedApk

if ($LASTEXITCODE -ne 0) { Write-Error "apksigner failed"; exit 1 }

# Verify
& $ApkSigner verify --verbose $SignedApk
if ($LASTEXITCODE -ne 0) { Write-Error "APK signature verification failed"; exit 1 }

Write-Host "   Signed: $SignedApk" -ForegroundColor Gray

# Zero password from both managed and unmanaged memory
$PlainPassword = $null
[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($BstrPtr)
[System.GC]::Collect()

# ── SHA256 ────────────────────────────────────────────────────────────────────

Write-Host "`n==> Computing SHA256 ..." -ForegroundColor Yellow

$Hash = (Get-FileHash $SignedApk -Algorithm SHA256).Hash.ToLower()
$HashFile = Join-Path $WorkDir "release-hashes.txt"
"$Hash  $ApkName" | Set-Content $HashFile -Encoding UTF8

Write-Host "   SHA256: $Hash" -ForegroundColor Gray

# ── GPG clearsign ─────────────────────────────────────────────────────────────

Write-Host "`n==> GPG clearsigning hashes ..." -ForegroundColor Yellow

$HashAscFile = Join-Path $WorkDir "release-hashes.txt.asc"

gpg --batch --yes `
    --local-user $GpgKeyId `
    --clearsign `
    --output $HashAscFile `
    $HashFile

if ($LASTEXITCODE -ne 0) { Write-Error "GPG signing failed"; exit 1 }

Write-Host "   Signature: $HashAscFile" -ForegroundColor Gray

# ── Generate release body ─────────────────────────────────────────────────────

$AscContent = Get-Content $HashAscFile -Raw
$GitSha     = git rev-parse HEAD
$GitShaShort = $GitSha.Substring(0, 7)

$IsPreRelease = $VersionName -match 'alpha|beta'
$PreReleaseNote = if ($IsPreRelease) { "`n> ⚠️ Pre-release — requires **Android 13+** (API 33+)`n" } else { "" }

$ReleaseBody = @"
## 🔐 Fialka ${VersionName} — ``versionCode ${VersionCode}``
${PreReleaseNote}
---

### 📥 Installation
1. Download ``fialka-${VersionName}.apk`` below
2. Enable **Install from unknown sources** on your device
3. Install the APK

---

### 🔧 APK Signing
Signed locally with **ECDSA P-256 + SHA-256** (PKCS12)
APK schemes **v2 + v3 + v4** enabled — v1 disabled

``````
sha256: ${Hash}  ${ApkName}
``````

---

### 🏗️ Build Provenance
Built from commit [``${GitShaShort}``](https://github.com/${Repo}/commit/${GitSha})
via GitHub Actions — source: [Fialka-Android@${TagName}](https://github.com/${Repo}/tree/${TagName})

---

### 🔐 Signature cryptographique

Signed SHA256 hashes of release files.
Developer GPG key: ``FialkaApp <devbot1667+fialka@gmail.com>``, ID ``0845E4B46420A2EA``

``````
${AscContent}
``````

**Verify:**
``````bash
gpg --keyserver keys.openpgp.org --recv-keys 0845E4B46420A2EA
gpg --verify release-hashes.txt.asc
sha256sum -c release-hashes.txt
``````
"@

$BodyFile = Join-Path $WorkDir "release-body.md"
$ReleaseBody | Set-Content $BodyFile -Encoding UTF8

# ── Publish GitHub Release ────────────────────────────────────────────────────

Write-Host "`n==> Publishing GitHub Release $TagName ..." -ForegroundColor Yellow

# Delete existing draft/release if present
gh release delete $TagName --repo $Repo --yes 2>$null

$PreReleaseFlag = if ($IsPreRelease) { "--prerelease" } else { "" }

$ReleaseArgs = @(
    "release", "create", $TagName,
    "--repo", $Repo,
    "--title", "🔐 Fialka $VersionName",
    "--notes-file", $BodyFile,
    $SignedApk,
    $HashFile,
    $HashAscFile
)

if ($IsPreRelease) { $ReleaseArgs += "--prerelease" }

gh @ReleaseArgs

if ($LASTEXITCODE -ne 0) { Write-Error "gh release create failed"; exit 1 }

Write-Host "`n✓ Release $TagName published successfully." -ForegroundColor Green
Write-Host "  https://github.com/$Repo/releases/tag/$TagName" -ForegroundColor Cyan

# ── Cleanup ───────────────────────────────────────────────────────────────────

Remove-Item $WorkDir -Recurse -Force
Write-Host "`n✓ Working directory cleaned up." -ForegroundColor Gray
