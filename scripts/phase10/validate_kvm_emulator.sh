#!/usr/bin/env bash
set -uo pipefail

# Validate host virtualization and Android emulator/device readiness.
# This script never creates fake ADB devices and never reports a simulator as a real device.

MODE="${1:-check}"
OUT_DIR="${2:-artifacts/phase10/host}"
ANDROID_HOME="${ANDROID_HOME:-$HOME/.android/sdk}"
ADB_BIN="${ADB_BIN:-$ANDROID_HOME/platform-tools/adb}"
EMULATOR_BIN="${EMULATOR_BIN:-$ANDROID_HOME/emulator/emulator}"

EXIT_OK=0
EXIT_USAGE=2
EXIT_HOST=3
EXIT_TARGET=4
EXIT_EMULATOR=5

usage() {
  cat <<'USAGE'
Usage:
  validate_kvm_emulator.sh check [output-dir]
  validate_kvm_emulator.sh kvm [output-dir]
  validate_kvm_emulator.sh avd [output-dir]
  validate_kvm_emulator.sh adb [output-dir]
  validate_kvm_emulator.sh boot [output-dir]

Modes:
  check  Run all checks. KVM is reported as a capability, not required for a physical device.
  kvm    Validate /dev/kvm, CPU virtualization flags, and virtualization context.
  avd    List available AVDs and emulator binary status.
  adb    Require exactly one ADB target in state 'device'.
  boot   Require exactly one ADB target with sys.boot_completed=1 and shell health.

Exit codes:
  0  Requested checks passed.
  2  Invalid usage.
  3  Host tooling or host capability failure.
  4  ADB target failure.
  5  Emulator/AVD failure.
USAGE
}

case "$MODE" in
  check|kvm|avd|adb|boot) ;;
  -h|--help) usage; exit "$EXIT_OK" ;;
  *) usage >&2; exit "$EXIT_USAGE" ;;
esac

mkdir -p "$OUT_DIR"
SUMMARY="$OUT_DIR/summary.txt"
: > "$SUMMARY"

pass() {
  printf '[PASS] %s\n' "$*"
  printf 'PASS: %s\n' "$*" >> "$SUMMARY"
}
warn() {
  printf '[WARN] %s\n' "$*" >&2
  printf 'WARN: %s\n' "$*" >> "$SUMMARY"
}
fail() {
  printf '[FAIL] %s\n' "$*" >&2
  printf 'FAIL: %s\n' "$*" >> "$SUMMARY"
}
need_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    fail "required command not found: $1"
    return 1
  }
  return 0
}

export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/build-tools/36.0.0:$PATH"
{
  echo "mode=$MODE"
  echo "timestamp_utc=$(date -u +%FT%H:%M:%SZ)"
  echo "android_home=$ANDROID_HOME"
  echo "kernel=$(uname -sr)"
  echo "architecture=$(uname -m)"
} >> "$SUMMARY"

check_kvm() {
  local virt_context="unknown"
  if command -v systemd-detect-virt >/dev/null 2>&1; then
    virt_context="$(systemd-detect-virt 2>/dev/null || true)"
  fi
  echo "virtualization_context=$virt_context" >> "$SUMMARY"
  printf 'virtualization_context=%s\n' "$virt_context" > "$OUT_DIR/virtualization.txt"

  if [[ -e /dev/kvm ]]; then
    if [[ -r /dev/kvm && -w /dev/kvm ]]; then
      pass "/dev/kvm exists and is readable/writable"
      echo 'kvm_device=available' >> "$SUMMARY"
      ls -l /dev/kvm > "$OUT_DIR/kvm-device.txt" 2>&1 || true
    else
      fail "/dev/kvm exists but is not readable/writable"
      echo 'kvm_device=permission_denied' >> "$SUMMARY"
      return "$EXIT_HOST"
    fi
  else
    warn "/dev/kvm is unavailable; hardware-accelerated emulator cannot be validated here"
    echo 'kvm_device=unavailable' >> "$SUMMARY"
  fi

  if grep -qE '(^|[[:space:]])(vmx|svm)([[:space:]]|$)' /proc/cpuinfo 2>/dev/null; then
    pass "CPU virtualization flag vmx/svm is exposed"
    echo 'cpu_virtualization=exposed' >> "$SUMMARY"
  else
    warn "CPU virtualization flags vmx/svm are not exposed"
    echo 'cpu_virtualization=hidden' >> "$SUMMARY"
  fi

  if grep -q 'CONFIG_VIRTUALIZATION=y' /proc/config.gz 2>/dev/null; then
    pass "guest kernel exposes CONFIG_VIRTUALIZATION"
  elif [[ -r /boot/config-$(uname -r) ]] && grep -q 'CONFIG_VIRTUALIZATION=y' "/boot/config-$(uname -r)"; then
    pass "kernel config exposes CONFIG_VIRTUALIZATION"
  else
    warn "CONFIG_VIRTUALIZATION is unavailable or disabled in the visible kernel config"
  fi

  return "$EXIT_OK"
}

check_avd() {
  [[ -x "$EMULATOR_BIN" ]] || {
    fail "emulator binary not found or not executable: $EMULATOR_BIN"
    return "$EXIT_EMULATOR"
  }
  "$EMULATOR_BIN" -version > "$OUT_DIR/emulator-version.txt" 2>&1 || {
    fail "emulator -version failed"
    return "$EXIT_EMULATOR"
  }
  pass "Android emulator binary is available"

  mapfile -t avds < <("$EMULATOR_BIN" -list-avds 2>/dev/null)
  if [[ "${#avds[@]}" -eq 0 ]]; then
    warn "no AVDs are registered"
    : > "$OUT_DIR/avds.txt"
  else
    printf '%s\n' "${avds[@]}" | tee "$OUT_DIR/avds.txt"
    pass "registered AVD count: ${#avds[@]}"
  fi

  if [[ ! -e /dev/kvm ]]; then
    warn "AVD execution will require software emulation; this is not equivalent to hardware-accelerated runtime"
  fi
  return "$EXIT_OK"
}

start_adb() {
  [[ -x "$ADB_BIN" || -n "$(command -v adb 2>/dev/null || true)" ]] || {
    fail "adb binary not found: $ADB_BIN"
    return "$EXIT_HOST"
  }
  "$ADB_BIN" start-server >/dev/null 2>&1 || {
    fail "adb server failed to start"
    return "$EXIT_HOST"
  }
  "$ADB_BIN" version > "$OUT_DIR/adb-version.txt" 2>&1 || true
  return "$EXIT_OK"
}

check_adb() {
  start_adb || return $?
  "$ADB_BIN" devices -l | tee "$OUT_DIR/adb-devices.txt"
  mapfile -t targets < <("$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  if [[ "${#targets[@]}" -ne 1 ]]; then
    fail "expected exactly one authorized ADB target in state device; found ${#targets[@]}"
    return "$EXIT_TARGET"
  fi
  echo "serial=${targets[0]}" >> "$SUMMARY"
  if [[ "${targets[0]}" == emulator-* ]]; then
    pass "exactly one emulator ADB target: ${targets[0]}"
  else
    pass "exactly one ADB target: ${targets[0]}"
  fi
  return "$EXIT_OK"
}

check_boot() {
  check_adb || return $?
  local serial boot shell_probe package_manager
  serial="$(awk -F= '/^serial=/{print $2; exit}' "$SUMMARY")"
  boot="$($ADB_BIN -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  echo "boot_completed=$boot" >> "$SUMMARY"
  [[ "$boot" == "1" ]] || {
    fail "target has not completed boot: $boot"
    return "$EXIT_TARGET"
  }
  shell_probe="$($ADB_BIN -s "$serial" shell echo ANDROID_RUNTIME_PREFLIGHT_READY 2>/dev/null | tr -d '\r')"
  [[ "$shell_probe" == "ANDROID_RUNTIME_PREFLIGHT_READY" ]] || {
    fail "ADB shell probe failed"
    return "$EXIT_TARGET"
  }
  package_manager="$($ADB_BIN -s "$serial" shell pm path android 2>/dev/null || true)"
  [[ -n "$package_manager" ]] || {
    fail "package manager probe failed"
    return "$EXIT_TARGET"
  }
  {
    echo "manufacturer=$($ADB_BIN -s "$serial" shell getprop ro.product.manufacturer | tr -d '\r')"
    echo "model=$($ADB_BIN -s "$serial" shell getprop ro.product.model | tr -d '\r')"
    echo "android=$($ADB_BIN -s "$serial" shell getprop ro.build.version.release | tr -d '\r')"
    echo "api=$($ADB_BIN -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')"
    echo "abi=$($ADB_BIN -s "$serial" shell getprop ro.product.cpu.abi | tr -d '\r')"
  } | tee "$OUT_DIR/device-info.txt" >> "$SUMMARY"
  pass "target boot, shell, and package-manager checks passed"
  return "$EXIT_OK"
}

case "$MODE" in
  kvm)
    check_kvm
    rc=$?
    ;;
  avd)
    check_kvm || true
    check_avd
    rc=$?
    ;;
  adb)
    check_adb
    rc=$?
    ;;
  boot)
    check_boot
    rc=$?
    ;;
  check)
    rc=0
    check_kvm
    result=$?
    [[ "$result" -eq 0 || "$rc" -ne 0 ]] || rc="$result"
    check_avd
    result=$?
    [[ "$result" -eq 0 || "$rc" -ne 0 ]] || rc="$result"
    check_boot
    result=$?
    [[ "$result" -eq 0 || "$rc" -ne 0 ]] || rc="$result"
    ;;
esac

printf '\nEvidence directory: %s\n' "$OUT_DIR"
printf 'Summary: %s\n' "$SUMMARY"
exit "$rc"
