#!/usr/bin/env bash
# Oracle: apply the reference implementation to /app.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd /app
git apply --whitespace=nowarn "$HERE/solution.patch"
# Sanity check: the main sources still compile offline.
mvn -o $MVN_FLAGS -q -pl plugin compile
