<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Reproducible Release Builds

AppManagerNG release publishing is guarded by a two-build reproducibility check.
The release workflow performs two clean signed `:app:assembleRelease` builds from
the same tag, compares the resulting APK SHA-256 hashes, and refuses to publish
if the APK bytes differ.

The Linux/CI equivalent is:

```bash
bash scripts/verify_reproducible_release.sh
```

On Windows, use the PowerShell wrapper so Gradle reads the Windows Android SDK
path from `local.properties`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify_reproducible_release.ps1
```

The script writes comparison artifacts to `build/reproducible-release/`:

- `app-release-first.apk`
- `app-release-second.apk`
- `AppManagerNG-reproducible-release.apk`
- `sha256.txt`

Determinism controls currently in place:

- `BuildConfig.BUILD_TIME_MILLIS` comes from the git commit timestamp, matching
  upstream App Manager v4.0.5's reproducible-build model.
- Gradle archive tasks disable file timestamp preservation and use reproducible
  file ordering.
- The server-side `am.jar` and `main.jar` D8 input lists are sorted before jar
  creation, so filesystem enumeration order cannot change APK bytes.
- Release assets include a `.sha256` sidecar generated from the verified APK.
