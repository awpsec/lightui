#!/usr/bin/env bash
# Build lightui and stage release/lightui-release.apk.
# To publish: tag and push, e.g.
#   git tag v1.0.6 && git push origin v1.0.6
# The Release GitHub Action uploads the APK to GitHub Releases.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [[ -z "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]]; then
  echo "ANDROID_HOME or ANDROID_SDK_ROOT must be set" >&2
  exit 1
fi

chmod +x ./build.sh
./build.sh

mkdir -p release
cp -f build/lightui-release.apk release/lightui-release.apk

version="$(rg -o 'APP_VERSION = \"([^\"]+)\"' -r '$1' app/src/main/java/com/lightos/minimalchat/MainActivity.java | head -1)"
echo "Staged release/lightui-release.apk (app version ${version:-unknown})"
echo "Publish with: git tag v${version} && git push origin v${version}"
