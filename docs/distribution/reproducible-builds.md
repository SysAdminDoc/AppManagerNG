<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Reproducible Builds

AppManagerNG's release pipeline verifies that two clean builds from the same
source tree produce byte-identical APKs before publishing artifacts.

## How it works

1. **Two clean builds** — the release workflow (`.github/workflows/release.yml`)
   runs `./gradlew clean :app:assembleRelease` twice from a fresh checkout.
2. **SHA-256 comparison** — every output APK's hash is compared across the two
   builds. If any APK differs, the release is rejected.
3. **Sidecar publication** — each verified APK is published alongside a
   `.sha256` file containing its hash and a combined `sha256.txt` covering all
   release assets.
4. **16 KB page-alignment check** — `scripts/verify-native-page-alignment.py`
   confirms native `.so` entries are aligned to 16 KB pages (Android 15+
   requirement).
5. **CycloneDX SBOM** — `scripts/generate-cyclonedx-sbom.py` produces a
   software bill of materials attached to the release.

## Local verification

Maintainers can run the same two-build-and-compare locally:

```powershell
# Windows
.\scripts\verify_reproducible_release.ps1
```

```bash
# Linux / CI
./scripts/verify_reproducible_release.sh
```

Both scripts:
- Build twice into `build/reproducible-release/{first,second}/`
- Compare per-APK SHA-256 hashes
- Copy verified APKs to `build/reproducible-release/publish/` with
  `AppManagerNG-reproducible-<variant>.apk` naming
- Run native page-alignment and SBOM generation/validation
- Write `release-assets.txt` listing all publishable artifacts

## Why reproducibility matters

- **F-Droid / IzzyOnDroid** — reproducible builds are a prerequisite for
  verified-source badges in F-Droid repositories.
- **User trust** — anyone with the same JDK, Android SDK, and source tree can
  verify that the published APK matches the source.
- **Supply chain** — byte-identical builds prove the CI pipeline introduced
  no unexpected modifications.

## Known constraints

- Reproducibility is verified within a single CI run (same JDK, SDK, OS).
  Cross-environment reproducibility (different JDK vendors, OS versions) is
  not currently gated but is a goal.
- The debug keystore (`dev_keystore.jks`) is checked into the repo so debug
  builds are also reproducible across developers. Release signing uses a
  separate keystore provided via CI secrets.
- R8/ProGuard determinism depends on the AGP version. The project pins AGP
  and Gradle versions in `versions.gradle` to minimize drift.
