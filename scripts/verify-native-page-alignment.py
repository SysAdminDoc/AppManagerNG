#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Fail release builds that contain native libraries incompatible with 16 KB pages."""

from __future__ import annotations

import argparse
import os
import struct
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


ELF_MAGIC = b"\x7fELF"
ELF_CLASS_32 = 1
ELF_CLASS_64 = 2
ELF_DATA_LITTLE = 1
ELF_DATA_BIG = 2
PT_LOAD = 1
PAGE_SIZE_16_KB = 16 * 1024
LOCAL_FILE_HEADER_SIGNATURE = 0x04034B50


@dataclass(frozen=True)
class ElfAlignment:
    min_load_alignment: int
    load_segment_count: int


def iter_apks(paths: Iterable[str]) -> list[Path]:
    apks: list[Path] = []
    for raw_path in paths:
        path = Path(raw_path)
        if path.is_dir():
            apks.extend(sorted(p for p in path.rglob("*.apk") if p.is_file()))
        elif path.is_file() and path.suffix.lower() == ".apk":
            apks.append(path)
        else:
            raise ValueError(f"{path} is neither an APK file nor a directory containing APKs")
    return apks


def parse_elf_alignment(data: bytes) -> ElfAlignment:
    if len(data) < 20 or not data.startswith(ELF_MAGIC):
        raise ValueError("not an ELF shared object")
    elf_class = data[4]
    elf_data = data[5]
    if elf_data == ELF_DATA_LITTLE:
        endian = "<"
    elif elf_data == ELF_DATA_BIG:
        endian = ">"
    else:
        raise ValueError(f"unsupported ELF endianness {elf_data}")

    if elf_class == ELF_CLASS_32:
        if len(data) < 46:
            raise ValueError("truncated ELF32 header")
        phoff = struct.unpack_from(f"{endian}I", data, 28)[0]
        phentsize = struct.unpack_from(f"{endian}H", data, 42)[0]
        phnum = struct.unpack_from(f"{endian}H", data, 44)[0]
        min_entry_size = 32
        align_offset = 28
        align_format = "I"
    elif elf_class == ELF_CLASS_64:
        if len(data) < 58:
            raise ValueError("truncated ELF64 header")
        phoff = struct.unpack_from(f"{endian}Q", data, 32)[0]
        phentsize = struct.unpack_from(f"{endian}H", data, 54)[0]
        phnum = struct.unpack_from(f"{endian}H", data, 56)[0]
        min_entry_size = 56
        align_offset = 48
        align_format = "Q"
    else:
        raise ValueError(f"unsupported ELF class {elf_class}")

    if phoff <= 0 or phentsize < min_entry_size or phnum <= 0:
        raise ValueError("missing or invalid ELF program header table")
    if phoff + phentsize * phnum > len(data):
        raise ValueError("ELF program header table extends beyond entry bytes")

    alignments: list[int] = []
    for index in range(phnum):
        offset = phoff + index * phentsize
        segment_type = struct.unpack_from(f"{endian}I", data, offset)[0]
        if segment_type != PT_LOAD:
            continue
        alignment = struct.unpack_from(f"{endian}{align_format}", data, offset + align_offset)[0]
        alignments.append(alignment)

    if not alignments:
        raise ValueError("ELF has no PT_LOAD program headers")
    return ElfAlignment(min(alignments), len(alignments))


def zip_data_offset(apk_path: Path, info: zipfile.ZipInfo) -> int:
    with apk_path.open("rb") as apk_file:
        apk_file.seek(info.header_offset)
        header = apk_file.read(30)
    if len(header) != 30:
        raise ValueError("truncated ZIP local file header")
    signature = struct.unpack_from("<I", header, 0)[0]
    if signature != LOCAL_FILE_HEADER_SIGNATURE:
        raise ValueError(f"bad ZIP local file header signature 0x{signature:x}")
    filename_length = struct.unpack_from("<H", header, 26)[0]
    extra_length = struct.unpack_from("<H", header, 28)[0]
    return info.header_offset + 30 + filename_length + extra_length


def check_apk(apk_path: Path) -> list[str]:
    failures: list[str] = []
    checked = 0
    with zipfile.ZipFile(apk_path) as apk_zip:
        for info in sorted(apk_zip.infolist(), key=lambda item: item.filename):
            if info.is_dir() or not info.filename.endswith(".so"):
                continue
            checked += 1
            entry_name = f"{apk_path}!{info.filename}"
            try:
                alignment = parse_elf_alignment(apk_zip.read(info))
            except (OSError, ValueError, struct.error) as exc:
                failures.append(f"{entry_name}: cannot verify ELF page alignment: {exc}")
                continue
            if alignment.min_load_alignment < PAGE_SIZE_16_KB:
                failures.append(
                    f"{entry_name}: PT_LOAD p_align={alignment.min_load_alignment} "
                    f"(< {PAGE_SIZE_16_KB})"
                )
            if info.compress_type == zipfile.ZIP_STORED:
                try:
                    data_offset = zip_data_offset(apk_path, info)
                except ValueError as exc:
                    failures.append(f"{entry_name}: cannot verify ZIP data offset: {exc}")
                    continue
                if data_offset % PAGE_SIZE_16_KB != 0:
                    failures.append(
                        f"{entry_name}: uncompressed ZIP data offset {data_offset} "
                        f"is not {PAGE_SIZE_16_KB}-byte aligned"
                    )
    if checked == 0:
        print(f"No native libraries found in {apk_path}")
    else:
        print(f"Verified {checked} native libraries in {apk_path}")
    return failures


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Verify every .so inside APK files has 16 KB-compatible ELF load "
            "segment alignment, and that uncompressed native entries are 16 KB "
            "ZIP-aligned."
        )
    )
    parser.add_argument("paths", nargs="+", help="APK file(s) or directories to scan")
    args = parser.parse_args()

    try:
        apks = iter_apks(args.paths)
    except ValueError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    if not apks:
        print("error: no APK files found", file=sys.stderr)
        return 2

    failures: list[str] = []
    for apk_path in apks:
        try:
            failures.extend(check_apk(apk_path))
        except zipfile.BadZipFile as exc:
            failures.append(f"{apk_path}: invalid APK/ZIP: {exc}")

    if failures:
        print("Native page-alignment verification failed:", file=sys.stderr)
        for failure in failures:
            print(f"::error::{failure}", file=sys.stderr)
        return 1
    print(f"Native page-alignment verification passed for {len(apks)} APK(s).")
    return 0


if __name__ == "__main__":
    if os.name == "nt":
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    raise SystemExit(main())
