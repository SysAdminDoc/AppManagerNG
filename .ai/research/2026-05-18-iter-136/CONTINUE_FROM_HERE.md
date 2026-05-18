# Continue From Here — Iter 136

## Completed

- Shipped the T12 Split-APK Cert-Mismatch Dialog row.
- Split bundle installs now preflight selected split signing certs against the base APK and surface actionable per-split mismatch rows before the package session starts.
- Updated `ROADMAP.md`, `CHANGELOG.md`, and `PROJECT_CONTEXT.md`.

## Verified

- Focused JVM test:
  - `.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.AppManager.apk.installer.SplitApkSignatureMismatchTest" --console=plain`

## Next roadmap row

- Continue after the iter-136 row. The next nearby unshipped row is likely the T2 **Predictive-Back WebView Freeze Fix** row, but it is already marked audit-clean in-place; pick the next actually actionable unshipped row after reconciling `ROADMAP.md`.

## Watch points

- The split-cert dialog is a pre-service gate. Real-device validation should exercise a `.apks` / `.xapk` bundle with one optional split signed by a different cert to confirm the removal path leaves a valid base-only or reduced split selection.
- Required split mismatches remain intentionally non-removable; users must cancel, choose a different bundle, or explicitly continue into the platform failure path.
