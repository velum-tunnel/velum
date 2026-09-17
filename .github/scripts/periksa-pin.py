#!/usr/bin/env python3
"""Fail when the Android network-security pin set is missing or near expiry."""
from datetime import date, timedelta
from pathlib import Path
import re
import sys

path = Path("app/src/main/res/xml/network_security_config.xml")
text = path.read_text(encoding="utf-8")
match = re.search(r'<pin-set\b[^>]*\bexpiration="(\d{4}-\d{2}-\d{2})"', text)
if not match:
    print(f"ERROR: pin-set expiration tidak ditemukan di {path}", file=sys.stderr)
    raise SystemExit(1)
try:
    expiry = date.fromisoformat(match.group(1))
except ValueError as exc:
    print(f"ERROR: tanggal expiry tidak valid: {match.group(1)} ({exc})", file=sys.stderr)
    raise SystemExit(1)

pins = re.findall(r'<pin\b[^>]*>([^<]+)</pin>', text)
if len(pins) < 2:
    print(f"ERROR: pin-set harus memiliki minimal dua pin untuk rotasi: ditemukan {len(pins)}", file=sys.stderr)
    raise SystemExit(1)

warning_window = date.today() + timedelta(days=30)
print(f"pin-set: {len(pins)} pin, expiry={expiry.isoformat()}, today={date.today().isoformat()}")
if expiry <= date.today():
    print("ERROR: certificate pin-set sudah kedaluwarsa", file=sys.stderr)
    raise SystemExit(1)
if expiry <= warning_window:
    print("ERROR: certificate pin-set kedaluwarsa dalam 30 hari; rotasi wajib sebelum merge", file=sys.stderr)
    raise SystemExit(1)
print("BERSIH: pin-set masih berlaku lebih dari 30 hari.")
