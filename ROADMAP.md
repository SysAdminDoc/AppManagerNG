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

## Deep Audit Follow-ups (2026-07-14)

Findings from the 2026-07-14 deep audit that were not fixed in place because they
need a versioned data migration, a design decision, a broad multi-site refactor,
or on-device verification. High-confidence host-fixable bugs from the same audit
were fixed directly (see git history / CHANGELOG).

### P3

## Research-Driven Additions (2026-07-14)

Backing research: `RESEARCH.md` (2026-07-14). Fresh host-verifiable code audit plus an
upstream/ecosystem sweep (App Manager v4.1.0, LibChecker/Hail/Canta/InstallerX/SD Maid SE,
dependency CVEs, Android 16/17 APIs). All items below are host-verifiable and unit-testable
offline. Device-gated feature ideas from this pass are in `Roadmap_Blocked.md`.

### P2

### P3

## Research-Driven Additions

### P1

### P2

### P3

## Research-Driven Additions (2026-07-22)

Backing research: `RESEARCH.md` (2026-07-22). Competitor harvest (InstallerX-Revived,
LibChecker, SD Maid, Hail) + a fresh host-verifiable audit and dependency/CVE sweep.
Upstream shipped no new tag since v4.1.0 (2026-06-29); all prior host findings are already
fixed, so these are net-new. Every item below is host-implementable and host-testable
(Robolectric/JUnit/Jazzer) except where a final on-device check is noted.

### P2

### P3


## Research-Driven Additions (2026-07-29)

### P0

### P1

### P2

## Security Threat-Model Follow-ups (2026-07-30)

Source: an automated security scan of the privileged core (`server`, `libserver`,
`libcore/io`, `libcore/compat` — 99 files at revision `57838cd`) that was **stopped during
the research stage**. The inventory and four threat models completed; no vulnerability
researcher reported and nothing below was confirmed by a verification panel.

Every row is therefore an **unverified hypothesis with a file:line anchor**, not a
confirmed vulnerability. Each one starts by establishing whether the weakness is real and
reachable — several are plausibly already mitigated by callers outside the scanned scope
(notably `RootServiceServer` doing the `getCallingUid()` work at
`app/src/main/java/io/github/muntashirakon/AppManager/ipc/RootServiceServer.java:127/145/182`).
Closing a row with evidence that it is already handled is a valid outcome and should leave
a regression test behind. All are host-verifiable with JUnit/Robolectric/Jazzer; none needs
a device.

### P1

### P2

### P3


## Audit Findings — 2026-08-02

Source: a read-only, multi-pass audit at revision `c1e609861`. Baseline recorded before any
inspection: `:app:testFlossDebugUnitTest` + `:libcore:compat:test` = 2491 tests, 0 failures, 1
skipped; `py -3.12 -m unittest discover -s scripts/tests` = 47 tests, 0 failures;
`./gradlew :app:lint` **fails** (see the first P0 below — pre-existing baseline failure). Every
item was traced to a reachable caller and checked against existing guards, `CLAUDE.md` and the
last 50 commits before being logged.

Investigated and deliberately **not** logged — do not re-raise these:
`CutPasteId` in `AppDetailsComponentsFragment` (deliberate view-slot aliasing, not a paste error);
`NewApi` on `AppLocaleOptions.stripExtensions` (guarded by an `SDK_INT < TIRAMISU` early return in
`AppInfoFragment#showAppLocalePicker`); `NewApi` tooltips in `MultiSelectionActionsView`,
`ModeOfOpsPreference` and `AudioPlayerDialogFragment` (all correctly `SDK_INT`-guarded); missing
night overrides for the `premium_*_light/_dark/_amoled` colours (correct raw-token plus
semantic-alias architecture — `values-night/colors-v2.xml` overrides the aliases); AMOLED tokens
(all six are referenced); `notifyDataSetChanged` in `MainActivity` (one-shot import path only, the
main list already diffs via `AdapterUtils`); unused `ArrayUtils.remove(ArraySet)` (vendored AOSP
file kept verbatim by design); `Overdraw` (14 layouts, negligible impact).

Two dimensions were swept and found **genuinely clean** — recorded here so the next pass does not
repeat the work:

- **Security.** No `WebView` anywhere in the app, so the usual JavaScript/file-access/JS-bridge
  surface does not exist. Every `PendingIntent` factory call already passes an explicit mutability
  flag (the only three grep hits are method declarations returning `PendingIntent`, not factory
  calls). Of 36 exported components with no `android:permission`, the sensitive ones are gated in
  code rather than by manifest permission: `AutomationUriActivity` — reachable from the web via
  `BROWSABLE` `am://` links — runs behind the app's authentication gate (`onAuthenticated`) and then
  shows an explicit confirmation naming the action, target and user
  (`automation_request_confirm_message` = "Action: %1$s\\nTarget: %2$s\\nUser: %3$s"). The backup
  rules correctly keep `server_secrets.xml` out of both cloud backup and device transfer, and those
  excludes sit inside an `<include domain="sharedpref" path="."/>`, so they take effect. The nine
  `FullBackupContent` lint Fatals are redundant `<exclude>` entries for domains that were never
  included — noise, not a leak. The privileged core (`server`, `libserver`, `libcore/io`,
  `libcore/compat`) was hardened across the nine commits ending at `c1e609861` and was not re-audited
  here.
- **Performance.** No material issue found. `Overdraw` affects 14 layouts but is negligible;
  `notifyDataSetChanged` survives only on a one-shot import path; `StaticDataset` uses lazy static
  caches rather than parsing its large resource tables eagerly. Note this was assessed statically —
  no profiling or device run was performed, so see the "Unaudited areas" item below.

### P1


### P2

### P3

## Research-Driven Additions (2026-08-02)

### P1

### P2
