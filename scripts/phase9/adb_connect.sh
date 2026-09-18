#!/usr/bin/env bash
set -euo pipefail

mode="${1:-}"
adb_bin="${ADB_BIN:-adb}"
usage() {
  echo "Usage: $0 usb | wireless <device-host:connect-port> [pair-host:pair-port]" >&2
  exit 2
}
command -v "$adb_bin" >/dev/null 2>&1 || { echo "adb not found" >&2; exit 2; }
"$adb_bin" start-server >/dev/null

case "$mode" in
  usb)
    "$adb_bin" devices -l
    ;;
  wireless)
    connect_target="${2:?wireless connect target required}"
    pair_target="${3:-$connect_target}"
    read -r -s -p "Enter the Android Wireless Debugging pairing code (not stored): " pairing_code
    printf '\n'
    [[ -n "$pairing_code" ]] || { echo "pairing code is required" >&2; exit 2; }
    "$adb_bin" pair "$pair_target" "$pairing_code"
    unset pairing_code
    "$adb_bin" connect "$connect_target"
    "$adb_bin" devices -l
    ;;
  *) usage ;;
esac
