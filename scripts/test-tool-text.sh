#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
OUT=/tmp/lightui-tool-text-test
JSON_JAR="${JSON_JAR:-/tmp/lightui-lib/json-20240303.jar}"
if [[ ! -f "$JSON_JAR" ]]; then
  mkdir -p "$(dirname "$JSON_JAR")"
  curl -fsSL -o "$JSON_JAR" https://repo1.maven.org/maven2/org/json/json/20240303/json-20240303.jar
fi
rm -rf "$OUT"
mkdir -p "$OUT"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -cp "$JSON_JAR" -d "$OUT" \
    app/src/main/java/com/lightos/minimalchat/ToolText.java \
    app/src/main/java/com/lightos/minimalchat/AgentTools.java \
    app/src/main/java/com/lightos/minimalchat/UpdateDownload.java \
    scripts/ToolTextTest.java \
    scripts/AgentToolsTest.java \
    scripts/ToolLoopHarness.java \
    scripts/UpdateDownloadTest.java
"$JAVA_HOME/bin/java" -Djava.net.preferIPv4Stack=true -cp "$OUT:$JSON_JAR" com.lightos.minimalchat.ToolTextTest
"$JAVA_HOME/bin/java" -Djava.net.preferIPv4Stack=true -cp "$OUT:$JSON_JAR" com.lightos.minimalchat.AgentToolsTest
"$JAVA_HOME/bin/java" -Djava.net.preferIPv4Stack=true -cp "$OUT:$JSON_JAR" com.lightos.minimalchat.ToolLoopHarness
"$JAVA_HOME/bin/java" -Djava.net.preferIPv4Stack=true -cp "$OUT:$JSON_JAR" com.lightos.minimalchat.UpdateDownloadTest
echo "All tool harness tests passed"
