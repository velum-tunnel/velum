#!/usr/bin/env bash
set -euo pipefail

ADB_BIN="${ADB_BIN:-adb}"
out="${1:-artifacts/phase9/device}"
fail() { echo "DEVICE PREFLIGHT: BLOCKED — $*" >&2; exit 2; }

if [[ -n "${ANDROID_HOME:-}" && -d "$ANDROID_HOME/platform-tools" && "$ADB_BIN" == "adb" ]]; then
  export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/36.0.0:$PATH"
fi

command -v "$ADB_BIN" >/dev/null 2>&1 || fail "adb not found"
"$ADB_BIN" start-server >/dev/null
mapfile -t devices < <("$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
[[ "${#devices[@]}" -eq 1 ]] || {
  echo "Expected exactly one ADB device; found ${#devices[@]}" >&2
  "$ADB_BIN" devices -l >&2 || true
  exit 2
}
serial="${devices[0]}"
mkdir -p "$out"

state="$($ADB_BIN -s "$serial" get-state | tr -d '\r')"
[[ "$state" == "device" ]] || fail "ADB state is $state"
boot="$($ADB_BIN -s "$serial" shell getprop sys.boot_completed | tr -d '\r')"
[[ "$boot" == "1" ]] || fail "device boot is not complete"
probe="$($ADB_BIN -s "$serial" shell echo VELUM_PHASE9_DEVICE_READY | tr -d '\r')"
[[ "$probe" == "VELUM_PHASE9_DEVICE_READY" ]] || fail "shell probe failed"
"$ADB_BIN" -s "$serial" shell pm path android >/dev/null || fail "package manager probe failed"
"$ADB_BIN" -s "$serial" shell dumpsys connectivity >/dev/null || fail "connectivity probe failed"

{
  echo "serial=$serial"
  echo "manufacturer=$($ADB_BIN -s "$serial" shell getprop ro.product.manufacturer | tr -d '\r')"
  echo "model=$($ADB_BIN -s "$serial" shell getprop ro.product.model | tr -d '\r')"
  echo "android=$($ADB_BIN -s "$serial" shell getprop ro.build.version.release | tr -d '\r')"
  echo "api=$($ADB_BIN -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "abi=$($ADB_BIN -s "$serial" shell getprop ro.product.cpu.abi | tr -d '\r')"
  echo "fingerprint=$($ADB_BIN -s "$serial" shell getprop ro.build.fingerprint | tr -d '\r')"
} > "$out/device-info.txt"
"$ADB_BIN" devices -l > "$out/adb-state.txt"
"$ADB_BIN" -s "$serial" shell settings get global airplane_mode_on > "$out/airplane-mode.txt"

echo "DEVICE PREFLIGHT: PASS ($serial)"
