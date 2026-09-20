#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MANIFEST="$ROOT_DIR/app/src/main/AndroidManifest.xml"
NETWORK_CONFIG="$ROOT_DIR/app/src/main/res/xml/network_security_config.xml"
JAVA_ROOT="$ROOT_DIR/app/src/main/java"

fail() {
  echo "SECURITY CHECK FAILED: $*" >&2
  exit 1
}

require_literal() {
  local file="$1"
  local pattern="$2"
  local description="$3"
  grep -Fq -- "$pattern" "$file" || fail "$description ($file)"
}

[[ -f "$MANIFEST" ]] || fail "AndroidManifest.xml tidak ditemukan"
[[ -f "$NETWORK_CONFIG" ]] || fail "network_security_config.xml tidak ditemukan"

require_literal "$MANIFEST" 'android:allowBackup="false"' 'backup Android harus dinonaktifkan'
require_literal "$MANIFEST" 'android:permission="android.permission.BIND_VPN_SERVICE"' 'VpnService harus dilindungi permission sistem'
require_literal "$NETWORK_CONFIG" '<base-config cleartextTrafficPermitted="false"' 'cleartext traffic global harus dilarang'
require_literal "$NETWORK_CONFIG" '<domain-config cleartextTrafficPermitted="false"' 'cleartext traffic domain harus dilarang'

if grep -RIn --exclude='VelumLog.kt' --exclude-dir=build -E 'android\.util\.Log\.(d|v|i)\s*\(' "$JAVA_ROOT"; then
  fail 'logging sensitif harus melewati VelumLog, bukan android.util.Log langsung'
fi

if grep -RIn --exclude-dir=build -E 'BEGIN (RSA|EC|OPENSSH|PRIVATE) KEY' "$ROOT_DIR/app" "$ROOT_DIR/scripts"; then
  fail 'material private key ditemukan dalam source atau script'
fi

if grep -RIn --exclude-dir=build -E 'cleartextTrafficPermitted="true"|usesCleartextTraffic="true"' "$ROOT_DIR/app"; then
  fail 'konfigurasi cleartext traffic ditemukan'
fi

echo "SECURITY CHECK PASS"
echo "- Android backup disabled"
echo "- VPN service permission present"
echo "- Cleartext traffic disabled"
echo "- Sensitive logging routed through VelumLog"
echo "- No private-key PEM material in app/scripts"
