# Android 18 implicit URI grant removal — audit + remediation

**Date:** 2026-05-02
**Scope:** ROADMAP T3 "Android 18 Implicit URI Grant Removal" + Engineering Debt Register row "Android 18 implicit URI grant removal (planned)"
**Reference:** [S55] Android 17 behaviour-changes-all preview signalling Android 18 will stop auto-granting URI read/write to targets of `Intent.ACTION_SEND`, `Intent.ACTION_SEND_MULTIPLE`, and `MediaStore.ACTION_IMAGE_CAPTURE`.

## Method

`grep -rn "ACTION_SEND\|ACTION_SEND_MULTIPLE\|EXTRA_STREAM\|ACTION_IMAGE_CAPTURE\|EXTRA_OUTPUT" app/src/main/java` across the entire app source. Each hit was inspected to determine whether the intent originates a share (needs explicit grant) or receives one (does not).

## Inventory

### Outgoing share intents that carry a content URI

| File | Line | Intent | Uri payload | Pre-audit state | Post-audit state |
|------|------|--------|-------------|-----------------|------------------|
| `details/info/AppInfoFragment.java` | 453 | ACTION_SEND | `FmProvider` APK URI | flag set; **no ClipData** | flag + ClipData |
| `settings/AboutPreferences.java` | 101 | ACTION_SEND | diagnostic ZIP URI | flag + ClipData | unchanged |
| `logcat/LogViewerActivity.java` | 120 | ACTION_SEND | `FmProvider` log URI | flag set; **no ClipData** | flag + ClipData |
| `editor/CodeEditorFragment.java` | 592 | ACTION_SEND | `FmProvider` source URI | flag set; **no ClipData** | flag + ClipData |
| `fm/SharableItems.java` | 31 | ACTION_SEND | `FmProvider` single URI | flag set; **no ClipData** | flag + ClipData |
| `fm/SharableItems.java` | 40 | ACTION_SEND_MULTIPLE | `FmProvider` URI list | flag set; **no ClipData** | flag + multi-item ClipData |
| `misc/AMExceptionHandler.java` | 65 | ACTION_SEND | crash log URI | flag + ClipData | unchanged |

### Outgoing intents that do **not** carry a URI (no remediation needed)

| File | Line | Intent | Why safe |
|------|------|--------|----------|
| `scanner/ScannerFragment.java` | 179 | ACTION_SEND | text/email body only |
| `intercept/ActivityInterceptor.java` | 891 | ACTION_SEND | text/plain only |
| `history/ops/OpHistoryActivity.java` | 700 | ACTION_SEND | text/plain only |

### Incoming intents (interceptor / receiver)

| File | Line | Reason |
|------|------|--------|
| `intercept/IntentCompat.java` | 110, 118 | Reads incoming `EXTRA_STREAM` from inbound share — receiver, not sender |

### `IMAGE_CAPTURE` callers

`grep` returned **zero** `MediaStore.ACTION_IMAGE_CAPTURE` / `EXTRA_OUTPUT` callers in NG source — the app does not capture media. The Android 18 IMAGE_CAPTURE clause is a no-op for AppManagerNG.

### `PackageInstaller` URI grants

Reviewed `apk/installer/PackageInstallerCompat.java` and adjacent install paths: APK content is streamed into a `PackageInstaller.Session` via `openWrite()` rather than passed as a content URI to a third-party installer. The Android 17/18 implicit-grant change does not apply.

## Remediation

The five share-out sites without `ClipData` were updated to call `setClipData(ClipData.newRawUri(...))` for single-URI intents and a multi-item `ClipData` for `ACTION_SEND_MULTIPLE`. The existing `FLAG_GRANT_READ_URI_PERMISSION` flag is preserved.

`Intent.setClipData()` returns `void`, so it is invoked as a separate statement after the chained `addFlags(...)` builder.

The changes are mechanical and pre-emptive — nothing breaks today on Android 14/15/16, but the share targets will continue to receive read access when the implicit grant is removed in Android 18.

## Verification

- `./gradlew :app:compileDebugJavaWithJavac` — green.
- Manual smoke test plan (deferred until Android 18 preview SDK is available): share an APK, share a logcat dump, share a code-editor file, share a single FM file, share multiple FM files. Receiving app should be able to read each URI on a device running an Android 18 preview build.

## Roadmap consequences

- ROADMAP T3 row "Android 18 Implicit URI Grant Removal" → closed.
- Engineering Debt Register row "Android 18 implicit URI grant removal (planned)" → closed.
- No follow-up work expected unless Android 18 introduces additional intent verbs that lose implicit grant.
