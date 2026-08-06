#!/usr/bin/env bash
# Upload an Android keystore as GitHub Actions secrets for lightui releases.
# Recommended: use the keystore already on devices so updates install cleanly.
#
# Usage:
#   ./scripts/upload-signing-keystore.sh
#   KEYSTORE=~/.android/debug.keystore ./scripts/upload-signing-keystore.sh

set -euo pipefail

REPO="${REPO:-awpsec/lightui}"
KEYSTORE="${KEYSTORE:-$HOME/.android/debug.keystore}"
ALIAS="${ALIAS:-androiddebugkey}"
STORE_PASSWORD="${STORE_PASSWORD:-android}"
KEY_PASSWORD="${KEY_PASSWORD:-android}"

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI (gh) is required. Install from https://cli.github.com/ and run gh auth login." >&2
  exit 1
fi

if [[ ! -f "$KEYSTORE" ]]; then
  echo "Keystore not found: $KEYSTORE" >&2
  exit 1
fi

echo "Uploading signing secrets for $REPO"
echo "Keystore: $KEYSTORE"
echo "Alias:    $ALIAS"

base64 -w0 "$KEYSTORE" 2>/dev/null | gh secret set LIGHTUI_KEYSTORE_BASE64 --repo "$REPO" \
  || base64 "$KEYSTORE" | tr -d '\n' | gh secret set LIGHTUI_KEYSTORE_BASE64 --repo "$REPO"
printf '%s' "$STORE_PASSWORD" | gh secret set LIGHTUI_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS" | gh secret set LIGHTUI_KEY_ALIAS --repo "$REPO"
printf '%s' "$KEY_PASSWORD" | gh secret set LIGHTUI_KEY_PASSWORD --repo "$REPO"

echo
echo "Secrets set. Next release tag will sign with this keystore."
echo "If phones already have lightui signed with a different key, those installs need one uninstall before updating."
