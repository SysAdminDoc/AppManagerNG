<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# Blocked Roadmap Items

Items moved here from ROADMAP.md because they cannot be completed without
device access, external services, or explicit design decisions. Move back to
ROADMAP.md once the blocker is resolved.

## Device/Design-Verification-Gated

### P2

- [ ] P2 — Accessibility, keyboard, and touch-target hardening
  Why: dense expert tools still need predictable focus, visible labels, 48dp controls, and
  non-color-only status meaning across dialogs, lists, chips, menus, and result screens.
  Where: app/src/main/res/layout/, app/src/main/java/io/github/muntashirakon/AppManager/
  Blocker: requires on-device a11y scanner, TalkBack, and keyboard navigation testing to identify specific violations before fixing.
  Complexity: M

- [ ] P2 — Degraded, empty, error, loading, and success state system
  Why: secondary screens should never fail silently or show blank states; every unavailable,
  partial, or failed workflow needs calm actionable copy and an obvious next step.
  Where: app/src/main/java/io/github/muntashirakon/AppManager/, app/src/main/res/layout/
  Blocker: requires systematic on-device walkthrough of each screen's error/empty/loading paths to identify gaps.
  Complexity: L

- [ ] P2 — Settings information architecture cleanup
  Why: settings should group risk, privileges, appearance, backup, rules, notifications, and
  advanced/debug controls so users can find decisions without memorizing implementation
  boundaries.
  Where: app/src/main/java/io/github/muntashirakon/AppManager/settings/
  Blocker: current 4-category structure is reasonable; needs UX review on device to identify specific reorganization targets.
  Complexity: M

### P3

- [ ] P3 — Clickable main-list badges have <48dp touch targets
  Why: `tracker_indicator` and `perm_indicator` are clickable (`setClickable(true)` + click listener) but render at `premium_badge_min_height/width` = 24dp with no TouchDelegate. Two clickable badges share one `FlowLayout` parent, so a single `TouchDelegate` (one target rect per view) can't cover both.
  Evidence: MainRecyclerAdapter.java:420-422,457-459; item_main_v2.xml:115-116,136-137; dimens-v2.xml:76-77
  Touches: MainRecyclerAdapter.java (composite touch delegate on the badge row), item_main_v2.xml
  Acceptance: each clickable badge has a >=48dp effective hit rect via a composite/multi-target TouchDelegate posted on the parent FlowLayout (visual size stays 24dp); a11y scanner clean. (Needs on-device touch verification.)
  Progress 2026-06-12: fixed the nested coordinate translation bug in the
  composite badge TouchDelegate, added a Robolectric regression test, installed
  the Floss debug build on the emulator, and captured the main-list badge
  surface. Remaining: clean a11y scanner confirmation after the emulator
  UiAutomation service recovers.
  Blocker: emulator UiAutomation service recovery needed for a11y scanner confirmation.
  Complexity: S

- [ ] P3 — Sibling list rows diverge from the V2 card treatment
  Why: ~15 list-row layouts (e.g. item_debloater.xml) still use the classic `Widget.AppTheme.CardView.ListItem.Outlined` (bg `?colorSurface`, elevation 0) while the main list uses `Widget.AppTheme.V2.Card.ListRow` (bg `?colorSurfaceContainerLow`, 1dp elevation, hairline stroke), so adjacent NG screens render visibly different card surfaces.
  Evidence: layout/item_debloater.xml:4 (+ ~14 sibling row layouts); themes-v2.xml V2.Card.ListRow
  Touches: app/src/main/res/layout/item_*.xml
  Progress 2026-06-12: migrated the shared sibling list rows, scanner cards,
  profile-review warnings, empty states, batch failure rows, and secondary
  toolbars onto V2 premium tokens; dark-mode emulator screenshots pass for the
  mode sheet, onboarding guides, and populated main list.
  Acceptance: NG list rows share the V2 card treatment; spot-checked across debloater / permission / one-click lists in light, dark and AMOLED. (Visual -- needs on-device verification.)
  Blocker: on-device visual verification required across light/dark/AMOLED.
  Complexity: S

- [ ] P3 — D-pad/TV navigation pass + Android TV banner
  Why: Upstream #107 (keyboard/remote navigation, "Partly Fixed") plus SD Maid SE's Android TV launcher support show the box-tinkerer segment is real (FireOS/Firestick issues already appear upstream: #1835, #1854); NG's M3 dashboard was not audited for focus traversal.
  Evidence: https://github.com/MuntashirAkon/AppManager/issues/107 ; SD Maid SE releases (TV support); upstream #1835/#1854 (FireOS users)
  Touches: app/src/main/res/ (focus order, leanback banner, manifest LEANBACK feature flags), main/, details/
  Acceptance: main list -> app details -> batch ops are fully operable with a D-pad on an Android TV emulator; app appears in the TV launcher with a banner.
  Blocker: requires Android TV emulator or device for focus-traversal testing and banner validation.
  Complexity: M

- [ ] P2 — Multi-user/work-profile/private-space capability matrix
  Why: AppManagerNG has broad userId plumbing and hidden-profile permission support, but users need one visible source of truth for which operations work in main, work, hidden, and private profiles under each privilege mode.
  Evidence: app/src/main/AndroidManifest.xml; docs/policy/permissions.md; Canta work-profile issue; Neo Backup multi-profile issue.
  Blocker: requires multi-user/work-profile device or emulator to test capability detection and action guards across profile boundaries.
  Complexity: L

- [ ] P2 — File-manager operations service with cancellation and recovery
  Why: Copy, move, delete, and archive work is still tied to fragment/background-thread flows even though the code calls out a bound service and cancellable thread ownership as needed.
  Evidence: fm/FmFragment.java; fm/FmAdapter.java.
  Blocker: foreground service behavior, rotation survival, and partial-move recovery need device testing; large architectural change touching the FM core.
  Complexity: L

## Security-Design-Gated

### P1

- [ ] P1 — Privileged local-server secure-session hardening
  Why: The privileged server uses a cleartext socket authenticated by a per-session token, while the code itself notes SSL as a future hardening path and Android 17/local ADB changes raise the cost of ambiguous local transport boundaries.
  Evidence: `app/src/main/java/io/github/muntashirakon/AppManager/servermanager/LocalServerManager.java`; `app/src/main/java/io/github/muntashirakon/AppManager/servermanager/ServerConfig.java`; `docs/security-advisories/2026-05-08-cve-2026-0073-adb-mode.md`; Android 17 local-network behavior docs; Shizuku limitation docs.
  Touches: local server client/server handshake; `ServerConfig`; server connection tests; security docs.
  Acceptance: the trust model is explicit; loopback-only sessions are enforced where possible or non-loopback sessions use per-session authenticated encryption/TLS; wrong host, wrong token, and replayed session attempts are rejected by tests.
  Blocker: requires explicit trust-model design decisions and touches native server bootstrap; device-gated for privileged-mode testing.
  Complexity: L

## External-Service-Gated

### P3

- [ ] P3 — Version-watch panel (full flavor): installed vs latest from static indexes
  Why: APKUpdater (3.8k stars, active) proves demand for multi-source update awareness without being a store; AppDash paywalls it; checking F-Droid/IzzyOnDroid index-v2 + GitHub releases against installed versions fits the full flavor's opt-in network doctrine and NG stays a manager (notify, don't install).
  Evidence: https://github.com/rumboalla/apkupdater ; https://appdash.app/ ; f-droid index-v2 format (RESEARCH.md Sources)
  Touches: full-flavor source set (new updates/ package), settings/PrivacyPreferences (opt-in + source toggles), WorkManager scheduled check
  Acceptance: with the toggle on, a scheduled check lists apps whose installed version trails the chosen indexes, with a signing-cert mismatch warning where the index cert differs; floss flavor compiles the feature out entirely.
  Blocker: requires network access to F-Droid/IzzyOnDroid index-v2 and GitHub release APIs for testing.
  Complexity: L
