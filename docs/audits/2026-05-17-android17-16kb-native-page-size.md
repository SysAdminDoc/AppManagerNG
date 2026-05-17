<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Audit: Android 17 / 16 KB native page-size compatibility

**Date:** 2026-05-17
**Source:** https://developer.android.com/guide/practices/page-sizes (S336); https://developer.android.com/tools/zipalign (S337); AppManager upstream issue context (S148)
**Audited against:** repo at `fd33fdc` plus this remediation pass
**Roadmap row:** ROADMAP §"iter-19 / Eng-Debt / Now" — Android 17 16 KB Page-Size Compatibility

## Premise

Android's 16 KB page-size guidance requires APKs that contain native code to use
16 KB-compatible ELF load-segment alignment. It also requires uncompressed `.so`
ZIP entries to be aligned on a 16 KB page boundary when libraries are loaded
directly from the APK. The AppManagerNG roadmap row was opened after Pixel 9a /
Android 17 testers reported the main app list showing zero apps when native
libraries were not 16 KB-compatible.

## Sweep methodology

- `rg -n "System\\.load|System\\.loadLibrary|extractNativeLibs|useLegacyPackaging|ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES|max-page-size" app libcore libserver server hiddenapi scripts docs .github`
- `rg -n "NativeLibraries|ZipAlign|verify_reproducible_release" app/src/main/java app/src/test/java scripts docs .github`
- Manual review of [`app/src/main/cpp/CMakeLists.txt`](../../app/src/main/cpp/CMakeLists.txt) and [`app/build.gradle`](../../app/build.gradle).
- Manual review of native-library ingestion in [`NativeLibraries.java`](../../app/src/main/java/io/github/muntashirakon/AppManager/scanner/NativeLibraries.java).
- Manual review of release gating in [`scripts/verify_reproducible_release.sh`](../../scripts/verify_reproducible_release.sh), [`scripts/verify_reproducible_release.ps1`](../../scripts/verify_reproducible_release.ps1), and [`.github/workflows/release.yml`](../../.github/workflows/release.yml).

## Findings

- **NG native build:** [`app/build.gradle`](../../app/build.gradle) already passes `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` to CMake. This pass added both linker flags recommended by the Android 16 KB page-size guide for non-r28-guaranteed NDK paths: `-Wl,-z,max-page-size=16384` and `-Wl,-z,common-page-size=16384`.
- **Runtime loading path:** The existing [`2026-05-08 System.load audit`](2026-05-08-android17-system-load-readonly.md) remains valid. NG loads its JNI library through `System.loadLibrary("am")`; there is no manual `System.load(absolutePath)` extraction path.
- **APK packaging:** [`app/build.gradle`](../../app/build.gradle) keeps `packagingOptions.jniLibs.useLegacyPackaging true`, so NG release APKs use the compressed-library fallback documented by Android for AGP paths where 16 KB APK ZIP alignment is not guaranteed.
- **Ingested APK audit:** [`NativeLibraries.java`](../../app/src/main/java/io/github/muntashirakon/AppManager/scanner/NativeLibraries.java) now parses ELF32/ELF64 program headers and records the minimum `PT_LOAD.p_align` value for each `.so` discovered in an APK. App Details / native-library rows now show `16 KB aligned`, `Not 16 KB aligned`, or `16 KB alignment unknown`.
- **Release gate:** [`scripts/verify-native-page-alignment.py`](../../scripts/verify-native-page-alignment.py) now fails release verification if any APK `.so` has a `PT_LOAD.p_align` below 16384, cannot be parsed, or is stored uncompressed at a ZIP data offset that is not 16 KB-aligned. Both reproducible-release wrappers invoke it for every publish APK.
- **Coverage:** [`NativeLibrariesTest`](../../app/src/test/java/io/github/muntashirakon/AppManager/scanner/NativeLibrariesTest.java) covers 16 KB, 4 KB, unknown, and truncated ELF inputs. The Python release verifier was also exercised locally with generated aligned and misaligned APK fixtures.

## Verdict

✅ **remediated** — NG now builds its own JNI library with 16 KB ELF alignment,
surfaces per-library ELF page-alignment state for APKs it inspects, and blocks
release artifacts that contain misaligned native libraries.

The remaining validation gap is device-side: this pass could not build or run
on a 16 KB page-size Android image because the local workstation has no JDK on
`PATH`. CI or a maintainer machine with Java/Android SDK installed should run
the reproducible release workflow and then install on a 16 KB emulator/device.

## Follow-ups

- Keep `useLegacyPackaging true` until a release build proves the final APKs pass
  `zipalign -c -P 16 -v 4` with uncompressed native libraries.
- Re-run this audit when upgrading AGP/NDK packaging defaults or when adding any
  prebuilt `.so` dependency.
- If a future APK scanner needs to warn about `android:extractNativeLibs="false"`
  plus uncompressed ZIP offset alignment inside third-party APKs, reuse the
  Python verifier's local-header offset logic in a Java-side ZIP parser.
