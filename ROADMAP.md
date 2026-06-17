<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Historical surfaces are archived under
`docs/roadmap/archive/`. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

## Product Quality Roadmap (2026-06-12)

- [ ] P3 — Visual token and component polish pass
  Why: cards, banners, list rows, dialogs, badges, chips, toasts, and nested surfaces should
  feel like one product in light, dark, and AMOLED modes without one-off colors or spacing.
  Where: app/src/main/res/{layout,values,drawable}/

- [ ] P3 — Tooltips and microcopy consistency pass
  Why: expert controls need concise labels, explainers, and warnings that are useful without
  being robotic, vague, or inconsistent between screens.
  Where: app/src/main/res/values/strings.xml, app/src/main/java/io/github/muntashirakon/AppManager/

## Research-Driven Additions

### P2

- [ ] P2 — Permission/app-op reference states (desired-vs-actual drift)
  Why: PermissionManagerX's reference-state model (pin desired value per permission/app-op, surface drift, restore references) is the only audit-grade permission pattern in the ecosystem and slots into NG's existing rule store + Permission Inspector.
  Evidence: https://github.com/mirfatif/PermissionManagerX (README, verified)
  Touches: rules/RulesStorageManager.java, rules/struct/, permissions/ (Inspector drift badges), details/AppDetailsPermissionsFragment
  Acceptance: user pins a reference for a permission/app-op; subsequent drift shows a visible indicator in Permission Inspector with one-tap restore-to-reference; references survive app reinstall via the rule store.
  Complexity: L


### P3

- [ ] P3 — File-manager trash bin (staged deletion)
  Why: NG's FM hard-deletes; Files-by-Google's staged trash with 30-day retention is the established data-safety pattern and FM batch ops magnify mistake cost.
  Evidence: Files by Google clean-flow walkthrough (RESEARCH.md Sources); fm/ has no trash concept (verified)
  Touches: fm/ (delete paths, trash root, restore UI), settings/ (retention pref)
  Acceptance: FM delete moves to a trash location with restore; trash auto-empties after the configured retention; "delete permanently" remains available.
  Complexity: M


## Research-Driven Additions (Pass 2 — 2026-06-10)

### P3

- [ ] P3 — Device-wide analytics dashboard (install-source / SDK / signing distributions)
  Why: Inure's analytics panel and AppDash's insight cards ("unused apps", "storage-heavy") are the category's stickiest discovery surfaces; NG already computes every datapoint (installer source, target SDK, signing info, usage) but offers no aggregate view with tap-through to a filtered list.
  Evidence: https://github.com/Hamza417/Inure (FEATURES.md analytics panel); https://appdash.app/ (insight cards); NG filters already support these predicates (filters/options/)
  Touches: main/ or a new dashboard fragment, filters/ (reuse predicates as tap-through), existing chart utilities
  Acceptance: a dashboard screen shows at least installer-source, targetSdk, and signing distributions plus an "unused 30/60/90 days" card; tapping any segment opens the main list pre-filtered to it.
  Complexity: M


- [ ] P3 — Boot-component manager view
  Why: A dedicated "what starts at boot" surface (BOOT_COMPLETED receivers across all apps, batch-blockable) is a classic MyAndroidTools/Inure feature NG can build almost entirely from existing component-blocking plumbing; today only NG's own BootReceiver references BOOT_COMPLETED (verified).
  Evidence: Inure boot manager (FEATURES.md); https://github.com/lihenggui/blocker (MyAndroidTools rule import demand); grep: no cross-app boot view in tree (verified)
  Touches: new view under main menu (reuse component list UI), rules/compontents/ (existing IFW/disable paths), filters/
  Acceptance: a screen lists every app with BOOT_COMPLETED/LOCKED_BOOT_COMPLETED receivers and their enable state; per-row and batch block/unblock work through the existing rule store with undo.
  Complexity: M


- [ ] P3 — Tracker report rollup: company → category → jurisdiction
  Why: TrackerControl's grouped presentation (parent company, ads/analytics/social category, HQ country) with plain-language blurbs is meaningfully more legible than flat library lists and is pure offline metadata; NG's TrackerInfoDialog already resolves tracker identity (verified) — the rollup is presentation work.
  Evidence: https://trackercontrol.org/ ; scanner/TrackerInfoDialog.java + rules/compontents/TrackerCategory.java (verified)
  Touches: scanner/ (report screen grouping), tracker metadata (extend the bundled dataset with company/category/country columns), strings (blurbs)
  Acceptance: the per-app tracker report groups findings by parent company with category chips and a one-line "what this category means"; flat list remains available as a toggle; works fully offline.
  Complexity: M

- [ ] P3 — Theme/a11y coherence pass (deferred-audit visual debt)
  Why: The 2026-06-09 audit verified divergent dark palettes across NG-added screens, dead premium design tokens, and tracker/perm badges under the 48dp touch-target minimum — small fixes that compound into perceived quality.
  Evidence: 2026-06-09 audit session record (deferred list); res/ themes and the named drawables (spot-verified)
  Touches: app/src/main/res/ (themes, drawables, dimens), details/ badge layouts
  Acceptance: NG-added screens share one dark palette token set; the misused drawables are replaced with purpose-named assets; all interactive badges hit ≥48dp touch targets (a11y scanner clean on those screens).
  Complexity: M

## Deep Audit Follow-ups (2026-06-11)

Deferred from the 2026-06-11 deep engineering/QA/UX audit pass. The fixed half
of that pass is in the commit history / CHANGELOG `Unreleased`. Device-gated
items (badge touch targets, sibling row V2 verification) moved to
`Roadmap_Blocked.md`.

## Improvement Sweep (2026-06-11)

Findings from a six-domain codebase sweep (settings/onboarding, file-manager/editor,
installer/scanner, running-apps/usage, rules/profiles/automation, CI/build/distribution).
Deduplicated against all sections above.

### P3

- [ ] P3 — Code editor: undo history browser + diff view
  Why: The editor supports undo/redo but no UI to browse history depth or see what changed. No file-version diff view for comparing current edits against the on-disk version.
  Evidence: editor/CodeEditorFragment.java:652-656 (undo/redo wired, no history UI)
  Touches: editor/ (undo history panel, simple inline diff)
  Acceptance: a toolbar action shows the undo stack; a diff toggle highlights changes vs. the saved file.
  Complexity: M

- [ ] P3 — Profile sharing via QR code or deep link
  Why: Profiles serialize to JSON but there's no QR code or `am://profile/import/<encoded>` deep link for mobile-to-mobile sharing. Users must export to file, transfer, and import manually.
  Evidence: profiles/struct/BaseProfile.java (serializeToJson exists, no QR/deep-link codec)
  Touches: profiles/ (QR encoder/decoder, deep-link handler in manifest)
  Acceptance: a "Share" action in the profile editor generates a QR code or copyable deep link; scanning/tapping it on another device opens the import flow.
  Complexity: M

## Research-Driven Additions (Pass 3 — 2026-06-13)

### P3

- [ ] P3 — Dedicated freeze surface with home-screen widget
  Why: Freeze/unfreeze works via app details or batch ops, but there is no dedicated screen listing all frozen apps with one-tap toggle — the feature is buried. Hail's frozen-apps grid with one-tap toggle and home-screen widget is the competitive standard for daily freeze/unfreeze workflows. NG ships a QS freeze tile but no in-app freeze surface or widget.
  Evidence: https://github.com/aistra0528/Hail (freeze grid, widget, grayscale icons); main/MainActivity.java (frozen filter exists but no dedicated freeze fragment); QuickFreezeTileService (QS tile only, no widget)
  Touches: new FreezeManagerFragment under main/ (reuse existing freeze/unfreeze plumbing from batchops/), new AppWidgetProvider for home-screen toggle, main menu entry, app/src/main/res/layout/ (grid layout), app/src/main/res/xml/ (widget metadata)
  Acceptance: a main-menu entry opens a grid of all frozen/suspended apps; each row has a one-tap toggle that freezes or unfreezes immediately; a home-screen widget shows frozen-app count and opens the freeze surface on tap; works in root, ADB, and Shizuku modes.
  Complexity: M

- [ ] P3 — Scheduled cache/data clearing as routine operation type
  Why: SD Maid SE's scheduled cache-clearing is the #1 feature users associate with automated Android maintenance. NG's RoutineScheduler (v0.6.0 target) already has the executor pattern for scheduled operations but does not include cache or expendable-data clearing as an operation type.
  Evidence: https://github.com/d4rken-org/sdmaid-se (scheduled cache clearing); profiles/RoutineScheduler.java and profiles/RoutineWorker.java (verified: no CLEAR_CACHE operation type); compat/PackageManagerCompat.java (freeStorageAndNotify available for privileged modes)
  Touches: profiles/ (add CLEAR_CACHE and CLEAR_DATA operation types to RoutineScheduler), compat/PackageManagerCompat.java (cache-clearing wrapper), settings/ (per-profile operation type selector)
  Acceptance: a routine can include "clear cache" or "clear expendable data" as an operation, scoped to specific apps or app-set filters; scheduled execution clears cache for matched apps and logs byte counts; requires root or Shizuku privilege; operation type cleanly refused with explanation on no-root mode.

## Research-Driven Additions

### P2




### P3


- [ ] P3 — Component intent-action finder
  Why: Users need to understand why apps start and which broadcast/action paths exist, while adjacent component managers expose intent-action filtering as a key discovery tool.
  Evidence: upstream App Manager startup-question issue; Blocker component search and intent-action filter issues; existing AppManagerNG component/rules packages.
  Touches: component list/search UI; rules/component scanner; app details component tabs; search/filter tests.
  Acceptance: users can search or filter components by intent action, category, exported state, and enabled rule state across apps, then jump directly to the component control; boot-manager work can reuse the same finder backend.
  Complexity: M


