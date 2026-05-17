<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-17 pass 2

Forward pointer for iter-26 (or whatever the next walk-away session is called).

---

## 1. Current state

The 2026-05-17 pass-2 session completed its full scope. Specifically:

- **3 source-code fix commits** landed (Finder regex, InstallTranscript URI redactor, OnboardingFragment race)
- **`docs/architecture/`** stood up with 3 docs + README (closes ROADMAP T11)
- **ROADMAP** Iter-25 section + S321-S325 added
- **CHANGELOG** Unreleased gained 4 new entries
- **`.ai/research/2026-05-17-pass-2/`** has 11 artifacts (this dir)

Branch state: 20 commits ahead of `origin/main`. Working tree clean except for the
iter-25 deliverables commit being staged.

No hard limits were hit. This file exists as a forward pointer rather than as a
mid-session continuation marker.

---

## 2. What the next session should do — in priority order

### Priority 1 — Shizuku Android-17 audit (F-NEW-25-01)

**Why first**: The single new external risk surfaced today. Android 17 stable lands
**June 2026** to Pixel devices. NG's Shizuku integration (shipped 2026-05-14) reaches
through the binder Shizuku #1965/#1967 reports broken on A17 Beta 3. Failing to land
before June means Pixel users lose NG's Shizuku mode the day they take the OTA.

**Concrete plan**:

1. Set up an Android 17 Beta image — Pixel 9 emulator (most accessible) or device per ROADMAP iter-19 [S148] device matrix.
2. Install Shizuku Manager 13.6.0 and NG (latest v0.4.2 + the three iter-25 fixes from this session).
3. Reproduce Shizuku #1965/#1967 — open Shizuku Manager → Application Management; verify it's blank.
4. With Shizuku still authorized, launch NG → Settings → Mode of Operation → Shizuku. Document what NG does in that state (probable: silent binder failure, generic error toast, or partial enumeration).
5. Read `app/src/main/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridge.java` and trace where the binder call fails. Likely candidates: `Shizuku.bindUserService()`, `IPackageManager` reflective lookups via the LocalServer bridge, or the AppOps enumeration path that pulls every-installed-package metadata.
6. Audit verdict to `docs/audits/<YYYY-MM-DD>-shizuku-android17-compat.md`:
   - **`confirmed, needs-design`** if there's no quick fix in NG code (most likely outcome — the regression is on Shizuku's side).
   - **`remediated`** if a runtime SDK-int-37 detection + onboarding-warning + ADB-mode-recommendation lands the same session.
7. Implementation if remediating: gate the Shizuku mode in onboarding when `Build.VERSION.SDK_INT >= 37 && shizukuPackageVersionCode < <fix-version>`; surface a warning that recommends switching to ADB mode; document in the capability matrix.
8. CHANGELOG entry: `Compliance — Shizuku Android-17 compatibility detection + fallback (2026-MM-DD)`.

**Estimated effort**: 3-5 hours including device setup, audit write, and the runtime detection patch.

### Priority 2 — Android 17 targetSdk=37 sub-audit batch (F-NEW-25-02)

**Why second**: Sized for the v0.7.x window. Five sub-audits, each ~2 hours. Aim to land
incrementally over the next 3-4 iterations so they're all done before NG bumps
`targetSdk = 37` after Pixel A17 rollout in June 2026.

The audits (in suggested order, easiest first):

1. **`docs/audits/<date>-android17-ml-dsa-keystore-oid.md`** — OID-to-display-name mapping in `CertUtils`; one fix at most, no behavior change beyond display.
2. **`docs/audits/<date>-android17-cleartext-traffic-enforcement.md`** — verify `network_security_config.xml` is wired; verify zero `http://` in manifests.
3. **`docs/audits/<date>-android17-access-local-network.md`** — declare the runtime permission if any LAN traffic (wireless ADB pair discovery); audit `libadb-android` for LAN egress.
4. **`docs/audits/<date>-android17-bal-intentsender.md`** — `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` → `_ALLOW_IF_VISIBLE` migration in all `ActivityOptions` usages.
5. **`docs/audits/<date>-android17-ech-default-on.md`** — verify no `<domainEncryption>` opt-out is needed; check that wireless ADB pair / LAN discovery is unaffected.

Each follows [`docs/audits/README.md`](../../../docs/audits/README.md). Verdicts go into
ROADMAP's Engineering Debt Register §"Android 17 targetSdk=37 compliance batch".

### Priority 3 — F-NEW-09 minSdk cascade analysis

Add a `## Cascade analysis` sub-section to
[`docs/policy/minsdk-21-ceiling.md`](../../../docs/policy/minsdk-21-ceiling.md):

```md
## Cascade analysis

If `min_sdk = 23` is taken (Material Components 1.14.0 ceiling driver), the following
dep lines can move in lockstep:

- `activity` 1.11.0 → 1.12.x (drops API 21-22)
- `biometric` 1.4.0-alpha04 → 1.4.0+ (drops API 21-22)
- `room` 2.7.2 → 2.8.x (drops API 21-22)
- `webkit` 1.14.0 → 1.15.x (drops API 21-22)
- `material` 1.13.0 → 1.14.0 (raises minSdk to 23)

Decision pressure: Material 1.14.0 is still alpha (alpha06/07/08 as of 2026-05-17 — see
ROADMAP [S325]). The ceiling can stay deferred until the 1.14.0 line goes stable;
project the timing as Q3-Q4 2026 based on Material's historic alpha → stable cadence.
```

Effort: ~30 minutes.

### Priority 4 — F-NEW-11 JaCoCo coverage badge

Add to `tests.yml`:

```yaml
- name: Coverage report
  run: ./gradlew :app:jacocoTestReport

- name: Upload coverage to Codecov
  uses: codecov/codecov-action@v4
```

Wire JaCoCo into `app/build.gradle` if not already present. Effort: ~1 hour.

### Priority 5 — F-NEW-13 Markdown link checker

Add a `lychee` step to `lint.yml`:

```yaml
- name: Markdown link check
  uses: lycheeverse/lychee-action@v1
  with:
    args: --no-progress --offline 'PROJECT_CONTEXT.md' 'docs/architecture/*.md' 'ROADMAP.md'
```

Effort: ~30 minutes.

---

## 3. Things explicitly **not** to do in iter-26

- **Do not re-mine the competitor surface**. Pass-2 spot-checked the top-6 active competitors; pass-1 mined all 22+ entries in COMPETITOR_MATRIX. Velocity is now low enough that monthly re-checks suffice; weekly is wasted effort.
- **Do not start an architecture refactor based on the new `docs/architecture/` docs.** The docs *describe* the existing surface; refactor work needs its own ROADMAP entry, design phase, and effort estimate.
- **Do not bump `targetSdk = 37` before the 5 sub-audits are complete and verdicted.** This is the load-bearing constraint for the audit doctrine.
- **Do not bump `min_sdk = 23`** until Material 1.14.0 ships stable. The minSdk-21 ceiling is the decision pressure; without a forced bump from a stable dep, the user-base trade-off doesn't pencil.
- **Do not push to `origin/main` from this VM.** Per `swiftfloris-git-auth.md` the auth fails 403; the maintainer pushes from another machine.

---

## 4. Reading the prior session

If you're an AI agent starting iter-26:

1. **Read `PROJECT_CONTEXT.md` first** (it's the canonical entry-point).
2. **Read `ROADMAP.md` §"Iter-25 Research Additions (2026-05-17)"** for what just landed.
3. **Read `CHANGELOG.md` §"Unreleased"** for the four 2026-05-17 entries.
4. **Skim `.ai/research/2026-05-17-pass-2/CHANGESET_SUMMARY.md`** for the deliverables map.
5. **Read `docs/architecture/README.md`** to understand the new architecture-docs surface.
6. **Read `docs/audits/README.md`** for the audit-doc doctrine.
7. **Then start on Priority 1** above.

Total prep time: ~10 minutes. Same as pass-2's prep time off pass-1.

---

## 5. End state

This session shipped the highest-leverage work that was queueable:

- **3 commit-ready in-progress fixes** (Finder regex, installer URI redactor, onboarding race).
- **4 architecture docs** (closes ROADMAP T11 — the longest-standing open documentation row).
- **ROADMAP iter-25 + 5 new sources** (Shizuku A17, Neo-Backup 8.3.18, Android 17 stable, Material 1.14 alpha + Source-Register self-reference).
- **CHANGELOG** Unreleased extended with 4 entries.
- **11 pass-2 research artifacts** documenting the execution.

The single new external risk (Shizuku A17) is captured with an action plan. The
five-audit Android-17 batch has a clear sizing and sequence. Two CI / docs hygiene
items are scoped for iter-26.

Total session: ~30 KB of new Markdown plus 3 source-code commits.
