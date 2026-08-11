#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
OUT=/tmp/lightui-tool-text-test
rm -rf "$OUT"
mkdir -p "$OUT"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -d "$OUT" \
  app/src/main/java/com/lightos/minimalchat/ToolText.java \
  scripts/ToolTextTest.java
"$JAVA_HOME/bin/java" -cp "$OUT" com.lightos.minimalchat.ToolTextTest
echo "ToolText tests passed"
