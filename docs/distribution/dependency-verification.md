<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Dependency Verification And Locks

AppManagerNG uses Gradle dependency verification and dependency locking to
catch unreviewed binary drift before CI or release builds compile code.

Tracked guardrails:

- `gradle/verification-metadata.xml` enables Gradle dependency verification;
  Gradle's default verification mode is strict when this file exists.
- `gradle/verification-metadata.xml` records trusted checksums for resolved
  plugin, buildscript, application, test, benchmark, and library artifacts.
- `buildscript-gradle.lockfile` pins the root buildscript classpath.
- Each Gradle project has a `gradle.lockfile` generated from its resolvable
  dependency configurations.

Refresh after intentionally changing dependencies:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
.\gradlew.bat --write-verification-metadata sha256,pgp --write-locks :app:dependencies :benchmark:dependencies :docs:dependencies :hiddenapi:dependencies :libcore:compat:dependencies :libcore:io:dependencies :libcore:ui:dependencies :libopenpgp:dependencies :libserver:dependencies :server:dependencies
.\gradlew.bat --write-verification-metadata sha256,pgp --write-locks :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.settings.StartupInitUiStateTest
.\gradlew.bat help
.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.settings.StartupInitUiStateTest
```

The first command refreshes metadata and lock state for the configurations that
the current project exposes through dependency reports. The second command
captures Android Gradle Plugin detached tool artifacts such as platform-specific
AAPT2 and verifies that a real app compile/test path can write any missing lock
state. The final two commands prove strict verification and locking can
initialize and run a focused app test without refresh flags.

## Configurations that only the CVE gate resolves

`dependencyCheckAggregate` resolves configurations no ordinary build touches —
`:app:androidLintTool`, `:app:kotlinBuildToolsApiClasspath`, the UTP test-plugin
host configurations, and others. Their POMs are not covered by the refresh
commands above, because those drive `:<project>:dependencies`, which never
resolves them. Strict verification then aborts the task before the scanner
starts, and the gate correctly refuses to write a receipt for a build that was
never scanned. That failure mode is pinned by
`scripts/tests/test_run_dependency_cve_gate.py`, so a future configuration
addition surfaces as a gate failure rather than a silent skip.

Do **not** close such a gap with a blanket
`--write-verification-metadata sha256`: that records whatever happens to be in
the local Gradle cache, which is the thing verification exists to distrust. Add
the entries from the repository's own published checksums instead:

1. Run the gate and collect the reported artifacts:
   `py -3.12 scripts/run_dependency_cve_gate.py --out-dir reproducible-release/publish`
   Each line reads `<file> (<group>:<name>:<version>) from repository <Repo>`.
2. For each one, download the artifact from that repository
   (`https://repo1.maven.org/maven2` for `MavenRepo`,
   `https://dl.google.com/dl/android/maven2` for `Google`) together with the
   checksum file published beside it — `.sha256` where available, otherwise
   `.sha1`. Maven Central publishes `.sha256` only for newer uploads.
3. Compare the downloaded bytes against the published checksum. A mismatch is a
   supply-chain event, not a refresh problem: stop and investigate.
4. Only then record the sha256 of the verified download in
   `gradle/verification-metadata.xml`, with `origin="Verified against the
   upstream repository"` and a `reason` naming which published checksum was
   compared. Those attributes are how a reviewer tells a verified entry from a
   trust-on-first-use one.
5. Re-run the gate. It reports the next configuration it reaches; repeat until
   the scan starts.

Review rules:

- Keep checksum changes bundled with the dependency version change that caused
  them.
- Do not commit files from `.gradle/`, Gradle caches, local SDK paths, or local
  keystores.
- Treat new JitPack artifacts as source-review work: verify the repository,
  tag or commit pin, license compatibility, and whether a Maven Central
  artifact exists before accepting the new checksum.
- Review generated `ignored-key` entries before committing. A missing public key
  is acceptable only when the artifact is also protected by a checksum in
  `verification-metadata.xml`.
- Re-run the focused test or build task that exercises the dependency change
  after the metadata refresh.
