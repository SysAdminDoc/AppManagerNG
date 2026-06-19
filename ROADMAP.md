<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Historical surfaces are archived under
`docs/roadmap/archive/`. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

All remaining items are in `Roadmap_Blocked.md` — gated on device access,
visual verification, privileged-mode testing, or external dependencies.

## Research-Driven Additions

### P1
- [ ] P1 — Privileged terminal launch hardening
  Why: Root, ADB, and Shizuku terminal routes are high-risk privileged surfaces, and the current launch gate can be bypassed when the global action-auth preference is disabled.
  Evidence: `app/src/main/java/io/github/muntashirakon/AppManager/terminal/TermActivity.java`, `app/src/main/java/io/github/muntashirakon/AppManager/misc/LabsActivity.java`, `app/src/main/java/io/github/muntashirakon/AppManager/crypto/auth/ActionAuthGate.java`, upstream AppManager issue #1738.
  Touches: `app/src/main/java/io/github/muntashirakon/AppManager/terminal/`, `app/src/main/java/io/github/muntashirakon/AppManager/crypto/auth/`, terminal/auth tests, terminal strings/layout.
  Acceptance: Opening a root, ADB, or Shizuku terminal always requires secure device credential regardless of the general action-auth toggle; local preview terminal behavior remains clearly labeled; tests prove privileged routes cannot bypass credential gating.
  Complexity: M

- [ ] P1 — Snapshot import preview and selective restore
  Why: Snapshot import is currently an all-in confirmation even though the import model already supports section flags; backup competitors and restore failure reports show users need previewable, section-aware restores.
  Evidence: `app/src/main/java/io/github/muntashirakon/AppManager/settings/PrivacyPreferences.java`, `app/src/main/java/io/github/muntashirakon/AppManager/snapshot/SnapshotBundle.java`, Neo Backup restore issues, Swift Backup restore documentation.
  Touches: `app/src/main/java/io/github/muntashirakon/AppManager/settings/PrivacyPreferences.java`, `app/src/main/java/io/github/muntashirakon/AppManager/snapshot/SnapshotBundle.java`, settings strings/layout, snapshot tests.
  Acceptance: Selecting a snapshot first shows source package/version, schema, entry counts, and selectable prefs/profiles/rules/tags/history sections; dry-run validation reports unsupported schema or size/count failures before writes; import applies only selected sections with tests for option combinations.
  Complexity: M

- [ ] P1 — Release/version consistency gate
  Why: Release trust depends on app version, README badges, fastlane metadata, release assets, and SBOM data agreeing before publish.
  Evidence: `app/build.gradle`, `README.md`, `fastlane/metadata/`, `.github/workflows/release.yml`, `scripts/generate-cyclonedx-sbom.py`, AppVerifier/source-verification ecosystem.
  Touches: `scripts/`, `.github/workflows/release.yml`, release/SBOM generation tests or fixtures.
  Acceptance: A local/CI preflight fails when `versionName`, `versionCode`, README badge, fastlane latest changelog, tag/release asset names, or SBOM version disagree; release workflow runs the check before publishing.
  Complexity: S

### P2
- [ ] P2 — Optional-network transparency ledger for full flavor
  Why: Full flavor includes opt-in VirusTotal, Pithus, debloat definition, and tracker freshness network paths, but users lack one audit surface showing what can talk to the network and what data category is involved.
  Evidence: `FeatureController.java`, `PrivacyPreferences.java`, `VirusTotal.java`, `Pithus.java`, `DebloatDefinitionsUpdater.java`, `TrackerDatabaseFreshnessChecker.java`, AppDash privacy/dashboard model, Android sideloading/developer-verification trust climate.
  Touches: full/floss feature controller, scanner clients, debloat updater, tracker freshness checker, privacy settings UI, preference storage, tests.
  Acceptance: Full flavor Settings > Privacy lists each optional network feature with compile availability, toggle state, last request/fetch time, endpoint class, payload category, and a direct action; floss flavor hides or compiles no-op ledger behavior; tests cover ledger recording and floss/full availability.
  Complexity: M

- [ ] P2 — Support bundle preview and redaction controls
  Why: Support bundles already redact sensitive data, but sharing is direct and users should be able to inspect and exclude sections before export.
  Evidence: `app/src/main/java/io/github/muntashirakon/AppManager/misc/SupportInfoBundle.java`, `TroubleshootingPreferences.java`, `ModeDoctorPreferences.java`, Android package-visibility privacy guidance, GitHub support workflows.
  Touches: `app/src/main/java/io/github/muntashirakon/AppManager/misc/SupportInfoBundle.java`, troubleshooting/mode-doctor settings UI, strings, support bundle tests.
  Acceptance: Support export opens a preview with section toggles for device, privilege state, feature flags, crash sink, and logcat; UI shows final size and redaction explanation; tests prove excluded sections are absent and redaction still applies.
  Complexity: S/M

- [ ] P2 — Android 16 strict-intent manifest audit gate
  Why: Android 16 safer-intent behavior makes exported components and deep links a release-hardening surface; future broad filters should fail CI before they ship.
  Evidence: Android 16 behavior changes, `app/src/main/AndroidManifest.xml`, `AutomationUriActivity`, `AppDetailsActivity` intent filters, prior changelog fixes for deep-link filter splits.
  Touches: manifest/static tests, automation URI parser tests, app-detail deep-link tests, CI test task.
  Acceptance: Static tests enumerate exported components and intent filters, assert exact action/data/scheme/host constraints for public AppManagerNG APIs, reject broad filters missing parser validation, and cover automation/app-detail deep-link examples.
  Complexity: M

- [ ] P2 — Dependency floor and security-tool drift gate
  Why: The project intentionally holds minSdk 21 while dependency lines and security tooling continue moving; accidental minSdk-23 dependency upgrades or stale dependency-check tooling should be visible before merge.
  Evidence: `versions.gradle`, `docs/policy/minsdk-21-ceiling.md`, Material Components release notes, Room release notes, WorkManager release notes, OWASP Dependency-Check plugin release history.
  Touches: `versions.gradle`, dependency policy docs during implementation, Gradle task or script, CI workflow.
  Acceptance: CI emits a machine-readable report of dependency minSdk floor blockers and security-tool drift; the check fails if a pinned dependency violates the minSdk-21 ledger or if dependency-check tooling exceeds the documented allowed drift window without an explicit roadmap/policy acknowledgement.
  Complexity: S/M

### P3
- [ ] P3 — Translation and pseudolocale quality gate
  Why: AppManagerNG has broad string coverage and pseudolocale support, but no visible gate preventing stale translations, clipped pseudo text, or untranslated high-risk flows from regressing.
  Evidence: `app/src/main/res/values*/`, `pseudoLocalesEnabled true` in `app/build.gradle`, Canta and Hail community translation practices.
  Touches: resource lint script or Gradle task, representative screenshots/tests for top settings/detail/dialog flows, strings policy docs during implementation.
  Acceptance: A local/CI check reports missing or stale translated strings, exercises pseudolocale for representative high-risk flows, and fails only on source-string regressions that can be fixed without needing new translator input.
  Complexity: M
