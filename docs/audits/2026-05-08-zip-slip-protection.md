<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Zip-Slip Protection Audit (Backup / Convert / APK)

**Date:** 2026-05-08
**Roadmap reference:** [ROADMAP.md](../../ROADMAP.md) — Engineering Debt Register row "Zip-slip protection in APK/backup extraction".
**Outcome:** ✅ **CLEAN — every disk-extraction path canonicalizes the output path and rejects traversal entries before any bytes are written.**

## Background

A zip-slip vulnerability arises when an archive entry's name (e.g. `../../../etc/passwd`) is used directly as the output path during extraction, allowing the archive to write outside the intended destination directory. Upstream AM v4.0.0-alpha02 added the canonical "double-check" guard (pre-write filename normalization + post-create real-path containment check); NG must verify it survives intact across every disk-writing extraction path.

## Scope

Every code path that:

1. Reads an entry from a zip / tar archive **and**
2. Writes that entry to a file system path **derived from the entry name**.

Conversion paths that read an entry name only as metadata for a *new* archive (not for a disk path) are listed in the second table — those are inherently safe because the consumer's extraction path will be the one that's checked.

## Method

```
grep -rn "getNextEntry\|getNextTarEntry\|ZipInputStream\|TarArchiveInputStream" \
    app/src/main/java/ libcore/ libserver/ \
    --include="*.java" --include="*.kt"
```

Each hit was inspected to determine whether it writes to a path derived from the entry name. Disk-writing paths were then verified to carry both the early traversal check and the late real-path containment check.

## Disk-Writing Extraction Paths (must protect)

| Site | Pre-write check | Post-create check | Verdict |
|------|----------------|-------------------|---------|
| [`TarUtils.extract`](../../app/src/main/java/io/github/muntashirakon/AppManager/utils/TarUtils.java) ~L154 | `Paths.normalize(entry.getName())` rejects `null` and `../`-prefixed names | `realFilePath.startsWith(realDestPath)` after creation | ✅ Both layers present |
| [`AndroidBackupExtractor.extract`](../../app/src/main/java/io/github/muntashirakon/AppManager/backup/adb/AndroidBackupExtractor.java) ~L72 | Same `Paths.normalize` + `startsWith("../")` rejection | Same `realFilePath.startsWith(realDestPath)` after creation | ✅ Both layers present |

Both sites raise `IOException("Zip slip vulnerability detected!")` with both the expected and actual paths, which surfaces clearly in NG's operation log on the (extremely unlikely) malicious-archive case.

## Archive-to-Archive Conversions (no disk path derived from entry name)

| Site | Output strategy | Verdict |
|------|-----------------|---------|
| [`SBConverter`](../../app/src/main/java/io/github/muntashirakon/AppManager/backup/convert/SBConverter.java) ~L229 | Caches each entry to `FileCache.createCachedFile(extension)` — controlled cache dir, name from extension, not entry name | ✅ Inherently safe |
| [`OABConverter`](../../app/src/main/java/io/github/muntashirakon/AppManager/backup/convert/OABConverter.java) ~L339 | Same `FileCache.createCachedFile(extension)` pattern | ✅ Inherently safe |
| [`TBConverter`](../../app/src/main/java/io/github/muntashirakon/AppManager/backup/convert/TBConverter.java) ~L290 | Tar-to-tar conversion only; entry name is metadata for the *new* tar's `TarArchiveEntry`, never a disk path | ✅ Inherently safe |
| [`ApkUtils.getManifestFromApk`](../../app/src/main/java/io/github/muntashirakon/AppManager/apk/ApkUtils.java) ~L181 | In-memory only via `ByteArrayOutputStream` | ✅ Inherently safe |

These conversion paths produce a new archive that is later extracted via `TarUtils.extract` or `AndroidBackupExtractor.extract` — both of which carry the double-check shown in the first table. So even a malicious source archive's `../`-prefixed entry name simply gets re-encoded into the output archive and is rejected at the eventual extraction step.

## Conclusion

Zero remediation required. The `TarUtils` and `AndroidBackupExtractor` extraction paths both implement the canonical pre-write + post-create double-check pattern; archive-to-archive converters never use the source entry name as a disk path. The Engineering Debt Register row "Zip-slip protection in APK/backup extraction" can be closed.

If a future feature wires up a new disk-writing extraction path, it MUST replicate the two-stage check pattern from `TarUtils:154` (early `Paths.normalize` + `startsWith("../")` rejection) and `TarUtils:192` (late `realFilePath.startsWith(realDestPath)` verification).

## References

- [S01]: AM v4.0.0-alpha02 release notes — added zip-slip extraction guard.
