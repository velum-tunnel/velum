#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

printf '%s\n' '== direct tunnel mutations outside the connection contract =='
rg -n 'VelumTunnel\.(up|restart)\(' app/src/main/java \
  | grep -v 'VelumConnectionContract.kt' \
  | grep -vE '^.*//|^.*`VelumTunnel\.(up|restart)' \
  || true

printf '%s\n' '== direct wasUp writes =='
rg -n '(^|[^[:alnum:]_])([A-Za-z0-9_]+\.)?wasUp[[:space:]]*=' app/src/main/java || true

printf '%s\n' '== handshake/contract call sites =='
rg -n 'VelumConnectionContract\.(connect|reconnect)|awaitHandshake|accepted\(' app/src/main/java app/src/test || true

printf '%s\n' '== recovery ownership call sites =='
rg -n 'tryClaim|release\(|cancelIntent|bumpIntent|markUpIfCurrent|clearUpIfCurrent' app/src/main/java app/src/test || true

printf '%s\n' '== manifest lifecycle hooks =='
rg -n 'BOOT_COMPLETED|MY_PACKAGE_REPLACED|QS_TILE|VpnService' app/src/main/AndroidManifest.xml || true
