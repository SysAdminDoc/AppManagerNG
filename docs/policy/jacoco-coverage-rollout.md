<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# JaCoCo coverage rollout plan

**Status:** Implementation plan — not yet wired into `app/build.gradle` or `tests.yml`.
**Roadmap reference:** ROADMAP iter-24 backlog F-NEW-11; ROADMAP T11 "Unit Test Coverage Expansion".
**Audience:** AppManagerNG maintainer landing the actual JaCoCo wire-in; reviewers of the coverage-badge PR.

This document captures the design for landing JaCoCo coverage reporting so that the
maintainer can ship it as a focused single-commit PR. The autonomous-research-session
that drafted this document deliberately did not modify `app/build.gradle` because:

1. **Gradle integration is a single-commit change that benefits from a real local build verification** — running `./gradlew :app:jacocoTestReport` on a Windows / macOS / Linux build host before commit is the right gate. The walk-away session cannot run that build.
2. **The current Robolectric test suite is small enough** (per ROADMAP T11 row "Unit Test Coverage Expansion": "currently only basic unit tests exist per `tests.yml`") that the *initial* coverage number will be embarrassingly low. The maintainer should land JaCoCo with a clear "this is the baseline, not the goal" framing in the PR description rather than have an autonomous session quietly push a 4%-coverage badge to the README.

The actual wire-in is small once the maintainer is ready.

---

## Design

### Step 1 — apply the JaCoCo plugin in `app/build.gradle`

Insert at the top, alongside the existing two plugins:

```gradle
plugins {
    id('com.android.application')
    id('dev.rikka.tools.refine') version "${refine_version}"
    id('jacoco')  // <-- new
}
```

### Step 2 — JaCoCo version pin in `versions.gradle`

The Android Gradle Plugin (`8.13.2` per [`versions.gradle:13`](../../versions.gradle#L13))
bundles a default JaCoCo. Pin explicitly to the current stable line so the version is
visible in the dependency-pin ledger:

```gradle
// in versions.gradle ext { ... }
jacoco_version = "0.8.13"  // https://github.com/jacoco/jacoco/releases
```

### Step 3 — `jacoco` block in `app/build.gradle`

Add after the `android { ... }` block:

```gradle
jacoco {
    toolVersion = jacoco_version
}

android {
    // ... existing config ...
    buildTypes {
        debug {
            testCoverageEnabled true  // <-- new; enables JaCoCo instrumentation on debug
        }
    }
}

tasks.register('jacocoTestReport', JacocoReport) {
    dependsOn 'testDebugUnitTest'
    reports {
        html.required = true
        xml.required = true   // for Codecov / Coveralls / report parsers
        csv.required = false
    }

    def fileFilter = [
        // Exclude generated code from the coverage metric — this is the standard
        // Android JaCoCo exclusion set; keeping it here in source so the next
        // contributor doesn't have to re-derive it.
        '**/R.class', '**/R$*.class',
        '**/BuildConfig.*',
        '**/Manifest*.*',
        '**/*Test*.*',
        'android/**/*.*',
        // Hidden-API stubs are compile-only — they have no runtime behaviour to cover.
        // The stubs live in the `hiddenapi` module; this filter covers any spillover.
        '**/HiddenUtil.*',
    ]
    def debugTree = fileTree(dir: "${buildDir}/intermediates/javac/debug/classes", excludes: fileFilter)
    def mainSrc = "${project.projectDir}/src/main/java"

    sourceDirectories.setFrom(files([mainSrc]))
    classDirectories.setFrom(files([debugTree]))
    executionData.setFrom(fileTree(dir: project.buildDir, includes: [
        'jacoco/testDebugUnitTest.exec',
    ]))
}
```

### Step 4 — wire into `.github/workflows/tests.yml`

Insert after the existing "Run tests" step:

```yaml
      - name: Generate JaCoCo coverage report
        run: ./gradlew jacocoTestReport

      - name: Upload coverage report
        if: ${{ always() }}
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-coverage
          path: ./app/build/reports/jacoco/jacocoTestReport/html/
```

Optionally, integrate with Codecov for a public badge:

```yaml
      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v4
        with:
          files: ./app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml
          flags: unittests
          name: appmanager-ng
          fail_ci_if_error: false  # don't fail the build on Codecov outage
```

**Privacy note**: Codecov uploads coverage data to a third-party service. If the
maintainer prefers a strictly-on-repo path, skip the Codecov step and rely on the
uploaded HTML artifact + a manual badge maintained in README. This matches the F-Droid
Anti-Features posture (no `Tracking` / `NonFreeNet` dependencies) — see ROADMAP [S244]
for the bright-line policy.

### Step 5 — README badge

When Codecov is enabled, replace the `<!-- coverage badge placeholder -->` (or add a
new line) in `README.md` near the existing version/license/platform badges with:

```html
<img alt="Coverage" src="https://codecov.io/gh/SysAdminDoc/AppManagerNG/branch/main/graph/badge.svg" />
```

Without Codecov, surface the coverage % manually in CHANGELOG entries when material
test-suite expansion lands.

---

## Verification before landing

1. `./gradlew :app:assembleDebug` succeeds (Gradle plugin doesn't break the build).
2. `./gradlew :app:testDebugUnitTest` runs the existing test suite to completion (Robolectric).
3. `./gradlew :app:jacocoTestReport` produces `app/build/reports/jacoco/jacocoTestReport/html/index.html`.
4. Open `index.html` locally; verify the coverage report renders and is non-empty (the existing tests at minimum exercise `AESCrypto`, `OperationHistoryExporter`, `InstallTranscript`, `FilterOption` — there will be some coverage).
5. Push to a topic branch and verify the GitHub Actions run uploads the coverage artifact successfully.
6. Open the PR with a clear baseline-coverage number in the description.

---

## Why not now

The autonomous session that drafted this plan landed five Android 17 sub-audits and a
Shizuku A17 compatibility audit in the same iter-26 pass; adding a Gradle plugin and
the corresponding CI plumbing on top, without local build verification, would push the
ratio of "shipped and verified" to "shipped untested" past the comfort threshold.

The plan above is detailed enough that a follow-up commit landing JaCoCo is essentially
a copy-paste exercise gated by the local build verification. ROADMAP iter-26 backlog
F-NEW-11 stays open with this doc cited as the implementation reference.

---

## References

- [JaCoCo releases](https://github.com/jacoco/jacoco/releases) — version pin source
- [Codecov GitHub Action](https://github.com/codecov/codecov-action) — third-party badge source if used
- [AGP testCoverageEnabled docs](https://developer.android.com/reference/tools/gradle-api/9.0/com/android/build/api/dsl/BuildType#testCoverageEnabled())
- ROADMAP iter-24 row F-NEW-11 — origin of this plan
- ROADMAP T11 row "Unit Test Coverage Expansion" — the larger track this contributes to
- F-NEW-13 doc-link-check workflow at [`.github/workflows/docs-link-check.yml`](../../.github/workflows/docs-link-check.yml) — sibling iter-26 hygiene item, already shipped
