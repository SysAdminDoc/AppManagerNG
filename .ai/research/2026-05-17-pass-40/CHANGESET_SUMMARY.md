<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Changeset summary — pass 40 (iter-63)

Date: 2026-05-17

## Scope

Pass 40 was an autonomous deep-audit + roadmap-research run. It produced both a
code-side reliability/security fix set and a roadmap research-additions section.

## Code-side fix set (uncommitted in working tree)

| File | Class | Description |
|------|-------|-------------|
| `app/src/main/java/.../crypto/OpenPGPCrypto.java` | FD leak + double-unregister hardening | Wrapped per-file stream pair in try-with-resources (OpenPgpApi#executeApi does not close streams); guarded `close()` against duplicate `unregisterReceiver` IllegalArgumentException. |
| `app/src/main/java/.../uri/GrantUriUtils.java` | Crash | Added `-1` guard on `indexOf(":", 1)` substring calls; try/catch around `Uri.parse`; null-authority fallback in `toLocalisedString`. |
| `app/src/main/java/.../adb/AdbConnectionManager.java` | Lazy-init race | `synchronized` on `getInstance()` — concurrent first-callers could both race in `KeyStoreManager.addKeyPair(ADB_KEY_ALIAS, …, overwrite=true)`. |
| `app/src/main/java/.../crypto/ks/KeyStoreManager.java` | Lazy-init race | `synchronized` on `getInstance()` and `reloadKeyStore()`. |
| `app/src/main/java/.../db/AppsDb.java` | Lazy-init race | `synchronized` on `getInstance()` — concurrent Room database builds would otherwise leak the unreferenced instance. |
| `app/src/main/java/.../StaticDataset.java` | Native UAF race | `synchronized` on all four `AhoCorasick` mutating accessors (`getSearchableTrackerSignatures` / `cleanup` / `getTrackerNames` / `getDebloatObjects`). |
| `app/src/main/java/.../utils/PackageUtils.java` | Shell-injection defense-in-depth | `getHiddenCodePathOrDefault` now refuses anything outside `[A-Za-z0-9._]` before interpolating into `pm dump <pkg> \| grep codePath`. |
| `app/src/test/java/.../utils/PackageUtilsPackageNameValidationTest.java` | Test coverage | Locks down shell-metacharacter rejection (`;`, `|`, `&`, `$()`, backticks, whitespace, quotes, redirects, backslashes, oversized, digit-leading). |
| `app/src/main/java/.../fm/FmViewModel.java` | Null-cursor NPE | Added null guard on `ContentResolver.query()` return; lets code fall through to publish an empty folder instead of opaque "Failed query" log. |
| `app/src/main/java/.../utils/CpuUtils.java` | Systemic wake-lock leak helper | New `acquireWakeLock(WakeLock)` helper with a 2-hour fallback timeout (`DEFAULT_WAKE_LOCK_TIMEOUT_MILLIS`). |
| `app/src/main/java/.../batchops/BatchOpsService.java` | Wake-lock leak | Routed through new helper. |
| `app/src/main/java/.../profiles/ProfileApplierService.java` | Wake-lock leak | Routed through new helper. |
| `app/src/main/java/.../apk/installer/PackageInstallerService.java` | Wake-lock leak | Routed through new helper. |
| `app/src/main/java/.../logcat/LogcatRecordingService.java` | Wake-lock leak | Routed through new helper. |
| `app/src/main/java/.../rules/compontents/ComponentUtils.java` | IFW rules-import truncation | Single malformed `<component-filter>` entry no longer NPE-cascades into dropping every subsequent rule; flipped `.equals()` direction for null-safety. |

## ROADMAP updates

- `ROADMAP.md` header: bumped Last-Updated to iter-63 / pass 40; prior iter-62 entry preserved under "Prior".
- `ROADMAP.md` new section "Iter-63 Research Additions (2026-05-17 — pass 40)": 3 corrections to existing rows + 11 new candidate rows + 2 new Rejected entries + Iter-63 Closures subsection mirroring the code-side fix set.
- `ROADMAP.md` Engineering Debt Register: Material Components 1.13.x row and AGP 8.13.2 row updated in place with corrected facts (Material 1.14.0 stable confirmed shipped 2025-05-13; AGP 10 late-2026 removes AGP-9 opt-outs).
- `ROADMAP.md` Source Appendix: 21 new sources S341–S361 appended.

## External research surfaces hit

- Top-20 OSS competitor releases page rechecks (Atom feed where HTML scrape produced future-dated hallucinations).
- Android platform progression rechecks (Android 17 release notes / Beta channel / Canary cadence / Play policy bulletin).
- Dependency-pin currency rechecks (Material Components, AGP, Bouncy Castle, libsu, Shizuku-API, jadx, Room, biometric).
- Community pain-point mining (GitHub issue trackers for Shizuku / SD Maid SE / Obtainium / InstallerX / Hail / Canta).
- Adjacent-tool / new-entrant search (privilege providers, per-app firewall, app cloning, Tasker integration patterns).
- Standards / new APIs (A16 Bluetooth bond-loss intents, A16 archived-APK format, A16 signature-permission allowlist, Health Connect SPN change).

## Verification status

- `git diff --check`: confirmed clean of whitespace errors on the code-side edits (no merge markers, no trailing whitespace introduced).
- XML / unit-test integrity: new test class is single-file JUnit 4 matching the project convention; no platform dependencies.
- Gradle test attempt: blocked by environment (no `JAVA_HOME` / `javac` on this VM), consistent with iter-39's same blocker.
- Markdown lint on `ROADMAP.md`: one MD051 link-fragment warning introduced and fixed inline; remaining pre-existing MD032/MD060 style warnings unchanged.

## Push status

Push remains expected-blocked on this VM (matches iter-39 caveat); maintainer to commit + push from authorized host. Local commits should follow the existing conventional-commit pattern with NO `Co-Authored-By: Claude` trailer (project convention).
