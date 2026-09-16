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
find app/src/main/java -type f -name '*.kt' -print0 |
  xargs -0 awk '
    function clean(line,    start, end, prefix, suffix) {
      # Remove block comments while retaining the source line number.
      while (in_block) {
        end = index(line, "*/")
        if (!end) return ""
        line = substr(line, end + 2)
        in_block = 0
      }
      while ((start = index(line, "/*")) != 0) {
        end = index(substr(line, start + 2), "*/")
        if (!end) {
          line = substr(line, 1, start - 1)
          in_block = 1
          break
        }
        end += start + 1
        prefix = substr(line, 1, start - 1)
        suffix = substr(line, end + 2)
        line = prefix suffix
      }
      sub(/\/\/.*/, "", line)
      return line
    }
    {
      code = clean($0)
      # Report member mutation (`prefs.wasUp = ...`), not reads or named
      # arguments (`wasUp = Prefs.of(...).wasUp`) in data-class constructors.
      if (code ~ /\.[[:space:]]*wasUp[[:space:]]*=/) {
        print FILENAME ":" FNR ":" $0
      }
    }
  ' || true

printf '%s\n' '== handshake/contract call sites =='
rg -n 'VelumConnectionContract\.(connect|reconnect)|awaitHandshake|accepted\(' app/src/main/java app/src/test || true

printf '%s\n' '== recovery ownership call sites =='
rg -n 'tryClaim|release\(|cancelIntent|bumpIntent|markUpIfCurrent|clearUpIfCurrent' app/src/main/java app/src/test || true

printf '%s\n' '== manifest lifecycle hooks =='
rg -n 'BOOT_COMPLETED|MY_PACKAGE_REPLACED|QS_TILE|VpnService' app/src/main/AndroidManifest.xml || true
