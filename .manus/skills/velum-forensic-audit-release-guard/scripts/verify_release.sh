#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

run_task() {
  local task="$1"
  printf '\n== ./gradlew %s ==\n' "$task"
  ./gradlew "$task" --no-daemon --stacktrace
}

run_task testDebugUnitTest
run_task assembleDebug
run_task assemblePreview
run_task assembleRelease
run_task lintDebug

printf '\n== git diff --check ==\n'
git diff --check

printf '\n== expected reports and artifacts ==\n'
test -f app/build/reports/tests/testDebugUnitTest/index.html
test -f app/build/reports/lint-results-debug.txt
find app/build/outputs/apk -type f -name '*.apk' -print -quit | grep -q .
printf '%s\n' 'release verification passed'
