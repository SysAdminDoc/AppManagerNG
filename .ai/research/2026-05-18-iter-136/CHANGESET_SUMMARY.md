# Iter 136 — Split APK Cert-Mismatch Dialog

## Roadmap row

- T12 — Split-APK Cert-Mismatch Dialog

## What changed

- Added `SplitApkSignatureMismatch`, a worker-thread helper that reads current signing cert SHA-256 values for the selected base/split APK entries and compares each selected split against the base APK before the install session starts.
- `PackageInstallerActivity` now gates split installs with a Material mismatch dialog that lists each bad split's name, version, cert SHA-256, and mismatch reason.
- The dialog lets users remove checked optional mismatched splits, prevents required split removal, and keeps an explicit "Install anyway" path for verification-disabled workflows.
- The preflight is skipped when the installer re-sign option is enabled because the session writer will sign selected entries uniformly before commit.
- Added `SplitApkSignatureMismatchTest` coverage for matching signer sets, signer-count mismatches, unreadable required split certs, and unreadable base certs.

## Verification

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.AppManager.apk.installer.SplitApkSignatureMismatchTest" --console=plain`
