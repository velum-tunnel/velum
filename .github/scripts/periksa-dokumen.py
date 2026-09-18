#!/usr/bin/env python3
"""Gerbang konsistensi dokumen — menjalankan mekanis aturan yang tadinya hanya prosa.

Alasan berkas ini ada (2026-09-13): aturan di kebijakan proyek bergantung pada agen **ingat**
menjalankannya, dan buktinya tidak andal — pada hari aturan itu ditulis, agen yang
menulisnya melanggar Gerbang 0 (§3) dan mengarang angka versi di dokumen (TODO 95, 96).
CI adalah satu-satunya penegakan yang selamat dari daur ulang sandbox, jadi pemeriksaan
yang bisa dimekaniskan dipindah ke sini.

Empat pemeriksaan, masing-masing lahir dari kesalahan nyata:

1. Angka versi di `docs/` harus sama dengan `versionName` di `app/build.gradle.kts`.
   (TODO 95: contoh keluaran diagnostik menulis "Velum 0.5.0" padahal versionName 0.1.0.)
2. Tidak ada karakter dari aksara yang tidak dipakai repo ini (CJK, Kana, Hangul,
   Cyrillic, Yunani, Ibrani, Arab, Thai, Devanagari).
   (TODO 80: "di磁盘" menyusup ke komentar `Prefs.open()` dan lolos semua gerbang lain.)
3. Tabel `TODO.md` utuh: jumlah kolom seragam, nomor item naik, tanpa duplikat.
   (Item 84/85 pernah tersisip sebelum 82 saat penyuntingan.)
4. Setiap `docs/adr/NNN-*.md` terdaftar di `docs/adr/README.md`.
   (Pencegahan: keputusan arsitektur yang "hilang" dari indeks tidak akan ditemukan lagi.)

Dijalankan lokal maupun di CI:

    python3 .github/scripts/periksa-dokumen.py

Keluar 0 bila bersih, 1 bila ada pelanggaran. Kegagalan juga dipancarkan sebagai anotasi
`::error file=...,line=...` supaya terbaca dari halaman run tanpa mengunduh log — log CI
tidak bisa diunduh dari sandbox agen (batasan artifact CI sandbox), jadi anotasi satu-satunya jalan.
"""
import glob
import os
import re
import sys

# Batas direktori yang diperiksa untuk karakter asing. `app/src/main/res/values*` SENGAJA
# di luar: di situlah terjemahan hidup, dan terjemahan yang sah memang memakai aksara lain.
CAKUPAN_AKSARA = (
    "app/src/main/java/**/*.kt",
    "app/src/test/**/*.kt",
    "app/build.gradle.kts",
    "build.gradle.kts",
    "settings.gradle.kts",
    "docs/**/*.md",
    "*.md",
)

AKSARA_TERLARANG = (
    (0x3000, 0x303F, "tanda baca CJK"),
    (0x3040, 0x30FF, "Kana"),
    (0x4E00, 0x9FFF, "Han/CJK"),
    (0xAC00, 0xD7AF, "Hangul"),
    (0x0400, 0x04FF, "Cyrillic"),
    (0x0370, 0x03FF, "Yunani"),
    (0x0590, 0x05FF, "Ibrani"),
    (0x0600, 0x06FF, "Arab"),
    (0x0900, 0x097F, "Devanagari"),
    (0x0E00, 0x0E7F, "Thai"),
)

# Dokumen yang boleh menyebut versi lama/salah: keduanya adalah buku besar riwayat yang
# justru mengutip nilai keliru ketika mencatat koreksinya. Menuduh keduanya berarti
# menghukum dokumen yang sedang jujur.
PENGECUALIAN_VERSI = ("TODO.md", "CHANGELOG.md")


def error(jalur: str, baris: int, pesan: str) -> None:
    """Pancarkan kegagalan sebagai anotasi GitHub sekaligus ke stdout."""
    teks = pesan.replace("%", "%25").replace("\r", " ").replace("\n", " ")
    print(f"::error file={jalur},line={baris}::{teks}")
    print(f"  SALAH {jalur}:{baris}: {pesan}")


def baca(jalur: str) -> str:
    with open(jalur, encoding="utf-8") as berkas:
        return berkas.read()


def periksa_versi_dokumen() -> int:
    """1. Angka `Velum X.Y.Z` di docs/ harus sama dengan versionName build."""
    if not os.path.exists("app/build.gradle.kts"):
        print("  lewati: app/build.gradle.kts tidak ada")
        return 0
    sumber = baca("app/build.gradle.kts")
    cocok = re.search(r'versionName\s*=\s*"([^"]+)"', sumber)
    if not cocok:
        print("  lewati: versionName tidak ditemukan di app/build.gradle.kts")
        return 0
    versi = cocok.group(1)
    pola = re.compile(r"Velum\s+(\d+\.\d+(?:\.\d+)?)")
    salah = 0
    for jalur in sorted(glob.glob("docs/**/*.md", recursive=True)):
        if os.path.basename(jalur) in PENGECUALIAN_VERSI:
            continue
        for nomor, baris in enumerate(baca(jalur).splitlines(), 1):
            for temu in pola.finditer(baris):
                if temu.group(1) != versi:
                    salah += 1
                    error(
                        jalur,
                        nomor,
                        f"versi '{temu.group(1)}' != versionName '{versi}' di "
                        f"app/build.gradle.kts (perbaiki dokumennya, atau bump versi "
                        f"atas permintaan maintainer — §4)",
                    )
    print(f"  versi dokumen: versionName={versi}, pelanggaran={salah}")
    return salah


def periksa_aksara() -> int:
    """2. Tidak ada aksara di luar yang dipakai repo ini."""
    salah = 0
    diperiksa = 0
    for pola in CAKUPAN_AKSARA:
        for jalur in sorted(glob.glob(pola, recursive=True)):
            if not os.path.isfile(jalur):
                continue
            diperiksa += 1
            for nomor, baris in enumerate(baca(jalur).splitlines(), 1):
                for ch in baris:
                    for bawah, atas, nama in AKSARA_TERLARANG:
                        if bawah <= ord(ch) <= atas:
                            salah += 1
                            error(
                                jalur,
                                nomor,
                                f"karakter {nama} U+{ord(ch):04X} menyusup ke teks; "
                                f"repo ini hanya memakai Latin + tanda baca tipografis",
                            )
    print(f"  aksara: {diperiksa} berkas diperiksa, pelanggaran={salah}")
    return salah


def periksa_tabel_todo() -> int:
    """3. Tabel TODO.md: kolom seragam, nomor naik, tanpa duplikat."""
    if not os.path.exists("TODO.md"):
        print("  lewati: TODO.md tidak ada")
        return 0
    salah = 0
    nomor_item = []
    for nomor, baris in enumerate(baca("TODO.md").splitlines(), 1):
        if not baris.startswith("|"):
            continue
        # Tabel repo ini: No. | Item | Prioritas | Status  ->  4 kolom  ->  5 pipa.
        if baris.count("|") != 5:
            salah += 1
            error(
                "TODO.md",
                nomor,
                f"baris tabel punya {baris.count('|')} pipa, seharusnya 5 "
                f"(4 kolom: No./Item/Prioritas/Status)",
            )
            continue
        if set(baris.replace("|", "").strip()) <= {"-", " "}:
            continue  # baris pemisah
        cocok = re.match(r"\|\s*(\d+)\s*\|", baris)
        if cocok:
            nomor_item.append((int(cocok.group(1)), nomor))
    for (kini, baris), (lalu, _) in zip(nomor_item[1:], nomor_item):
        if kini <= lalu:
            salah += 1
            error(
                "TODO.md",
                baris,
                f"nomor item {kini} tidak naik dari {lalu}; riwayat tidak dihapus dan "
                f"item baru = baris baru di akhir (§4)",
            )
    duplikat = sorted({n for n, _ in nomor_item if [x for x, _ in nomor_item].count(n) > 1})
    if duplikat:
        salah += 1
        error("TODO.md", 1, f"nomor item duplikat: {duplikat}")
    print(f"  tabel TODO: {len(nomor_item)} item, pelanggaran={salah}")
    return salah


def periksa_indeks_adr() -> int:
    """4. Setiap ADR terdaftar di indeks."""
    if not os.path.isdir("docs/adr"):
        print("  lewati: docs/adr tidak ada")
        return 0
    indeks_path = "docs/adr/README.md"
    indeks = baca(indeks_path) if os.path.exists(indeks_path) else ""
    salah = 0
    jumlah = 0
    for jalur in sorted(glob.glob("docs/adr/*.md")):
        nama = os.path.basename(jalur)
        if nama == "README.md":
            continue
        jumlah += 1
        if nama not in indeks:
            salah += 1
            error(
                indeks_path if os.path.exists(indeks_path) else jalur,
                1,
                f"ADR '{nama}' tidak terdaftar di docs/adr/README.md — keputusan "
                f"arsitektur yang tidak terindeks tidak akan ditemukan orang berikutnya",
            )
    print(f"  indeks ADR: {jumlah} ADR, tidak terindeks={salah}")
    return salah


def utama() -> int:
    print("Gerbang konsistensi dokumen (.github/scripts/periksa-dokumen.py)")
    total = 0
    for periksa in (
        periksa_versi_dokumen,
        periksa_aksara,
        periksa_tabel_todo,
        periksa_indeks_adr,
    ):
        try:
            total += periksa()
        except OSError as e:
            print(f"::error file={__file__},line=1::gagal membaca berkas: {e}")
            total += 1
    if total:
        print(f"\nGAGAL: {total} pelanggaran.")
        print("Perbaiki isinya — jangan melonggarkan pemeriksaannya.")
        return 1
    print("\nBERSIH: keempat pemeriksaan lulus.")
    return 0


if __name__ == "__main__":
    sys.exit(utama())
