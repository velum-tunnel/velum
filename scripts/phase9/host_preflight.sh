#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fail() { echo "HOST PREFLIGHT: BLOCKED — $*" >&2; exit 2; }

if [[ -n "${ANDROID_HOME:-}" && -d "$ANDROID_HOME/platform-tools" ]]; then
  export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/36.0.0:$PATH"
fi

command -v adb >/dev/null 2>&1 || fail "adb not found"
command -v java >/dev/null 2>&1 || fail "java not found"
command -v git >/dev/null 2>&1 || fail "git not found"
[[ -d "$ROOT_DIR/.git" ]] || fail "repository checkout not found at $ROOT_DIR"

adb version | head -5
java -version 2>&1 | head -3
git --version

if [[ -n "${ANDROID_HOME:-}" ]]; then
  [[ -x "$ANDROID_HOME/platform-tools/adb" ]] || fail "ANDROID_HOME platform-tools/adb is not executable"
fi
if command -v aapt >/dev/null 2>&1; then
  aapt version 2>&1 | head -2
else
  echo "WARNING: aapt not found; configure Android build-tools on the host" >&2
fi

if command -v curl >/dev/null 2>&1; then
  curl --fail --silent --show-error --max-time 10 -o /dev/null https://github.com/ || fail "GitHub connectivity check failed"
else
  echo "WARNING: curl not found; GitHub connectivity not checked" >&2
fi

echo "HOST PREFLIGHT: PASS"
