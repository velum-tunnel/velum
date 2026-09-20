#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
aar="$root/app/libs/tunnel-1.0.20260102-velum1.aar"
checksum="$root/third_party/wireguard-tunnel/SHA256SUMS"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

cd "$root"
sha256sum --check "$checksum" >/dev/null
unzip -p "$aar" classes.jar > "$work/classes.jar"
javap -classpath "$work/classes.jar" -p -c \
  'com.wireguard.android.backend.GoBackend$VpnService' > "$work/vpnservice.javap"

grep -q 'android.app.PendingIntent.getActivity' "$work/vpnservice.javap"
grep -q 'android.app.Notification\$Builder.setContentIntent' "$work/vpnservice.javap"
grep -q 'android.content.Intent.setClassName' "$work/vpnservice.javap"
grep -q 'com.rollinkxx.velum.MainActivity' "$work/vpnservice.javap"
# API 34+ receives the typed overload; API 10-33 retains the two-argument fallback.
grep -q 'startForeground:(ILandroid/app/Notification;I)' "$work/vpnservice.javap"
grep -q 'startForeground:(ILandroid/app/Notification;)' "$work/vpnservice.javap"
# The notification must remain immutable and ongoing; reject obvious regressions.
! grep -q 'FLAG_MUTABLE' "$work/vpnservice.javap"
! grep -q 'setContentIntent(null)' "$work/vpnservice.javap"

echo "notification contract: PASS"
