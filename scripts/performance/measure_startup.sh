#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 <package> <activity> <output-dir> [iterations]" >&2
  echo "Example: $0 com.rollinkxx.velum.preview com.rollinkxx.velum.MainActivity artifacts/performance 5" >&2
  exit 2
}

[[ $# -ge 3 && $# -le 4 ]] || usage
PACKAGE="$1"
ACTIVITY="$2"
OUT_DIR="$3"
ITERATIONS="${4:-5}"
ADB_BIN="${ADB_BIN:-adb}"

[[ "$ITERATIONS" =~ ^[1-9][0-9]*$ ]] || { echo "iterations harus bilangan positif" >&2; exit 2; }
if ! command -v "$ADB_BIN" >/dev/null 2>&1; then
  if [[ -x "${ANDROID_HOME:-}/platform-tools/adb" ]]; then
    ADB_BIN="${ANDROID_HOME}/platform-tools/adb"
  elif [[ -x "$HOME/.android/sdk/platform-tools/adb" ]]; then
    ADB_BIN="$HOME/.android/sdk/platform-tools/adb"
  else
    echo "ADB tidak ditemukan" >&2
    exit 3
  fi
fi

mapfile -t DEVICES < <("$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ "${#DEVICES[@]}" -ne 1 ]]; then
  echo "PERFORMANCE BLOCKED: membutuhkan tepat satu ADB device sehat; ditemukan ${#DEVICES[@]}" >&2
  "$ADB_BIN" devices -l >&2 || true
  exit 4
fi
SERIAL="${DEVICES[0]}"
ADB=("$ADB_BIN" -s "$SERIAL")
mkdir -p "$OUT_DIR"
SUMMARY="$OUT_DIR/summary.tsv"
printf 'iteration\tstartup_ms\tmem_pss_kb\tmem_private_dirty_kb\n' > "$SUMMARY"

for ((i=1; i<=ITERATIONS; i++)); do
  raw="$OUT_DIR/startup-$i.txt"
  mem="$OUT_DIR/meminfo-$i.txt"
  "${ADB[@]}" shell am force-stop "$PACKAGE"
  "${ADB[@]}" shell am start -W -n "$PACKAGE/$ACTIVITY" > "$raw"
  "${ADB[@]}" shell dumpsys meminfo "$PACKAGE" > "$mem"

  startup_ms="$(sed -n 's/^TotalTime: *\([0-9][0-9]*\)$/\1/p' "$raw" | head -1)"
  pss_kb="$(sed -n 's/^TOTAL *\([0-9][0-9]*\).*/\1/p' "$mem" | head -1)"
  private_kb="$(sed -n 's/^ *TOTAL: *\([0-9][0-9]*\).*/\1/p' "$mem" | head -1)"
  [[ "$startup_ms" =~ ^[0-9]+$ ]] || { echo "startup TotalTime tidak ditemukan pada iterasi $i" >&2; exit 5; }
  [[ "$pss_kb" =~ ^[0-9]+$ ]] || pss_kb=NA
  [[ "$private_kb" =~ ^[0-9]+$ ]] || private_kb=NA
  printf '%s\t%s\t%s\t%s\n' "$i" "$startup_ms" "$pss_kb" "$private_kb" >> "$SUMMARY"
done

awk -F '\t' '
  NR == 1 { next }
  { sum += $2; count++; if (min == "" || $2 < min) min=$2; if ($2 > max) max=$2 }
  END { if (count > 0) printf "iterations=%d\naverage_startup_ms=%.1f\nmin_startup_ms=%d\nmax_startup_ms=%d\n", count, sum/count, min, max; else exit 1 }
' "$SUMMARY" > "$OUT_DIR/summary.txt"
mapfile -t STARTUP_VALUES < <(tail -n +2 "$SUMMARY" | cut -f2 | sort -n)
count="${#STARTUP_VALUES[@]}"
median_index=$(( (count - 1) / 2 ))
p95_index=$(( (95 * count + 99) / 100 - 1 ))
(( p95_index < count )) || p95_index=$((count - 1))
{
  echo "median_startup_ms=${STARTUP_VALUES[$median_index]}"
  echo "p95_startup_ms=${STARTUP_VALUES[$p95_index]}"
} >> "$OUT_DIR/summary.txt"

cat "$OUT_DIR/summary.txt"
echo "PERFORMANCE PASS: startup samples captured on $SERIAL"
