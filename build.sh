#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk" ]]; then
  echo "ANDROID_HOME or ANDROID_SDK_ROOT must be set" >&2
  exit 1
fi

# Prefer JDK 17 when available — d8 34.x can NPE on newer javac output.
if [[ -z "${JAVA_HOME:-}" ]]; then
  for candidate in \
    /usr/lib/jvm/java-17-openjdk-amd64 \
    /usr/lib/jvm/java-17-openjdk \
    /Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
  do
    if [[ -x "$candidate/bin/javac" ]]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi

build_tools="${BUILD_TOOLS:-$sdk/build-tools/34.0.0}"
platform_jar="${PLATFORM_JAR:-$sdk/platforms/android-34/android.jar}"
javac_bin="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
jar_bin="${JAVA_HOME:+$JAVA_HOME/bin/}jar"
keytool_bin="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"

for required in "$platform_jar" "$build_tools/aapt2" "$build_tools/d8" "$build_tools/zipalign" "$build_tools/apksigner"; do
  if [[ ! -e "$required" ]]; then
    echo "missing required Android SDK path: $required" >&2
    exit 1
  fi
done

manifest="app/src/main/AndroidManifest.xml"
res_dir="app/src/main/res"
src_dir="app/src/main/java"
out="build"

rm -rf "$out"
mkdir -p "$out/compiled" "$out/generated" "$out/classes" "$out/dex"

"$build_tools/aapt2" compile --dir "$res_dir" -o "$out/compiled/res.zip"
"$build_tools/aapt2" link -I "$platform_jar" --manifest "$manifest" -o "$out/unsigned.apk" --java "$out/generated" --auto-add-overlay -R "$out/compiled/res.zip"

mapfile -t sources < <(find "$src_dir" "$out/generated" -type f -name '*.java' | sort)
"$javac_bin" -g:none -encoding UTF-8 --release 8 -classpath "$platform_jar" -d "$out/classes" "${sources[@]}"
"$jar_bin" cf "$out/classes.jar" -C "$out/classes" .
"$build_tools/d8" --no-desugaring --min-api 23 --lib "$platform_jar" --output "$out/dex" "$out/classes.jar"

cp "$out/unsigned.apk" "$out/with-dex.apk"
"$jar_bin" uf "$out/with-dex.apk" -C "$out/dex" classes.dex
"$build_tools/zipalign" -f 4 "$out/with-dex.apk" "$out/aligned.apk"

keystore=""
keystore_pass="android"
key_pass="android"
key_alias="androiddebugkey"
cleanup_keystore=""

if [[ -n "${LIGHTUI_KEYSTORE:-}" && -f "${LIGHTUI_KEYSTORE}" ]]; then
  keystore="$LIGHTUI_KEYSTORE"
  keystore_pass="${LIGHTUI_KEYSTORE_PASSWORD:-android}"
  key_pass="${LIGHTUI_KEY_PASSWORD:-$keystore_pass}"
  key_alias="${LIGHTUI_KEY_ALIAS:-androiddebugkey}"
elif [[ -n "${LIGHTUI_KEYSTORE_BASE64:-}" ]]; then
  keystore="$out/release.keystore"
  cleanup_keystore="$keystore"
  printf '%s' "$LIGHTUI_KEYSTORE_BASE64" | base64 -d > "$keystore"
  keystore_pass="${LIGHTUI_KEYSTORE_PASSWORD:-android}"
  key_pass="${LIGHTUI_KEY_PASSWORD:-$keystore_pass}"
  key_alias="${LIGHTUI_KEY_ALIAS:-androiddebugkey}"
elif [[ "${LIGHTUI_REQUIRE_RELEASE_KEYSTORE:-}" == "1" ]]; then
  echo "Release signing required, but no LIGHTUI_KEYSTORE / LIGHTUI_KEYSTORE_BASE64 was provided." >&2
  echo "Add GitHub Actions secrets (see scripts/upload-signing-keystore.ps1)." >&2
  exit 1
else
  key_dir="${HOME}/.android"
  keystore="$key_dir/debug.keystore"
  if [[ ! -f "$keystore" ]]; then
    mkdir -p "$key_dir"
    "$keytool_bin" -genkeypair -keystore "$keystore" -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
  fi
fi

"$build_tools/apksigner" sign \
  --ks "$keystore" \
  --ks-key-alias "$key_alias" \
  --ks-pass "pass:$keystore_pass" \
  --key-pass "pass:$key_pass" \
  --out "$out/lightui-release.apk" \
  "$out/aligned.apk"
"$build_tools/apksigner" verify "$out/lightui-release.apk"

if [[ -n "$cleanup_keystore" ]]; then
  rm -f "$cleanup_keystore"
fi

echo "Built $out/lightui-release.apk"
