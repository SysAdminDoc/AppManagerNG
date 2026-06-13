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
