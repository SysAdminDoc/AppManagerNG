<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-17 pass 3

Forward pointer for iter-27 / pass-4.

---

## 1. Current state

The 2026-05-17 walk-away session is at **end of pass 3**. Cumulative output:

- **PROJECT_CONTEXT.md** at repo root (canonical entry point) — pass-1
- **`.ai/research/2026-05-17/`** (11 files) — pass-1 audit trail
- **`.ai/research/2026-05-17-pass-2/`** (11 files) — pass-2 audit trail
- **`.ai/research/2026-05-17-pass-3/`** (this dir) — pass-3 audit trail
- **`docs/architecture/`** (3 docs + README) — pass-2; closes ROADMAP T11
- **`docs/audits/`** at 20 verdicts (14 pre-existing + 6 from pass-3)
- **`docs/audits/README.md`** — pass-1
- **`docs/policy/jacoco-coverage-rollout.md`** — pass-3
- **`docs/policy/minsdk-21-ceiling.md`** — extended in pass-3 with cascade analysis
- **`.github/workflows/docs-link-check.yml`** — pass-3
- **6 new commits** (4 docs commits + 3 source fixes; net 22 commits ahead of `origin/main`)

The Android 17 targetSdk=37 audit batch is **closed** — NG ships zero source-side
compliance work for the platform-stable bump in June 2026. Engineering Debt Register
reflects this.

---

## 2. iter-27 priority queue

### Priority 1 — Shizuku A17 implementation (external deadline: June 2026)

The audit at [`docs/audits/2026-05-17-shizuku-android17-compat.md`](../../../docs/audits/2026-05-17-shizuku-android17-compat.md)
captures the full design. The implementation is two non-destructive additions:

**Step 1**: Add to [`shizuku/ShizukuBridge.java`](../../../app/src/main/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridge.java):

```java
public static final String MIN_ANDROID_17_COMPATIBLE_VERSION = null; // unknown until fix ships

@AnyThread
public static boolean hasAndroid17CompatibilityRisk(@NonNull Context context) {
    if (Build.VERSION.SDK_INT < 37) return false;
    if (MIN_ANDROID_17_COMPATIBLE_VERSION == null) return true;
    String version = getInstalledVersionName(context);
    return version == null
            || compareVersion(version, MIN_ANDROID_17_COMPATIBLE_VERSION) < 0;
}
```

**Step 2**: In [`onboarding/OnboardingFragment.java`](../../../app/src/main/java/io/github/muntashirakon/AppManager/onboarding/OnboardingFragment.java)
where the Shizuku card renders, conditionally show a banner when
`ShizukuBridge.hasAndroid17CompatibilityRisk(context)` returns true, with copy that
deep-links to the existing Wireless ADB pairing wizard.

**Verification before commit**: Run NG on a Pixel 9 Android 17 Beta image; reproduce
the Shizuku #1965 / #1967 regression; confirm NG's onboarding banner now warns the
user. Sample output: "Shizuku 13.6.0 has known compatibility issues on Android 17.
Consider switching to Wireless ADB pairing."

**Estimated effort**: 3-5 hours including device setup, end-to-end test, and CHANGELOG entry.

**CHANGELOG entry shape** (drop into `## Unreleased`):

```md
### Compliance — Shizuku Android-17 detection + onboarding fallback (2026-MM-DD)

- New `ShizukuBridge.hasAndroid17CompatibilityRisk(Context)` probe combines
  `Build.VERSION.SDK_INT >= 37` with the installed Shizuku version against a
  `MIN_ANDROID_17_COMPATIBLE_VERSION` constant (kept `null` until a Shizuku fix ships).
- `OnboardingFragment` now surfaces a warning banner when the probe returns true,
  recommending the existing Wireless ADB pairing wizard as a fallback. Closes the
  implementation half of ROADMAP iter-25 §"Shizuku Android-17 Compatibility Watch"
  (the design half was captured in iter-26's
  `docs/audits/2026-05-17-shizuku-android17-compat.md`).
- When Shizuku publishes a fix for #1965 / #1967, populate
  `MIN_ANDROID_17_COMPATIBLE_VERSION` to that release; the banner auto-clears for
  users running the fixed version.
```

### Priority 2 — JaCoCo wire-in (maintainer build verification)

Follow [`docs/policy/jacoco-coverage-rollout.md`](../../../docs/policy/jacoco-coverage-rollout.md)
verbatim. Five steps:

1. Apply `id('jacoco')` in `app/build.gradle` plugins block.
2. Pin `jacoco_version = "0.8.13"` in `versions.gradle`.
3. Add the `jacoco` block + `jacocoTestReport` task.
4. Wire into `.github/workflows/tests.yml` with the upload step.
5. Optionally add a Codecov badge to `README.md`.

**Estimated effort**: 1-2 hours including `./gradlew :app:jacocoTestReport` verification locally.

### Priority 3 — ML-DSA OID prettify-name map (polish)

Per [`docs/audits/2026-05-17-android17-ml-dsa-keystore-oid.md`](../../../docs/audits/2026-05-17-android17-ml-dsa-keystore-oid.md)
§"Polish opportunity":

```java
public static String prettifyAlgorithmName(String oid, String fallback) {
    switch (oid) {
        case "1.3.6.1.4.1.2.267.12.6.5":  return "ML-DSA-65 (Dilithium)";
        case "1.3.6.1.4.1.2.267.12.8.7":  return "ML-DSA-87 (Dilithium)";
        default:                          return fallback;
    }
}
```

Insert into `utils/CertUtils` or `utils/Utils`. Wire into the two cert-display call
sites in `PackageUtils.java:745` and `ScannerFragment.java:439`. Add a unit test for
both OIDs.

**Estimated effort**: ~30 minutes.

### Priority 4 — Shizuku release-feed CI watcher (process automation)

Stand up a `.github/workflows/shizuku-release-watch.yml` workflow analogous to the
existing `upstream-rename-watch.yml`:

- Weekly (Thursday 09:27 UTC, staggered off existing slots)
- Probes the Shizuku GitHub releases feed
- Auto-opens an NG issue when a new release matches a "13.6.x" / "13.7.x" pattern
  containing the keywords "Android 17" or "#1965" / "#1967" in the release notes
- Reminds the maintainer to populate `MIN_ANDROID_17_COMPATIBLE_VERSION` and remove
  the onboarding banner

**Estimated effort**: ~1 hour.

---

## 3. Things explicitly **not** to do in iter-27

- **Do not bump `targetSdk = 37` yet.** The 5 audit verdicts pave the road but the bump itself should land alongside Android 17 Pixel stable rollout (June 2026) so it ships when the platform is in users' hands. Bumping early is a non-zero risk against pre-stable A17 builds.
- **Do not raise `min_sdk = 23`.** Material 1.14.0 is still alpha (pass-2 / pass-3 confirmed). Defer until forced.
- **Do not modify the existing 14 pre-existing audit docs.** They're verdicts on prior platform / dep changes; rewriting them invalidates the audit timeline.
- **Do not re-mine the competitor surface.** Pass-2 spot-checked the top-6 active competitors; the next external sweep should wait at least a week.

---

## 4. Reading the prior session

If you're an AI agent starting iter-27:

1. Read `PROJECT_CONTEXT.md` first (canonical entry-point).
2. Read `ROADMAP.md` §"Iter-26 Closures (2026-05-17)" for what just landed.
3. Read `CHANGELOG.md` §"Unreleased" for the 9 iter-25 + iter-26 entries.
4. Read `docs/audits/README.md` for the audit-doc doctrine.
5. Read `docs/audits/2026-05-17-shizuku-android17-compat.md` — Priority 1 implementation reference.
6. Read `docs/policy/jacoco-coverage-rollout.md` — Priority 2 implementation reference.
7. Skim `.ai/research/2026-05-17-pass-3/CHANGESET_SUMMARY.md` for the deliverables map.

Prep time: ~10 minutes. Then dive into Priority 1.

---

## 5. End-of-session-3 state

Three passes shipped:

- **Pass 1** (foundation): PROJECT_CONTEXT.md + 11 research artifacts + 5 governance items + `docs/audits/README.md` doctrine.
- **Pass 2** (execution): 3 source fixes + 4 architecture docs + iter-25 ROADMAP delta + 11 pass-2 research artifacts.
- **Pass 3** (audit + plan): 6 audit verdicts (5 clean + 1 design-pending) + minSdk cascade analysis + docs link-check workflow + JaCoCo plan + iter-26 ROADMAP delta + 11 pass-3 research artifacts.

The repository is in a **high-readiness state** for v0.5.0 (Settings & Discovery) and
v0.6.0 (Rootless Power). The Android 17 cliff is mapped, audited, and (5 of 6 dimensions)
clean. The Shizuku A17 risk is captured with a non-destructive runtime-detection
design ready to land once device verification is possible.

Iter-27 has the cleanest backlog of any iteration in the project's six weeks of history:
one external-deadline item, one Gradle wire-in, one polish item, one CI watcher. None
of them require fresh research; all the research is done.
