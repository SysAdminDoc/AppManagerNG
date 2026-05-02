<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Android Power-Tool Competitive Research

Date: 2026-05-02

Scope: AppManagerNG compared against current Android power-user utilities: app managers, debloaters, app freezers, backup tools, permission/AppOps tools, profile isolation tools, tracker blockers, cleaners, and release-verification tools.

## Executive Take

AppManagerNG already has the broadest local engine: package inspection, AppOps, component blocking, trackers, backups, logcat, file manager, terminal, profiles, installer, debloater, and root/ADB operations. The weakness is not capability count. The weakness is that competing apps turn narrower capability into clearer jobs: "debloat safely", "freeze distractions", "isolate risky apps", "prove who is tracking me", "clean storage", "restore my phone", "verify this APK".

The path to making AppManagerNG more powerful and useful is to make it an operation cockpit:

1. Every privileged action gets a safety model, reversibility plan, and operation log.
2. Every app gets a trust and risk summary that merges permissions, AppOps, trackers, backup status, signing state, source, and network evidence.
3. Every repetitive workflow becomes a profile, intent, shortcut, tile, or schedule.
4. Root, Shizuku, ADB, and unprivileged modes are shown as capabilities, not errors.
5. Community-maintained metadata turns raw package lists into decisions.

## Comparator Map

### Canta

Source: https://samolego.github.io/Canta/ and https://github.com/samolego/Canta

What matters:

- Rootless debloat is framed as safe removal through Shizuku.
- System apps are not actually erased from the device; the user-facing message is that recovery is possible by factory reset.
- Community badges and descriptions help users decide what to remove.
- Presets can be created, shared, and applied across devices.

Implication for AppManagerNG:

- Debloater needs a trust layer, not just action buttons. Add safety classes, OEM notes, dependency warnings, undo command, profile/user impact, and a "why this is here" description.
- Debloat presets should be first-class profile artifacts.
- Shizuku debloat should land as a flagship feature, not a settings footnote.

### Universal Android Debloater

Source: https://github.com/0x192/universal-android-debloater

What matters:

- UAD wins because of package documentation, OEM lists, multi-user support, import/export of selections, and action logging.
- Its README explicitly frames privacy, battery, security, and attack-surface reduction as the outcome.
- It covers Google/Facebook/Amazon/Microsoft, AOSP, OEM, carrier, Qualcomm/MediaTek, and miscellaneous package families.

Implication for AppManagerNG:

- Import the UAD data model concept: package metadata by family, vendor, recommendation level, dependency risk, and restore guidance.
- Add "Review plan" before applying debloat batches.
- Add "Export plan" and "Import plan" so users can reuse decisions across phones.
- Make multi-user/profile scope explicit before removal or freeze.

### Hail

Source: https://github.com/aistra0528/Hail

What matters:

- Hail separates freeze methods: disable, hide, suspend, and uninstall/reinstall system apps.
- It publishes a capability matrix for root, device owner, privileged system app, Shizuku root/Sui, Shizuku ADB, Dhizuku, and Insular.
- It exposes automation APIs and deeplinks for launch, freeze, unfreeze, freeze tag, unfreeze tag, freeze all, freeze non-whitelisted, auto-freeze, lock, and lock-freeze.

Implication for AppManagerNG:

- AppManagerNG should expose the same kind of capability matrix in onboarding and per-operation sheets.
- Freeze should be a family of strategies with visible side effects, not a single verb.
- Profiles need public automation intents, launcher shortcuts, and Quick Settings tiles.
- Tags should drive operations: freeze tag, backup tag, revoke tag, block trackers for tag.

### Inure

Source: https://github.com/Hamza417/Inure

What matters:

- Inure competes on breadth plus polish: app manager, component scanning, terminal, usage stats, split/APK installer, root/Shizuku support, custom theme engine, crash handler, image renderer, animations, reproducible builds.
- It treats accessibility as a feature set: alternate color modes, clickable-element highlighting, dividers, reduced animation controls, screen-reader consideration.
- It marks FOSS apps by F-Droid availability or app manifest metadata.
- Its menu/dialog taxonomy is intentional: popup quick actions near thumb, modal panel preferences, chip dialogs for filters/tags.

Implication for AppManagerNG:

- Polish must include accessibility controls, not only color and spacing.
- Add a "FOSS/source" trust badge to app details: F-Droid presence, license metadata, source URL metadata when available, installer source, signing certificate continuity.
- Avoid one generic overflow menu. Use action surfaces by job: quick app actions, risk operations, filters/tags, and deep technical details.

### SD Maid SE

Source: https://f-droid.org/en/packages/eu.darken.sdmse/

What matters:

- SD Maid SE owns the "storage hygiene" job: corpse finder, system cleaner, app cleaner, deduplicator, swiper review, media compression, and storage analysis.
- It uses AccessibilityService to automate tedious cache deletion while explicitly stating that it does not collect information through that service.
- F-Droid lists its privileges: full storage management, usage stats, notification, query-all-packages, cache clearing, run at startup, delete packages, keep awake, secure settings, and Shizuku API permission.

Implication for AppManagerNG:

- AppManagerNG does not need to become a cleaner, but it should surface app leftovers and cache pressure where package management decisions happen.
- Add "after uninstall cleanup" and "backup-before-clean" flows.
- Add a storage impact panel in app details: app size, data, cache, backup size, leftovers, duplicate APKs, OBB/media remnants.
- If AccessibilityService automation is added, it needs an explicit privacy explainer and opt-in scope.

### Shelter

Source: https://github.com/PeterCxy/Shelter

What matters:

- Shelter makes Android Work Profile usable for isolation, cloning, and freezing apps inside the profile.
- It is deliberately constrained to Work Profile APIs and is effectively in maintenance mode because it avoids ADB privilege expansion.

Implication for AppManagerNG:

- AppManagerNG can be more useful than Shelter by becoming profile-aware without trying to be a work-profile owner by default.
- Add cross-profile visibility: show whether an app exists in owner, work profile, secondary users, and clone spaces.
- Add "install/restore to user/profile", "freeze in profile", and "copy APK to profile" where supported by existing privilege paths.
- Do not silently attempt device-owner flows; they have high lock-in risk.

### TrackerControl

Source: https://github.com/TrackerControl/tracker-control-android

What matters:

- TrackerControl combines static tracker-library analysis with runtime network evidence.
- It uses blocklists from Disconnect, DuckDuckGo Tracker Radar, X-Ray, and an in-house analysis set.
- It explains companies, purposes, and categories such as analytics or advertising.
- It offers blocking modes: Minimal, Standard, Strict, with compatibility tradeoffs and per-tracker control.
- It is explicit that no tracker result is perfect.

Implication for AppManagerNG:

- AppManagerNG already has tracker scanning and component blocking. It should add better risk explanation and evidence.
- Add a privacy evidence panel: static tracker SDKs, enabled tracker components, recently contacted tracker domains when data is available, block state, and known breakage risk.
- Avoid adding a VPN blocker immediately. A local VPN module would be powerful but changes the app's purpose, background behavior, battery profile, and support burden.
- Better short-term path: integrate static tracker lists, optional import of TrackerControl/Exodus-style metadata, and one-click component/AppOps blocking.

### Neo Backup

Source: https://github.com/NeoApplications/Neo-Backup

What matters:

- Neo Backup is narrowly positioned as Android's backup solution.
- It supports root backup of individual apps and data, batch backup/restore, custom lists, scheduled backups, and unlimited schedules.

Implication for AppManagerNG:

- Backup should stop being "one more app action" and become a reliability system.
- Add schedule builder, custom backup sets, retention policy, integrity verification, preflight storage check, per-app restore simulation, and rich completion logs.
- Show backup freshness in the app list and app details as a primary health signal.

### Permission Manager X

Source: https://f-droid.org/en/packages/com.mirfatif.permissionmanagerx/ and https://mirfatif.github.io/PermissionManagerX/help

What matters:

- Permission Manager X focuses on permissions and AppOps instead of general app management.
- Its appeal is watch/review/control: users can inspect permission state, AppOps state, and unwanted changes.
- It needs elevated modes such as root or ADB for full editing.

Implication for AppManagerNG:

- AppManagerNG can own this job by connecting permissions, AppOps, trackers, components, and operation history in one place.
- Add permission drift monitoring: "this app gained a dangerous permission since last scan", "this AppOp changed from ignored to allowed", "new receiver exported after update".
- Add one-tap rollback of permission/AppOps bundles.

### Shizuku Ecosystem

Source: https://github.com/RikkaApps/Shizuku and https://github.com/timschneeb/awesome-shizuku

What matters:

- Shizuku is now the standard rootless privilege bridge for power-user Android apps.
- The ecosystem is broad: debloaters, firewalls, AppOps managers, installers, theme patchers, volume tools, and sensors managers.

Implication for AppManagerNG:

- Shizuku integration is existential for rootless usefulness.
- The privilege provider should not be treated as a feature. It should be a platform layer shared by debloat, AppOps, install, freeze, profiles, cache operations, and diagnostics.

### Obtainium and AppVerifier

Sources: https://github.com/ImranR98/Obtainium and https://github.com/soupslurpr/AppVerifier

What matters:

- Obtainium makes source-based Android updates normal for users who avoid stores.
- AppVerifier reinforces certificate and APK trust.

Implication for AppManagerNG:

- AppManagerNG should show update provenance and signing continuity for installed APKs.
- Add app-detail trust signals: installer package, signing cert fingerprint, signature history, source channel, F-Droid/Izzy/GitHub presence, and "signature changed since last scan".
- Integrate with AppVerifier by documenting and deep-linking rather than bundling verification logic first.

## Recommended Product Bets

### 1. Capability-First Onboarding and Operation Sheets

Problem:

Users do not understand which operations require root, Shizuku, ADB, device owner, or regular permissions until the operation fails.

Build:

- A device capability model: unprivileged, usage access, notification access, all-files access, accessibility, ADB, wireless ADB, Shizuku ADB, Shizuku root, libsu root, device owner, work profile owner.
- A visible "available now" matrix in onboarding and Settings.
- Per-operation preflight sheets: required capability, current state, setup action, risk level, rollback.
- "Fix access" CTA instead of disabled buttons.

Why it matters:

This converts AppManagerNG from a tool that errors into a guide that tells users how to unlock power safely.

### 2. Safe Debloat Studio

Problem:

Current debloat workflows can expose dangerous actions without enough context.

Build:

- UAD/Canta-style package metadata: recommendation, vendor, family, component role, known breakage, restore command, affected profiles/users.
- Debloat plan review: operations grouped by package family and risk.
- Presets: Privacy, Minimal OEM, Gaming, Battery, Google-lite, carrier cleanup.
- Dry-run and export/import plan.
- Post-operation restore center.

Why it matters:

Debloat is one of the highest-demand power-user jobs. Safety metadata turns it from risky package deletion into a repeatable workflow.

### 3. App Trust and Risk Dashboard

Problem:

App details are rich but fragmented. Users need a single answer: should I trust this app, restrict it, back it up, or remove it?

Build:

- Trust score is not necessary. Use evidence chips instead:
  - dangerous permissions granted
  - AppOps with sensitive access
  - tracker SDKs and enabled tracker components
  - known tracker domains if imported or observed
  - installer source
  - signing cert continuity
  - backup freshness
  - open-source/F-Droid/license signal
  - background/run-at-boot components
  - network policy state
- Add "recommended actions" with explicit rationale.

Why it matters:

This uses the app's existing deep inspection capabilities and makes them actionable.

### 4. Operation Journal and Rollback Center

Problem:

Power tools become trusted when every change is visible and reversible.

Build:

- Persist every privileged operation:
  - timestamp
  - actor/mode: root, ADB, Shizuku, UI
  - target package/user/profile
  - exact command or API action
  - before/after state
  - rollback action if available
  - success/failure output
- Add filters by package, operation type, date, privilege mode.
- Add "undo last operation" when safe.

Why it matters:

UAD's action logging is a key trust pattern. AppManagerNG can do this better because it already controls many operation types in-app.

### 5. Automation as a Public Contract

Problem:

Users want repeatable phone-management routines, not repeated manual taps.

Build:

- Public intents and deep links for:
  - freeze/unfreeze app
  - freeze tag
  - backup tag
  - revoke permission preset
  - block tracker components
  - clear cache for set
  - run profile
- Quick Settings tiles:
  - freeze selected tag
  - run one-click ops
  - start/stop foreground tracker monitor
  - backup now
- Launcher shortcuts for saved operations.
- Tasker-compatible extras and result broadcasts.

Why it matters:

Hail proves public automation makes a focused tool sticky. AppManagerNG should expose its broader engine the same way.

### 6. Backup Reliability Upgrade

Problem:

Backup is critical, but users trust backup tools only when they can schedule, verify, and restore predictably.

Build:

- Schedules with charging/network/idle constraints.
- Named backup sets and tags.
- Retention: keep last N, keep within age, protect pinned.
- Preflight storage estimate.
- Integrity hashing at creation and before restore.
- Restore dry-run: APK exists, split compatibility, data archive readable, version mismatch warning.
- Rich notifications and post-run report.

Why it matters:

Neo Backup's value is reliability, not just root file copy. AppManagerNG already has backup infrastructure; reliability polish can make it a daily tool.

### 7. Profile and Multi-User Awareness

Problem:

Android users increasingly have work profiles, cloned apps, secondary users, and OEM private spaces.

Build:

- Main list grouped by user/profile with package presence matrix.
- App details show owner user, work profile, secondary users, clones.
- Batch operations require explicit scope selection.
- Export/import selections preserve user/profile scope.
- Where privileges allow it: install existing app for user, uninstall for user, freeze in user, backup per user.

Why it matters:

UAD and Shelter both show that profile scope is a real user need. AppManagerNG has the technical depth to make it understandable.

### 8. Permission and AppOps Drift Monitoring

Problem:

Users want to know what changed after app updates.

Build:

- Snapshot permissions, AppOps, components, signing cert, trackers, installer source after scan/update.
- Diff after package change:
  - new dangerous permission
  - newly granted permission
  - AppOp mode changed
  - new exported component
  - new tracker SDK
  - signing cert changed
- Notify only when severity threshold is met.
- Offer rollback preset for permission/AppOps changes.

Why it matters:

This is more useful than static inspection because it watches for trust regressions.

### 9. Storage and Leftover Cleanup Adjacent to App Management

Problem:

After uninstall, backup, restore, and app cache growth, users need cleanup decisions in the same context.

Build:

- App details storage panel: APK, splits, data, cache, external data, OBB, backups, leftovers.
- "Safe cleanup after uninstall" flow.
- Backup duplicate finder: same package/version repeated across backup roots.
- APK duplicate finder: same signing cert/package/version in Downloads and backups.
- Cache batch operations with clear warnings about Android restrictions.

Why it matters:

SD Maid owns storage hygiene. AppManagerNG should not clone it, but package-aware cleanup is a natural extension.

### 10. Finder as Command Palette

Problem:

The app has many tools; discovery is the bottleneck.

Build:

- Global search across:
  - apps
  - components
  - permissions
  - AppOps
  - trackers
  - backups
  - logs
  - files
  - settings
  - operations
- Search results include immediate actions with capability preflight.
- Regex/prefix filters for expert mode.

Why it matters:

Power scales only when users can find the right object and action quickly.

### 11. FOSS, Source, and Signature Trust Signals

Problem:

Power users care where an APK came from and whether updates are authentic.

Build:

- Store first-seen signing certificate per package.
- Alert on cert changes.
- Show installer package and install/update timestamp.
- Detect F-Droid/Izzy/known GitHub source where possible.
- Support manifest metadata such as `open_source` / `open_source_license`.
- Deep-link to AppVerifier guidance for manual verification.

Why it matters:

This is high-trust, low-risk, and fits AppManagerNG's identity as a package manager.

### 12. Accessibility and Motion Controls for Power UI

Problem:

Dense power-user apps become inaccessible quickly.

Build:

- Reduced motion setting independent of system animations.
- High-contrast state colors.
- Clickable element highlighting.
- Optional separators in dense lists.
- TalkBack grouping for app rows and operation sheets.
- Font-scale stress pass at 200%.

Why it matters:

Inure treats accessibility as part of power. AppManagerNG should do the same, especially with dense app lists and destructive operations.

## Highest-Leverage Roadmap Changes

Move these up:

1. Shizuku privilege provider.
2. Operation journal and rollback center.
3. Safe Debloat Studio with UAD/Canta metadata.
4. Automation intents, tiles, and shortcuts.
5. App trust/risk dashboard.
6. Backup scheduling, retention, and integrity checks.
7. Multi-user/profile scope UI.
8. Permission/AppOps drift monitoring.

Defer these:

1. Full VPN tracker blocker. It changes app purpose and creates compatibility/support burden.
2. General-purpose cleaner clone. Keep cleanup package-aware.
3. Device-owner enrollment by default. Too much lock-in risk.
4. New third-party dependencies for metadata before the local data model is stable.

## Suggested Delivery Sequence

### v0.4.x: Explain and Log

- Capability wizard.
- Operation journal schema and UI.
- Per-operation preflight sheets for debloat/freeze/AppOps/permissions.
- App details trust/risk header.

### v0.5.x: Make Decisions Safer

- Debloat metadata import format.
- Plan review/export/import.
- Restore center.
- Signing certificate continuity alerts.
- Permission/AppOps snapshot diff.

### v0.6.x: Rootless Power

- Shizuku provider.
- Rootless debloat.
- Rootless AppOps/permission paths where supported.
- Wireless ADB pairing guide.

### v0.7.x: Automation and Reliability

- Public intents/deeplinks.
- Quick Settings tiles.
- Backup schedules/retention/integrity.
- Saved operation profiles.

### v0.8.x: Cross-Profile and Search

- Multi-user/profile matrix.
- Finder as command palette.
- Storage/leftover package-aware cleanup.
- FOSS/source trust badges.

## Implementation Notes

- Keep all new features behind capability checks; no button should fail because the app already knew the required privilege was missing.
- Preserve Android Views and Material Components; these are product and architecture features, not a UI rewrite.
- Store metadata locally first. Remote community updates should be optional, signed, and cacheable.
- Every destructive operation should write an operation journal entry before and after execution.
- Every batch operation should have dry-run, plan review, and export.
- Every external-source integration must be GPL-compatible and distro-safe.
