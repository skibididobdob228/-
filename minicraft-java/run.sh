#!/usr/bin/env bash
# Launches MiniCraft. On macOS the -XstartOnFirstThread flag is required by GLFW.
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$DIR/target/minicraft.jar"
[ -f "$JAR" ] || { echo "Build first: mvn -f \"$DIR/pom.xml\" package"; exit 1; }
EXTRA=""
if [[ "$(uname)" == "Darwin" ]]; then EXTRA="-XstartOnFirstThread"; fi
exec java $EXTRA -jar "$JAR" "$@"
