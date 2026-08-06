# Upload your Android keystore as GitHub Actions secrets for lightui releases.
# Recommended: use the same keystore already installed on your phone / friends' phones
# (usually %USERPROFILE%\.android\debug.keystore) so in-app updates install cleanly.
#
# Usage:
#   .\scripts\upload-signing-keystore.ps1
#   .\scripts\upload-signing-keystore.ps1 -Keystore "C:\path\to\release.keystore" -Alias androiddebugkey -StorePassword android -KeyPassword android

param(
  [string]$Keystore = "$env:USERPROFILE\.android\debug.keystore",
  [string]$Alias = "androiddebugkey",
  [string]$StorePassword = "android",
  [string]$KeyPassword = "android",
  [string]$Repo = "awpsec/lightui"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
  Write-Error "GitHub CLI (gh) is required. Install from https://cli.github.com/ and run gh auth login."
}

if (-not (Test-Path -LiteralPath $Keystore)) {
  Write-Error "Keystore not found: $Keystore"
}

Write-Host "Uploading signing secrets for $Repo"
Write-Host "Keystore: $Keystore"
Write-Host "Alias:    $Alias"

$bytes = [System.IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $Keystore))
$b64 = [Convert]::ToBase64String($bytes)

$b64 | gh secret set LIGHTUI_KEYSTORE_BASE64 --repo $Repo
$StorePassword | gh secret set LIGHTUI_KEYSTORE_PASSWORD --repo $Repo
$Alias | gh secret set LIGHTUI_KEY_ALIAS --repo $Repo
$KeyPassword | gh secret set LIGHTUI_KEY_PASSWORD --repo $Repo

Write-Host ""
Write-Host "Secrets set. Next release tag will sign with this keystore."
Write-Host "If phones already have lightui signed with a different key, those installs need one uninstall before updating."
