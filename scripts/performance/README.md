# Benchmark Performa Velum

`measure_startup.sh` mengukur waktu startup Activity dan mengambil snapshot memori pada satu perangkat Android yang sehat melalui ADB. Skrip tidak membuat device sintetik dan berhenti dengan status `PERFORMANCE BLOCKED` jika jumlah target ADB bukan tepat satu.

Contoh penggunaan:

```bash
scripts/performance/measure_startup.sh \
  com.rollinkxx.velum.preview \
  com.rollinkxx.velum.MainActivity \
  artifacts/performance \
  5
```

Skrip menghasilkan `summary.tsv`, `summary.txt`, output `am start -W`, dan dump `dumpsys meminfo` per iterasi. Ringkasan startup mencakup rata-rata, minimum, maksimum, median, dan p95. Hasil harus dibandingkan pada model perangkat, API level, ABI, build variant, dan kondisi jaringan yang sama.

Benchmark ini mengukur startup serta memori aplikasi. Benchmark ini **tidak** membuktikan bahwa VPN service berjalan, interface VPN terbentuk, WireGuard handshake terjadi, atau traffic melewati tunnel. Bukti tersebut tetap memerlukan runtime smoke chain pada perangkat yang berwenang.
