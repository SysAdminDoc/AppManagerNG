<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Historical surfaces are archived under
`docs/roadmap/archive/`. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

All remaining items are in `Roadmap_Blocked.md` — gated on device access,
visual verification, privileged-mode testing, or external dependencies.

## Research-Driven Additions (2026-06-19)



### P3

- [ ] P3 — Terminal command history persistence and minimal completion
  Why: TermActivity.java line 49 is the project's oldest TODO ("Replace it with an actual terminal"); lines 98, 107, 184 mark tab completion, command history, and init-script support as missing; all are standard terminal expectations
  Evidence: TermActivity.java TODOs; Inure ships a terminal with history; upstream #23 (21 comments) requests a proper terminal
  Touches: app/src/main/java/io/github/muntashirakon/AppManager/terminal/TermActivity.java (history ring buffer, file-based persistence, input completion overlay)
  Acceptance: terminal persists command history across sessions (stored in app-private file); up/down arrow keys navigate history; tab key triggers path/command completion from available executables; init-script support loads `~/.amrc` or equivalent on session start; unit test for history ring buffer and persistence
  Complexity: M

- [ ] P3 — Auto-freeze on screen lock with optional re-freeze delay
  Why: Hail's auto-freeze-on-screen-lock is the most-requested freeze UX pattern (6k stars, explicitly highlighted as its killer feature); NG has freeze/unfreeze plumbing, QS tile, and shortcut-based unfreezing, but no automatic re-freeze after the user leaves the app
  Evidence: Hail's auto-freeze feature; upstream App Manager unfreeze-on-shortcut-launch feature; existing FreezeUnfreezeService.java and FreezeRule.java infrastructure
  Touches: app/src/main/java/io/github/muntashirakon/AppManager/apk/behavior/ (new ScreenLockFreezeReceiver for ACTION_SCREEN_OFF), app/src/main/java/io/github/muntashirakon/AppManager/settings/ (auto-freeze preference with optional delay), app/src/main/java/io/github/muntashirakon/AppManager/rules/struct/FreezeRule.java (auto-freeze flag)
  Acceptance: user can enable auto-freeze in settings; all apps with auto-freeze rules are frozen when ACTION_SCREEN_OFF fires (with optional configurable delay); unit test for the receiver logic and delay; preference is off by default
  Complexity: M

- [ ] P3 — IzzyOnDroid repository submission preparation
  Why: Fastlane metadata is already complete (title, descriptions, icon, 9 screenshots, changelogs); IzzyOnDroid is the fastest path to F-Droid ecosystem visibility in Neo Store and Droid-ify clients; distribution readiness is high
  Evidence: fastlane/metadata/android/en-US/ (complete); IzzyOnDroid inclusion policy (requires FLOSS license, Fastlane metadata, release-signed APK, distinct from original); upstream App Manager is already listed
  Touches: fastlane/metadata/android/en-US/ (verify currency of descriptions), docs/distribution/ (submission checklist), README.md (add IzzyOnDroid badge after listing)
  Acceptance: IzzyOnDroid submission request filed at codeberg.org/IzzyOnDroid/repo with correct metadata pointing to GitHub Releases; badge added to README after acceptance
  Complexity: S
