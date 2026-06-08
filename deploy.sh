#!/bin/bash
# Build plugin and hot-deploy to local IDEA installation.
# Usage: ./deploy.sh [--restart]
#   --restart   also call restart_idea MCP tool and wait for IDEA to come back
set -e
cd "$(dirname "$0")"

PLUGIN_LIB="$HOME/Library/Application Support/JetBrains/IntelliJIdea2026.1/plugins/breeze-dev-helper/lib"
MCP="http://localhost:19876/mcp"

# 1. Build
echo "Building..."
./gradlew buildPlugin -q

# 2. Get version from build.gradle
VERSION=$(grep "^version = " build.gradle | grep -o "'[^']*'" | tr -d "'")
NEW_JAR="build/libs/breeze-dev-helper-${VERSION}.jar"

# 3. Deploy jar
echo "Deploying ${VERSION}..."
mkdir -p "$PLUGIN_LIB"
rm -f "$PLUGIN_LIB"/*.jar
cp "$NEW_JAR" "$PLUGIN_LIB/"
echo "  -> $PLUGIN_LIB/breeze-dev-helper-${VERSION}.jar"

# 4. Optionally restart IDEA and wait for MCP server
if [[ "$1" == "--restart" ]]; then
    echo "Sending restart_idea..."
    curl -s -X POST "$MCP" \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"restart_idea","arguments":{}}}' \
        2>/dev/null | python3 -c "import sys,json; r=json.load(sys.stdin); print(' ', r['result']['content'][0]['text'])" 2>/dev/null || true

    echo "Waiting for IDEA to come back..."
    for i in $(seq 1 30); do
        sleep 2
        if curl -s --max-time 2 -X POST "$MCP" \
            -H "Content-Type: application/json" \
            -d '{"jsonrpc":"2.0","id":99,"method":"tools/list","params":{}}' \
            2>/dev/null | grep -q '"tools"'; then
            echo "  IDEA is back (${i}x2s elapsed)"
            break
        fi
    done
fi

echo "Done."
