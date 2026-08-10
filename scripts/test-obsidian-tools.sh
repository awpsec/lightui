#!/usr/bin/env bash
# Host-side unit tests for Obsidian tool parsing / path helpers.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
OUT="/tmp/obsidian-tools-test"
rm -rf "$OUT" && mkdir -p "$OUT"
"$JAVA_HOME/bin/javac" --release 8 -d "$OUT" \
  app/src/main/java/com/lightos/minimalchat/ObsidianTools.java \
  scripts/ObsidianToolsTest.java
"$JAVA_HOME/bin/java" -cp "$OUT" com.lightos.minimalchat.ObsidianToolsTest
echo "ObsidianTools tests passed"
