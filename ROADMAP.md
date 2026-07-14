<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Maintainer-local historical archives are not
published with the repository. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

All remaining items are in `Roadmap_Blocked.md` — gated on device access,
visual verification, privileged-mode testing, or external dependencies.

## Research-Driven Additions (2026-06-20)

All remaining blocked items are in `Roadmap_Blocked.md`.

## Deep Audit Follow-ups (2026-07-02)

## Research-Driven Additions

Backing research: `RESEARCH.md` (2026-07-12) — user-state protection (snapshot secret
boundary, Room migration safety, snapshot portability, encrypted bundles, release/screenshot
truth) — combined with a host-verifiable code-level correctness audit of the intercept /
backup / apk-parser / search / debloat / filters / rules / uri subsystems (findings cited
inline per item). All items are fixable and unit-testable offline except the screenshot
refresh, which needs a capture environment.

### P1

### P2

### P3

## Research-Driven Additions (2026-07-14)

Backing research: `RESEARCH.md` (2026-07-14). Fresh host-verifiable code audit plus an
upstream/ecosystem sweep (App Manager v4.1.0, LibChecker/Hail/Canta/InstallerX/SD Maid SE,
dependency CVEs, Android 16/17 APIs). All items below are host-verifiable and unit-testable
offline. Device-gated feature ideas from this pass are in `Roadmap_Blocked.md`.

### P2

- [ ] P2 — Port upstream host-verifiable fixes shipped after our base commit
  Why: upstream shipped these after base `3d11bcb`; each is unit-testable offline and closes a correctness/compat gap: TarUtils integer-overflow guard; A12+ keystore-entry backup gate (keystore can't be backed up on Android 12+); `am start -d <link>` / link-interception intent resolution (open issue #2001); report install result via `EXTRA_RETURN_RESULT` to match the stock-installer contract (#2003).
  Evidence: upstream commits `3899dca0a` (TarUtils), `bdb293626` (keystore A12+), `4a25c3f0f` (link interception, #2001), issue #2003 (`EXTRA_RETURN_RESULT`).
  Touches: `backup/tar/` or `utils/TarUtils`, backup keystore-entry path, `intercept/`/manifest intent-filter, `apk/installer/` result reporting; CHANGELOG.md with upstream attribution.
  Acceptance: TarUtils rejects overflowing sizes; keystore backup is skipped/labeled on API 31+ without error; a `<link>` intent resolves through the interceptor; the installer returns `EXTRA_RETURN_RESULT` on session finish; each has a host test.
  Complexity: M

- [ ] P2 — Regression test locking in AES-GCM reuse immunity
  Why: upstream #1958 ("GCM cipher cannot be reused for encryption") breaks large-app backups; NG's streaming `GCMBlockCipher.newInstance()` + `CipherInputStream` design is structurally immune but nothing pins that, so a future refactor to the JCE `Cipher` API could silently reintroduce it.
  Evidence: `crypto/AESCrypto.java` (BC streaming GCM); upstream `MuntashirAkon/AppManager#1958`.
  Touches: `app/src/test/` AES round-trip test.
  Acceptance: a large (>2 GiB simulated / multi-block) encrypt→decrypt round trip through `AESCrypto` succeeds without a cipher-reuse exception; test fails if the crypto path is swapped to a non-reusable single-shot `Cipher`.
  Complexity: S

### P3

- [ ] P3 — Harden dex header detection against partial reads and odex fallback
  Why: `isDex` discards the `read()` return so a short read (SAF/remote streams) misidentifies a real `.dex`; the odex fallback then retries the parser on the same already-consumed, non-resettable stream so a valid `.odex` always fails.
  Evidence: `dex/DexUtils.java:77` (ignored partial read), `dex/DexUtils.java:216-230` (consumed-stream odex retry) via `dex/DexClasses.java`.
  Touches: `dex/DexUtils.java`, dex detection/parse test.
  Acceptance: header detection fully reads (or EOFs) before comparing; the odex fallback parses from offset 0 (buffered/re-readable stream); tests cover a short-read dex and a valid odex.
  Complexity: S

- [ ] P3 — Fix small correctness defects: profile null-path NPE, FmItem compareTo/equals, search hint "null"
  Why: three low-risk offline bugs — `ProfileManager` can pass a `@Nullable` resolved path into `@NonNull` `BaseProfile.fromPath` (NPE outside the caught exceptions); `FmItem.compareTo` returns 0 for unequal items (breaks the `TreeSet`/`TreeMap` contract, can silently drop items); `AdvancedSearchView` renders a literal `"null (Contains)"` hint when no query hint is set.
  Evidence: `profiles/ProfileManager.java:174-176`; `fm/FmItem.java:160-166`; `misc/AdvancedSearchView.java:439`.
  Touches: those three files, targeted unit tests.
  Acceptance: a missing profile path fails with a clear `IOException`; `FmItem.compareTo` is consistent with `equals` (full-path tie-break); the search hint coalesces null to empty; tests cover each.
  Complexity: S

- [ ] P3 — Surface low-cost inspection signals from data NG already computes
  Why: cheap power-user differentiators that require no new data source — a weak-signature flag (v1-scheme-only APKs) as an at-a-glance security signal; the Android 16 `BODY_SENSORS → android.permissions.health` granular mapping in the permission catalog; and LibChecker-class signals (modern-vs-legacy Xposed API, live-update-notification capability, themed-icon/alias detection) as App Details / Finder rows.
  Evidence: LibChecker 2026 releases; Android 16 behavior-changes (health permissions); `utils/PackageUtils.java` signing-scheme data; existing `XposedModuleInfo`/permission parsing.
  Touches: `details/info/` (signing + capability chips), `permission/` catalog, `filters/options/` (new predicates), `scanner/`/`details/` Xposed/icon inspection.
  Acceptance: an app signed only with scheme v1 shows a weak-signature chip; health permissions map to the granular group; at least one new LibChecker-class signal appears in App Details and is filterable in the Finder; all verified by host unit tests over fixture package data.
  Complexity: M

- [ ] P3 — Evaluate ARSCLib V1.4.0 pin bump
  Why: ARSCLib is currently pinned to a commit; a tagged `V1.4.0` release (2026-07-01) supersedes it and may carry arsc-parsing fixes relevant to the manifest/resource viewer.
  Evidence: https://github.com/REAndroid/ARSCLib/releases/tag/V1.4.0; current pin in `versions.gradle`.
  Touches: `versions.gradle`, resource/manifest parsing tests.
  Acceptance: the pin moves to `V1.4.0`, the project builds, and existing manifest/resource-parsing tests pass; any behavior delta is noted in CHANGELOG.md.
  Complexity: S
