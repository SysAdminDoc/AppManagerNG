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

- [ ] P2 — Filter-expression highlighting uses pure red/blue and matches inside words
  Category: visual
  Where: `app/src/main/java/io/github/muntashirakon/AppManager/filters/EditFiltersDialogFragment.java:45-52`
  (`HIGHLIGHT_MAP`) and `:204-212` (the `ForegroundColorSpan` loop)
  Problem: two defects in one place. (1) The highlighter applies `ForegroundColorSpan(Color.RED)` and
  `ForegroundColorSpan(Color.BLUE)` — fully saturated `#FF0000` / `#0000FF` — to text inside a themed
  `TextInputLayout`. Pure blue against the app's dark and AMOLED surfaces is roughly 2.4:1, far below
  the WCAG AA 4.5:1 minimum for body text; pure red against the light theme's white surface is roughly
  4:1, also below AA. So one of the two keyword colours is unreadable in whichever theme the user
  picks. These are the only hardcoded UI colours left outside the ANSI terminal palette and the SVG
  parser. (2) The loop uses `text.indexOf(keyword)` with no token boundary, so `true` and `false` are
  highlighted as substrings — a filter naming a package such as `com.truecaller` gets `true` coloured
  mid-word.
  Evidence: `grep -n "HIGHLIGHT_MAP" -A 12` on that file shows
  `s.setSpan(new ForegroundColorSpan(color), index, index + keyword.length(), ...)` at line 209 inside
  `while (index >= 0) { ... index = text.indexOf(keyword, index + keyword.length()); }`. A
  project-wide sweep for `Color.RED|BLUE|WHITE|BLACK|…` finds no other hardcoded colour on a themed
  surface.
  Fix: resolve both colours from the existing V2 token set at runtime instead of using literals — the
  operators from `?attr/colorPrimary` / `premium_color_primary` and the boolean literals from the
  `premium_info_content` semantic token, both of which already switch per mode via
  `app/src/main/res/values/colors-v2.xml` and `values-night/colors-v2.xml`. Use the same
  `getThemeColor(...)` helper pattern as `MainRecyclerAdapter.java:115-118`. For (2), match
  `true`/`false` on word boundaries with a precompiled `Pattern` and leave the single-character
  operators as literal matches.
  Acceptance: no `Color.` constant remains in that file; highlighted text meets 4.5:1 against the
  surface in light, dark and AMOLED; a unit test asserts `com.truecaller` receives no span while a
  standalone `true` does.
  Confidence: Verified — the colour values and span code are confirmed by reading; the contrast ratios
  are computed from the literals rather than measured on a device.
  Effort: M

- [ ] P2 — The lint baseline hides 4060 issues, including every crash found in this audit
  Category: testing
  Where: `app/lint-baseline.xml` (2.2 MB, 4060 suppressed issues); `app/build.gradle:106-111`
  Problem: all three crashes logged above were already detected by lint and silently absorbed by the
  baseline. The baseline currently suppresses 78 `NewApi`, 79 `ThreadConstraint`, 12
  `WrongThreadInterprocedural`, 10 `Fatal` and 261 `Error`-severity findings. Combined with the P0
  above (lint cannot run at all today), nothing prevents the next unguarded API-26 call from reaching
  a release — and with `min_sdk = 21` that is the widest possible exposure. The baseline has become a
  place where real defects go quiet rather than a record of accepted debt.
  Evidence: counting `id="…"` occurrences in `app/lint-baseline.xml` yields 4060 total, with the
  distribution above. The `NewApi` entries include the seven `setTooltipText` lines and the
  `Intent#removeFlags` line proven crashing above; the `WrongThreadInterprocedural` entries include the
  `ScannerViewModel` toast proven crashing above.
  Fix: do not try to empty the baseline. After fixing the three crash items, delete their entries, then
  lift the high-signal checks out of the baseline entirely and promote them to build failures — add
  `lint { error 'NewApi', 'WrongThreadInterprocedural', 'SpecifyForegroundServiceType' }` to
  `app/build.gradle` — so any new occurrence fails the build. Leave the high-volume, low-signal
  categories (`UnknownNullness` 1774, `DuplicateStrings` 1054) baselined, and record in `docs/policy/`
  which categories are intentionally baselined and why.
  Acceptance: `./gradlew :app:lint` runs (needs the P0 above first) and passes; a deliberately
  introduced unguarded API-26 call fails the build instead of being absorbed; the baseline contains no
  `NewApi`, `WrongThreadInterprocedural` or `SpecifyForegroundServiceType` entry.
  Confidence: Verified
  Effort: M

### P3

- [ ] P3 — `AdvancedSearchView#matches` is annotated `@UiThread` but is a pure matcher called from workers
  Category: maintainability
  Where: the annotation on `AdvancedSearchView#matches`; callers at `main/MainViewModel.java:736,753`,
  `details/AppDetailsViewModel.java:567,636,664`, `runningapps/RunningAppsViewModel.java:571`
  Problem: eight of the twelve `WrongThreadInterprocedural` violations in the project come from this
  one wrong annotation. Every list-filtering path correctly runs on a worker thread and calls
  `matches`, which is annotated `@UiThread` despite being a pure string matcher that touches no UI. The
  annotation is both false and actively harmful: it fills the baseline with noise that trains readers
  to skim past thread warnings, which is how the genuine `ScannerViewModel` toast violation stayed
  buried in the same list.
  Evidence: the twelve `WrongThreadInterprocedural` entries in `app/lint-baseline.xml` are eight
  `-> AdvancedSearchView#matches`, one `-> View#getContext`, one `-> UIUtils#displayLongToast` (the
  real bug logged above), and two whose message did not resolve.
  Fix: read `matches` to confirm it touches no view state, then change the annotation to `@AnyThread`
  and delete the eight stale baseline entries. If it does touch view state, the correct fix is the
  reverse — move that state out of the matcher — so read it before changing the annotation.
  Acceptance: the eight `AdvancedSearchView#matches` entries are gone from `app/lint-baseline.xml` and
  lint reports no new `WrongThreadInterprocedural`.
  Confidence: Verified
  Effort: S

- [ ] P3 — Two layouts set `paddingEnd` without `paddingStart`, breaking RTL symmetry
  Category: a11y
  Where: `app/src/main/res/layout/activity_main_v2.xml:67`;
  `app/src/main/res/layout/item_app_details_primary.xml:39`
  Problem: both define an end padding with no matching start padding, so spacing becomes asymmetric
  when the layout direction flips. Neither is an obscure screen — the first is the main activity shell,
  the second is the row used by every App Details component list. The app ships `values-ar`,
  `values-ar-rSA` and `values-fa`, so RTL is a supported configuration.
  Evidence: lint reports `RtlSymmetry` ("When you define `paddingEnd` you should probably also define
  `paddingStart` for right-to-left symmetry") for exactly these two lines; both are suppressed in
  `app/lint-baseline.xml`, and they are the only two `RtlSymmetry` issues in the project.
  Fix: add the matching `android:paddingStart` using the same `@dimen` token as the end padding, or
  collapse to a symmetric `paddingHorizontal` where both sides should match. Verify under
  Settings → Developer options → Force RTL layout direction.
  Acceptance: both `RtlSymmetry` baseline entries are removed and lint stays clean; the main list and
  an App Details component row show even leading/trailing spacing in an RTL locale.
  Confidence: Verified — the layout attributes are confirmed by reading; the visual asymmetry was not
  observed on a device.
  Effort: S

- [ ] P3 — Two custom touch handlers never call `performClick()`, so assistive tech cannot activate them
  Category: a11y
  Where: `BarChartView#onTouchEvent` (`BarChartView.java:727`); `BottomSheetDialog.java:353,356`
  (a `FrameLayout` with `setOnTouchListener`)
  Problem: a view that consumes touches in `onTouchEvent` / `OnTouchListener` without calling
  `performClick()` never fires the accessibility click action, so screen-reader and switch-access users
  cannot trigger it. `BarChartView` is the usage/data chart; the `BottomSheetDialog` `FrameLayout` is
  the tap-outside-to-dismiss target, so dismissing a sheet by tap is unreachable to those users.
  Evidence: lint reports all three as `ClickableViewAccessibility` — "`BarChartView#onTouchEvent`
  should call `BarChartView#performClick` when a click is detected" and "Custom view `FrameLayout` has
  `setOnTouchListener` called on it but does not override `performClick`" — all suppressed in
  `app/lint-baseline.xml`, and they are the only three such issues in the project.
  Fix: in `BarChartView`, override `performClick()` (calling `super.performClick()`) and invoke it from
  `onTouchEvent` on `ACTION_UP` when the gesture resolves to a click. For the bottom-sheet
  `FrameLayout`, call `view.performClick()` in the `onTouch` handler on `ACTION_UP` before returning.
  Confirm the sheet retains a non-touch dismissal route (back gesture or drag handle) before treating
  it as done.
  Acceptance: the three `ClickableViewAccessibility` baseline entries are removed and lint stays clean;
  TalkBack can activate the chart and dismiss the bottom sheet.
  Confidence: Verified — the lint findings and call sites are confirmed by reading; TalkBack behaviour
  was not exercised on a device.
  Effort: S

- [ ] P3 — `CLAUDE.md` overstates the remaining upstream-branding copy debt
  Category: docs
  Where: `CLAUDE.md`, section "Hardcoded 'App Manager' string references"
  Problem: the note claims `grep -n "App Manager"` across `app/src/main/res/` "returns hundreds of hits
  … copy inside dialogs/help screens still says 'App Manager'" and that a sweep is pending. For the
  English strings that is no longer true: exactly one user-visible occurrence remains, and it is
  intentional. A stale note describing a large phantom task misdirects future work, and the repo's own
  self-healing-memory rule asks for notes like this to be corrected in place once disproven.
  Evidence: stripping `xliff:g example="…"` translator-hint attributes from
  `app/src/main/res/values/strings.xml` and scanning only `<string>` bodies leaves a single visible hit
  — `pref_export_upstream_compat` = "Export for upstream App Manager" — which correctly names the
  upstream project. The other 21 raw matches all sit inside `example=` attributes, which are never
  rendered to users.
  Fix: rewrite that section to state the verified position (English copy is clean; the one remaining
  reference is intentional) and, if non-English locales still carry stale product names, scope the note
  to those locales with a real count instead of "hundreds".
  Acceptance: the note matches what a fresh grep shows, with the `xliff` caveat stated so the next
  reader does not have to re-derive it.
  Confidence: Verified
  Effort: S

- [ ] P3 — Unaudited areas needing their own pass
  Category: docs
  Where: repository-wide
  Problem: the 2026-08-02 pass was static and host-only. The following were not covered and must not be
  assumed clean: (a) any on-device or emulator run — no UI was rendered, so all visual, motion,
  focus-order, touch-target and measured-contrast checks remain unverified by observation; (b) the
  native C/C++ under `app/src/main/cpp/` and the JNI boundary; (c) the ~40 non-English locales beyond
  string-level checks — no pseudolocale, overflow or truncation review; (d) Room schema and migration
  correctness under `schema/`; (e) the `benchmark/` module; (f) the packages `viewer/`, `types/`,
  `magisk/` and `progress/`, the only ones with no unit-test directory at all; (g) runtime behaviour of
  the privileged root/ADB modes, which needs a rooted device.
  Evidence: no emulator or device was attached during the audit;
  `find app/src/test/java/io/github/muntashirakon/AppManager -maxdepth 1 -type d` lists a directory for
  every main package except those four.
  Fix: schedule a device-backed UX/visual pass covering light, dark and AMOLED plus RTL and a
  pseudolocale, and a separate native/JNI review.
  Acceptance: each listed area has either findings logged against it or an explicit note that it was
  reviewed and found clean.
  Confidence: Verified
  Effort: M
