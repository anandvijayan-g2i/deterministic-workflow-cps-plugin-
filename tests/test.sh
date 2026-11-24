#!/usr/bin/env bash
# Harbor verifier: installs the hidden tests into /app, runs them offline and writes
# 1 to /logs/verifier/reward.txt only if every FAIL_TO_PASS and PASS_TO_PASS test passes.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOGS=/logs/verifier
mkdir -p "$LOGS"
echo 0 > "$LOGS/reward.txt"
echo '''{"reward": 0}''' > "$LOGS/reward.json"

FAIL_TO_PASS=(DeterministicReplayRecordTest DeterministicReplayVerifyTest DeterminismDriftTest)
PASS_TO_PASS=(CpsStepContextTest ParallelStepTest CpsBodyExecutionTest)
EXPECTED=("${FAIL_TO_PASS[@]}" "${PASS_TO_PASS[@]}")

cd /app
# Hidden tests overwrite any same-named files the agent may have written.
DEST=plugin/src/test/java/org/jenkinsci/plugins/workflow/cps/determinism
mkdir -p "$DEST"
cp -f "$HERE"/src/determinism/*.java "$DEST"/
rm -rf plugin/target/surefire-reports

SELECTOR="$(IFS=,; echo "${EXPECTED[*]}")"
mvn -o $MVN_FLAGS -pl plugin test \
    -Dtest="$SELECTOR" -Dsurefire.failIfNoSpecifiedTests=false -Dsurefire.rerunFailingTestsCount=0 \
    2>&1 | tee "$LOGS/mvn.log"
mvn_status=${PIPESTATUS[0]}

# One line per test case: PASSED/FAILED/SKIPPED class#method
: > "$LOGS/results.txt"
for f in plugin/target/surefire-reports/TEST-*.xml; do
  [ -f "$f" ] || continue
  awk 'BEGIN{RS="<testcase "} NR>1 {
         match($0,/classname="[^"]*"/); c=substr($0,RSTART+11,RLENGTH-12);
         match($0,/(^| )name="[^"]*"/); n=substr($0,RSTART,RLENGTH); sub(/^ ?name="/,"",n); sub(/"$/,"",n);
         st="PASSED"; if ($0 ~ /<(failure|error)[ >\/]/) st="FAILED"; else if ($0 ~ /<skipped/) st="SKIPPED";
         print st, c "#" n }' "$f" >> "$LOGS/results.txt"
done
echo "===== results ====="; cat "$LOGS/results.txt"

ok=1
[ "$mvn_status" -eq 0 ] || ok=0
for cls in "${EXPECTED[@]}"; do
  if ! grep -q "^PASSED [^#]*\.${cls}#" "$LOGS/results.txt"; then
    echo "MISSING or not passing: $cls"; ok=0
  fi
done
if grep -q "^FAILED " "$LOGS/results.txt"; then ok=0; fi

echo "$ok" > "$LOGS/reward.txt"
echo "{\"reward\": $ok}" > "$LOGS/reward.json"
echo "reward=$ok"
[ "$ok" -eq 1 ]
