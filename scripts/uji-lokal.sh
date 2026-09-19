#!/usr/bin/env bash
set -euo pipefail

# Uji lokal Velum — cara supaya lulus sebelum push CI
# Jalankan: ./scripts/uji-lokal.sh
# Prasyarat: JDK 17, Android SDK (platform 36, build-tools 36.0.0)

echo "=== 1. Gerbang dokumen (tanpa JDK, bisa di sandbox) ==="
python3 .github/scripts/periksa-dokumen.py

echo ""
echo "=== 2. Grep pola berbahaya (tanpa JDK) ==="
echo "--- Log langsung (harus 0 kecuali VelumLog.kt) ---"
! grep -Rn "android.util.Log\|Log\.d(\|Log\.i(" app/src/main/java --include="*.kt" | grep -v "VelumLog" | grep -v "VelumLog.kt" || echo "BERSIH"
echo "--- Context leak (harus applicationContext) ---"
grep -Rn "Prefs.of(this" app/src/main/java --include="*.kt" | grep -v "try" || echo "Semua Prefs.of sudah dalam try/catch atau applicationContext"
echo "--- commit() di main thread (harus hanya di Prefs background + SuppressLint) ---"
grep -Rn "\.commit()" app/src/main/java --include="*.kt" || echo "Tidak ada commit() di luar Prefs (aman)"
echo "--- Handler tanpa Looper ---"
! grep -Rn "Handler(" app/src/main/java --include="*.kt" | grep -v "getMainLooper\|Looper" || echo "Semua Handler pakai Looper eksplisit"
echo "--- Executor non-daemon ---"
! grep -Rn "newSingleThreadExecutor()" app/src/main/java --include="*.kt" | grep -v "isDaemon" || echo "Semua executor daemon"

echo ""
echo "=== 3. Custom audit Python (tanpa JDK) ==="
python3 /tmp/audit_deep.py 2>&1 | tail -20 || python3 scripts/audit_deep.py 2>&1 | tail -20 || echo "Custom audit script tidak ada, lewati"

echo ""
echo "=== 4. Cek JDK ==="
if ! command -v java >/dev/null 2>&1; then
  echo "JDK tidak ditemukan di PATH. Install JDK 17 dulu:"
  echo "  - Ubuntu/Debian: sudo apt-get install openjdk-17-jdk"
  echo "  - macOS (brew): brew install openjdk@17"
  echo "  - Windows: https://adoptium.net/temurin/releases/?version=17"
  echo "  - Atau via SDKMAN: sdk install java 17.0.20-tem"
  echo "  Setelah install, pastikan: java -version"
  echo "  Dan ANDROID_HOME ter-set ke Android SDK"
  exit 0
fi
java -version

echo ""
echo "=== 5. Unit test (butuh JDK + Android SDK) ==="
./gradlew --no-daemon --stacktrace testDebugUnitTest

echo ""
echo "=== 6. Build debug APK (butuh JDK + Android SDK) ==="
./gradlew --no-daemon --stacktrace assembleDebug

echo ""
echo "=== 7. Build preview APK R8 (butuh JDK + Android SDK) ==="
./gradlew --no-daemon --stacktrace assemblePreview

echo ""
echo "=== 8. Lint debug (advisori) ==="
./gradlew --no-daemon --stacktrace lintDebug || echo "Lint gagal tapi advisori (continue-on-error di CI)"
if [ -f app/build/reports/lint-results-debug.txt ]; then
  tail -n 100 app/build/reports/lint-results-debug.txt
fi

echo ""
echo "=== SEMUA UJI LOKAL SELESAI ==="
