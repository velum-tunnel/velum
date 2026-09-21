# Audit Archive

Direktori ini menyimpan laporan audit yang terikat pada commit tertentu. Laporan historis menjadi baseline dan knowledge base; audit berikutnya tetap harus memverifikasi perubahan terhadap HEAD terbaru.

## Current baseline

| Tanggal | Commit yang diaudit | Scope | Temuan utama | Status |
|---|---|---|---|---|
| 2026-09-21 | `45e72840eec5019b5fe0e0a20ed2946a8628929e` | Comprehensive repository audit | `BUG-CI-001`; runtime/device testing gap; residual network and lifecycle risks | `BUG-CI-001` diperbaiki secara lokal; verifikasi GitHub Actions dan device masih pending |

## Laporan dan ledger

- [Comprehensive audit 2026-09-21](2026-09-21-comprehensive-audit.md)
- [Audit findings ledger](findings.md)

## Delta-audit workflow

Agen berikutnya harus membaca `AGENTS.md`, indeks ini, laporan terbaru, dan ledger temuan. Setelah itu, inspeksi perubahan sejak commit audit terakhir dan fokuskan pekerjaan pada temuan terbuka, remediation yang belum diverifikasi, serta area yang tersentuh diff baru. Audit penuh ulang hanya diperlukan bila perubahan baru memengaruhi area yang luas atau baseline tidak lagi dapat dipercaya.

## Aturan pemeliharaan

Setiap laporan harus mencatat branch, commit, tanggal, environment, scope, evidence yang benar-benar dijalankan, confirmed findings, potential findings, blocked/not-verified areas, residual risks, dan verification matrix. Jangan mengubah laporan historis untuk menghapus temuan. Jika remediasi dilakukan setelah audit, catat commit perbaikannya dan hasil verifikasi sebagai status terpisah di ledger.
