<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Split-APK breakdown in App Details — Audit

**Date:** 2026-05-26
**Roadmap reference:** [ROADMAP.md](../../ROADMAP.md) — T19-A
"App Details Storage panel completeness audit" row.
**Outcome:** ✅ Split-APK enumeration already lands in App Details
through `PackageInfo.splitNames` + `splitPublicSourceDirs`; the gap
is presentation, not data.

## Background

The T19-A row asks two questions:

1. Does App Details enumerate split APKs at all?
2. Is backup-archive size surfaced as a sibling row?

Question 2 was answered in the same iteration (see
[`BackupArchiveSizeAggregator`](../../app/src/main/java/io/github/muntashirakon/AppManager/details/info/BackupArchiveSizeAggregator.java)
and the
[Backup archive size aggregator changelog entry](../../CHANGELOG.md)).
This audit answers question 1.

## Method

```bash
# Where do we read splits from PackageInfo?
grep -rn "splitNames\|splitPublicSourceDirs\|splitSourceDirs" \
  app/src/main/java/io/github/muntashirakon/AppManager/

# How does App Details Storage block consume the data today?
grep -n "hasSplits\|splitNames\|splitApks\|ApkFile" \
  app/src/main/java/io/github/muntashirakon/AppManager/details/info/AppInfoFragment.java

# Existing user-visible split surface (App Details "APKs" picker)?
grep -rn "class SplitApkChooser\|ApkFile\.entries" \
  app/src/main/java/io/github/muntashirakon/AppManager/
```

## Findings

### 1. The data is already plumbed

- `App.hasSplits` (Room cache) is the boolean "does this package ship
  splits". Refreshed by every `MainViewModel` cache pass.
- `ApkFile.entries()` enumerates every APK entry on the install
  (base.apk + every config split + every density split + every locale
  split) with per-entry size in bytes. This is the canonical surface
  the existing
  [`SplitApkChooser`](../../app/src/main/java/io/github/muntashirakon/AppManager/apk/splitapk/SplitApkChooser.java)
  consumes when the user installs an APKS / APKM bundle.
- The "APK file format" detail (T18 / T19) sub-screen of App Details
  already lists every split in a grouped form when the user expands
  it.

### 2. The Storage and Cache block does not yet surface a split-by-split
breakdown

`AppInfoFragment.Storage` reads from `PackageSizeInfo` (one number
per category: code / data / cache / obb / media / total). For
non-split installs that is the complete picture. For split installs
the `code` row already includes every split's bytes (Android reports
the post-merge size for storage stats), so the user is not
under-informed on total bytes - but the row does not break that
total down by split.

### 3. Recommended slice (not landed in this audit)

A future Android-side pass can add a `Splits` expander row to the
Storage and Cache block that iterates `ApkFile.entries()` and renders
per-split bytes. The aggregator already exists in
[`BackupArchiveSizeAggregator.formatBytes`](../../app/src/main/java/io/github/muntashirakon/AppManager/details/info/BackupArchiveSizeAggregator.java)
- the unit formatter is generic and not specific to backup archives.
Rendering can re-use it directly without duplicating the byte
ladder.

### 4. Audit boundary

This audit is documentation-only. No data-layer or UI changes ship in
this audit's commit; both the existing split enumeration (`ApkFile`)
and the new backup-archive aggregator already live in the tree under
matching changelog entries.

## Open follow-ups

- [ ] App Details "Storage and Cache" -> "Splits" expander row using
  `ApkFile.entries()` + `BackupArchiveSizeAggregator.formatBytes`.
  Tracked under the next T19-A iteration; this audit closes the
  enumeration-coverage half of the row.
- [ ] Per-locale and per-density split labelling for the expander
  (already partially handled by `ApkFile.ApkEntry.label`).
