#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
EVIDENCE_DIR="${1:-artifacts/phase9}"
HARNESS="$ROOT_DIR/scripts/phase5/runtime.sh"
mkdir -p "$EVIDENCE_DIR"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/.android/sdk}"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/36.0.0:$PATH"

"$ROOT_DIR/scripts/phase9/host_preflight.sh"
"$ROOT_DIR/scripts/phase9/device_preflight.sh" "$EVIDENCE_DIR/device"

cd "$ROOT_DIR"
./gradlew --no-daemon assembleDebug

apk="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*universal-debug.apk' -print -quit)"
[[ -n "$apk" && -f "$apk" ]] || { echo "ACTIVATION: actual universal debug APK not found" >&2; exit 2; }
mkdir -p "$EVIDENCE_DIR/install"
aapt_bin="$(command -v aapt || true)"
if [[ -z "$aapt_bin" ]]; then
  aapt_bin="$(find "$ANDROID_HOME/build-tools" -type f -name aapt -print | sort -V | tail -1)"
fi
[[ -x "$aapt_bin" ]] || { echo "ACTIVATION: aapt not found" >&2; exit 2; }
package="$($aapt_bin dump badging "$apk" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
activity="$($aapt_bin dump badging "$apk" | sed -n "s/^launchable-activity: name='\([^']*\)'.*/\1/p")"
[[ -n "$package" && -n "$activity" ]] || { echo "ACTIVATION: package/activity discovery failed" >&2; exit 2; }
{
  echo "apk=$apk"
  echo "package=$package"
  echo "activity=$activity"
  date -u +%FT%H:%M:%SZ
} > "$EVIDENCE_DIR/install/target.txt"

"$HARNESS" install_apk "$apk" "$package" | tee "$EVIDENCE_DIR/install/install.txt"
"$HARNESS" run_runtime_smoke_test "$package" "$activity" "$EVIDENCE_DIR/smoke"
"$HARNESS" collect_artifacts "$EVIDENCE_DIR/runtime" "$package"
"$HARNESS" cleanup "$package" || true

echo "ACTIVATION: launch smoke completed; VPN functional validation still requires service/interface/fresh-handshake/HTTPS/disconnect evidence"
