#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  runtime.sh preflight <output-dir>
  runtime.sh detect_device
  runtime.sh verify_device
  runtime.sh build_apk [debug|preview]
  runtime.sh install_apk <apk> <package>
  runtime.sh uninstall_apk <package>
  runtime.sh launch_app <package> <activity>
  runtime.sh capture_logcat <output-dir>
  runtime.sh capture_package_state <package> <output-dir>
  runtime.sh capture_vpn_state <output-dir>
  runtime.sh capture_network_state <output-dir>
  runtime.sh run_runtime_smoke_test <package> <activity> <output-dir>
  runtime.sh collect_artifacts <output-dir> <package>
  runtime.sh cleanup <package>
USAGE
}

ADB_BIN="${ADB_BIN:-adb}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

require_adb() {
  command -v "$ADB_BIN" >/dev/null 2>&1 || {
    echo "ERROR: adb is not available: $ADB_BIN" >&2
    return 2
  }
  "$ADB_BIN" start-server >/dev/null
}

require_device() {
  require_adb
  mapfile -t devices < <("$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  if [[ "${#devices[@]}" -ne 1 ]]; then
    echo "ERROR: expected exactly one authorized ADB device in state 'device'; found ${#devices[@]}" >&2
    "$ADB_BIN" devices -l >&2 || true
    return 2
  fi
  export ANDROID_SERIAL="${devices[0]}"
}

capture_device_identity() {
  local out="${1:?output file required}"
  mkdir -p "$(dirname "$out")"
  {
    echo "serial=$ANDROID_SERIAL"
    echo "manufacturer=$($ADB_BIN -s "$ANDROID_SERIAL" shell getprop ro.product.manufacturer | tr -d '\r')"
    echo "model=$($ADB_BIN -s "$ANDROID_SERIAL" shell getprop ro.product.model | tr -d '\r')"
    echo "android=$($ADB_BIN -s "$ANDROID_SERIAL" shell getprop ro.build.version.release | tr -d '\r')"
    echo "api=$($ADB_BIN -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
    echo "abi=$($ADB_BIN -s "$ANDROID_SERIAL" shell getprop ro.product.cpu.abi | tr -d '\r')"
    echo "fingerprint=$($ADB_BIN -s "$ANDROID_SERIAL" shell getprop ro.build.fingerprint | tr -d '\r')"
  } > "$out"
}

preflight() {
  local out="${1:?output directory required}"
  require_device
  [[ "$($ADB_BIN -s "$ANDROID_SERIAL" get-state | tr -d '\r')" == "device" ]] || {
    echo "ERROR: ADB target is not in state device" >&2
    return 2
  }
  [[ "$($ADB_BIN -s "$ANDROID_SERIAL" shell getprop sys.boot_completed | tr -d '\r')" == "1" ]] || {
    echo "ERROR: Android target has not completed boot" >&2
    return 2
  }
  [[ "$($ADB_BIN -s "$ANDROID_SERIAL" shell echo VELUM_RUNTIME_PREFLIGHT | tr -d '\r')" == "VELUM_RUNTIME_PREFLIGHT" ]] || {
    echo "ERROR: adb shell probe failed" >&2
    return 2
  }
  "$ADB_BIN" -s "$ANDROID_SERIAL" shell pm path android >/dev/null || {
    echo "ERROR: package manager probe failed" >&2
    return 2
  }
  "$ADB_BIN" -s "$ANDROID_SERIAL" shell dumpsys connectivity >/dev/null || {
    echo "ERROR: connectivity probe failed" >&2
    return 2
  }
  mkdir -p "$out"
  "$ADB_BIN" devices -l > "$out/adb-state.txt"
  capture_device_identity "$out/device-info.txt"
  echo "DEVICE PREFLIGHT: PASS ($ANDROID_SERIAL)"
}

redact_log() {
  sed -E \
    -e 's/(Authorization: Bearer )[A-Za-z0-9._~+\/-]+/\1[REDACTED]/g' \
    -e 's/(private[_ -]?key|registration[_ -]?token|access[_ -]?token)[=:][^ ]+/\1=[REDACTED]/gi' \
    -e 's/[A-Za-z0-9+\/_-]{80,}/[REDACTED_LONG_VALUE]/g'
}

case "${1:-}" in
  preflight)
    preflight "${2:?output directory required}"
    ;;
  detect_device)
    require_device
    echo "ADB device: $ANDROID_SERIAL"
    ;;
  verify_device)
    require_device
    [[ "$($ADB_BIN -s "$ANDROID_SERIAL" get-state)" == "device" ]]
    [[ "$($ADB_BIN -s "$ANDROID_SERIAL" shell getprop sys.boot_completed | tr -d '\r')" == "1" ]]
    [[ "$($ADB_BIN -s "$ANDROID_SERIAL" shell settings get global adb_enabled | tr -d '\r')" == "1" ]]
    probe="phase5-$(date +%s)"
    "$ADB_BIN" -s "$ANDROID_SERIAL" shell "echo '$probe' > /data/local/tmp/velum_phase5_probe"
    [[ "$($ADB_BIN -s "$ANDROID_SERIAL" shell cat /data/local/tmp/velum_phase5_probe | tr -d '\r')" == "$probe" ]]
    "$ADB_BIN" -s "$ANDROID_SERIAL" shell rm -f /data/local/tmp/velum_phase5_probe
    echo "DEVICE HEALTH: PASS ($ANDROID_SERIAL)"
    ;;
  build_apk)
    variant="${2:-debug}"
    case "$variant" in debug) task=assembleDebug;; preview) task=assemblePreview;; *) echo "unknown variant: $variant" >&2; exit 2;; esac
    (cd "$ROOT_DIR" && ./gradlew --no-daemon "$task")
    find "$ROOT_DIR/app/build/outputs/apk" -type f -name "*-${variant}.apk" -print
    ;;
  install_apk)
    require_device
    apk="${2:?APK path required}"; package="${3:?package required}"
    [[ -f "$apk" ]] || { echo "APK not found: $apk" >&2; exit 2; }
    "$ADB_BIN" -s "$ANDROID_SERIAL" install -r "$apk"
    "$ADB_BIN" -s "$ANDROID_SERIAL" shell pm path "$package" >/dev/null
    echo "INSTALLED: $package"
    ;;
  uninstall_apk)
    require_device
    "$ADB_BIN" -s "$ANDROID_SERIAL" uninstall "${2:?package required}"
    ;;
  launch_app)
    require_device
    "$ADB_BIN" -s "$ANDROID_SERIAL" shell am force-stop "${2:?package required}"
    "$ADB_BIN" -s "$ANDROID_SERIAL" shell am start -W -n "${2}/${3:?activity required}"
    ;;
  capture_logcat)
    require_device
    out="${2:?output directory required}"; mkdir -p "$out"
    "$ADB_BIN" -s "$ANDROID_SERIAL" logcat -c
    "$ADB_BIN" -s "$ANDROID_SERIAL" logcat -d -v threadtime | redact_log > "$out/logcat.txt"
    ;;
  capture_package_state)
    require_device
    package="${2:?package required}"; out="${3:?output directory required}"; mkdir -p "$out"
    "$ADB_BIN" -s "$ANDROID_SERIAL" shell dumpsys package "$package" | redact_log > "$out/package-state.txt"
    ;;
  capture_vpn_state)
    require_device
    out="${2:?output directory required}"; mkdir -p "$out"
    "$ADB_BIN" -s "$ANDROID_SERIAL" shell dumpsys connectivity | redact_log > "$out/connectivity.txt"
    "$ADB_BIN" -s "$ANDROID_SERIAL" shell ip addr | redact_log > "$out/ip-addr.txt"
    "$ADB_BIN" -s "$ANDROID_SERIAL" shell ip route | redact_log > "$out/ip-route.txt"
    "$ADB_BIN" -s "$ANDROID_SERIAL" shell dumpsys activity services | redact_log > "$out/services.txt"
    ;;
  capture_network_state)
    require_device
    out="${2:?output directory required}"; mkdir -p "$out"
    "$ADB_BIN" -s "$ANDROID_SERIAL" shell dumpsys connectivity | redact_log > "$out/network-connectivity.txt"
    "$ADB_BIN" -s "$ANDROID_SERIAL" shell settings get global airplane_mode_on > "$out/airplane-mode.txt"
    ;;
  run_runtime_smoke_test)
    require_device
    package="${2:?package required}"; activity="${3:?activity required}"; out="${4:?output directory required}"; mkdir -p "$out"
    "$ADB_BIN" -s "$ANDROID_SERIAL" logcat -c
    "$ADB_BIN" -s "$ANDROID_SERIAL" shell am force-stop "$package"
    "$ADB_BIN" -s "$ANDROID_SERIAL" shell am start -W -n "$package/$activity" > "$out/launch.txt"
    "$ADB_BIN" -s "$ANDROID_SERIAL" shell dumpsys package "$package" | redact_log > "$out/package-state.txt"
    "$ADB_BIN" -s "$ANDROID_SERIAL" logcat -d -v threadtime | redact_log > "$out/logcat.txt"
    if grep -Eiq 'FATAL EXCEPTION|AndroidRuntime|ANR in|SecurityException|UnsatisfiedLinkError' "$out/logcat.txt"; then
      echo "SMOKE TEST: FAIL (see $out/logcat.txt)" >&2
      exit 1
    fi
    echo "SMOKE TEST: PASS (launch/log scan only; not VPN validation)"
    ;;
  collect_artifacts)
    out="${2:?output directory required}"; package="${3:?package required}"; mkdir -p "$out"
    "$0" capture_logcat "$out"
    "$0" capture_package_state "$package" "$out"
    "$0" capture_vpn_state "$out"
    "$0" capture_network_state "$out"
    ;;
  cleanup)
    require_device
    "$ADB_BIN" -s "$ANDROID_SERIAL" shell am force-stop "${2:?package required}" || true
    "$ADB_BIN" -s "$ANDROID_SERIAL" shell rm -f /data/local/tmp/velum_phase5_probe /data/local/tmp/velum_phase7_probe || true
    echo "DEVICE CLEANUP: completed without uninstalling packages or deleting user data"
    ;;
  *) usage; exit 2;;
esac
