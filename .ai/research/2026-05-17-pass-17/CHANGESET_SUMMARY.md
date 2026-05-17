<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET SUMMARY — 2026-05-17 pass 17

## Roadmap item closed

- T11 `APK Share-Target Receiver`

## Implementation status

No code change was required. The roadmap row described an already-shipped
installer surface and was closed as a stale gap after a source audit.

## Evidence

- `app/src/main/AndroidManifest.xml` exports `PackageInstallerActivity` for:
  - `ACTION_SEND` with `application/vnd.android.package-archive`,
    `application/vnd.apkm`, `application/xapk-package-archive`, and
    `application/octet-stream`;
  - `ACTION_SEND_MULTIPLE` with `*/*`;
  - `ACTION_VIEW` / `ACTION_INSTALL_PACKAGE` for `file:`, `content:`, and
    `package:` URIs.
- `ApkQueueItem.fromIntent()` reads incoming data/share URIs through
  `IntentCompat.getDataUris(intent)`, creates `ApkSource` queue items, records
  originating metadata, and persists granted URI permissions when present.
- `PackageInstallerActivity` routes shared items through the normal installer
  dialog flow and already surfaces:
  - tracker count callouts;
  - dependency warnings from `InstallDependencyChecker`;
  - session SHA-256 confirmation before Android's install prompt;
  - signature-mismatch reinstall handling.

## Files changed

- `CHANGELOG.md`
- `ROADMAP.md`
- `PROJECT_CONTEXT.md`
- `.ai/research/2026-05-17-pass-17/CHANGESET_SUMMARY.md`
- `.ai/research/2026-05-17-pass-17/CONTINUE_FROM_HERE.md`

## Verification

- Source audit completed with `rg` and line-numbered reads of the manifest,
  `ApkQueueItem`, and `PackageInstallerActivity`.
- `git diff --check` passed.
- No Gradle test was run for this documentation/audit-only closure.

## Push status

Push remains skipped because `origin` is `https://github.com/SysAdminDoc/AppManagerNG.git`
while the configured GitHub credential authenticates as `MavenImaging`, producing
a 403 earlier in this session.
