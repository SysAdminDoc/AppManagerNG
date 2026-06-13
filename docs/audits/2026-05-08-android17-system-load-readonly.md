<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Android 17 `System.load()` Read-Only Native Audit

**Date:** 2026-05-08
**Roadmap reference:** [ROADMAP.md](../../ROADMAP.md) — Iter-20 / Eng-Debt / Now row "System.load() Read-Only Native Audit (Android 17)".
**Outcome:** ✅ **CLEAN — no remediation required.**

## Background

Android 17 ([S206](../../ROADMAP.md)) hardens the JNI loader: native libraries that an app
extracts manually onto disk and then loads via `System.load(absolutePath)` must be marked
read-only (`chmod 444`) before the load call, or the runtime throws
`UnsatisfiedLinkError: dlopen failed: cannot load library — file not read-only`.

This affects apps that:

1. Bundle a native library as an asset (not as a `jniLibs/` artifact),
2. Extract it to writable storage at runtime (e.g. cache directory),
3. Load it explicitly via `System.load("/abs/path/to/libfoo.so")`.

Apps that load bundled libraries via `System.loadLibrary("foo")` — the canonical
AOSP path — are unaffected, because the platform installer is what extracts those `.so`
files (when `android:extractNativeLibs="true"` or `useLegacyPackaging true`) and the platform
applies the correct permissions itself.

## Scope

Recursive search for any direct or transient native-library extraction across all source
roots and resource directories of AppManagerNG.

Source roots searched:

- `app/src/`
- `libcore/`
- `libserver/`
- `libopenpgp/`
- `hiddenapi/`
- `server/`
- `libs/`

Build outputs and Gradle caches excluded.

## Method

```
grep -rn "System\.load[^L]" app/src/ libcore/ libserver/ libopenpgp/ hiddenapi/ server/
grep -rn "System\.loadLibrary" app/src/ libcore/ libserver/ libopenpgp/ hiddenapi/ server/
grep -rn "IoUtils\.copy" app/src/ libcore/ libserver/ libopenpgp/ hiddenapi/ server/
grep -rn "\.so[\"']" app/src/ libcore/ libserver/ libopenpgp/ hiddenapi/ server/
grep -n "useLegacyPackaging\|extractNativeLibs" app/src/main/AndroidManifest.xml app/build.gradle
```

## Findings

### `System.load(absolutePath)` call sites

**Zero matches.** AppManagerNG does not use the absolute-path JNI loader anywhere.

### `System.loadLibrary(name)` call sites

Two — both load AppManagerNG's own bundled `libam.so` JNI surface:

- [`app/src/main/java/io/github/muntashirakon/algo/AhoCorasick.java:7`](../../app/src/main/java/io/github/muntashirakon/algo/AhoCorasick.java#L7)
- [`app/src/main/java/io/github/muntashirakon/AppManager/utils/CpuUtils.java:13`](../../app/src/main/java/io/github/muntashirakon/AppManager/utils/CpuUtils.java#L13)

Both call sites are `System.loadLibrary("am")` — the canonical AOSP form. The `libam.so`
artifact is built from [`app/src/main/cpp/`](../../app/src/main/cpp/) and packaged into
`jniLibs/<abi>/libam.so` at build time. Extraction is the platform installer's
responsibility, not AppManagerNG's, and the platform handles the read-only flag itself.

### `IoUtils.copy` call sites that write `.so` files

**Zero matches.** Forty-plus `IoUtils.copy` call sites exist across the codebase
(APK extraction, backup data, image cache, log dumps, keystore export/import, etc.), but
**none of them write `.so` files**. `IoUtils.copy` is used for byte-stream piping; the
nearest-neighbour `.so` references appear in:

- [`scanner/NativeLibraries.java`](../../app/src/main/java/io/github/muntashirakon/AppManager/scanner/NativeLibraries.java) — *reads* `.so` ELF headers from the APK ZIP entries to compute size and 16 KB-page-alignment status; never writes them out.
- [`apk/signing/ZipAlign.java:144`](../../app/src/main/java/io/github/muntashirakon/AppManager/apk/signing/ZipAlign.java#L144) — passes `lib/*.so` ZIP entries through during APK alignment; the bytes stream into the alignment output ZIP, never onto the device's filesystem as a loadable native library.
- [`details/AppDetailsViewModel.java`](../../app/src/main/java/io/github/muntashirakon/AppManager/details/AppDetailsViewModel.java) — *reads* the suffix to label library types in the UI tag cloud (".so" → "SO", ".jar" → "JAR"); zero filesystem writes.

### Packaging configuration

[`app/build.gradle:118-126`](../../app/build.gradle#L118-L126) declares `useLegacyPackaging true`
for `jniLibs`. This means the .so files **are** extracted to the app's `nativeLibraryDir`
at install time, but the extraction is done by the platform installer, which handles the
read-only flag itself. NG does not write to `nativeLibraryDir` directly.

`AndroidManifest.xml` does not override `android:extractNativeLibs`, so the platform default
(true, in conjunction with `useLegacyPackaging true`) applies. No NG-side extraction occurs.

## Conclusion

AppManagerNG does not extract native libraries to disk via any of its own code paths and
does not use `System.load(absolutePath)`. The only native-library load sites use the
canonical `System.loadLibrary("am")` form, which delegates extraction and permission
handling to the platform installer.

The Android 17 read-only hardening therefore applies to the platform's behavior, not to
AppManagerNG's. **No remediation required.**

If a future feature ever extracts a native library to disk (e.g. dynamic plugin loading,
sideloaded JADX `.so` plugins, or a Frida sidecar from the iter-19 architectural-exploration
list), the extracting code must `chmod 444` the file before the corresponding
`System.load(absolutePath)` call. A reviewer reading this audit should reject any new code
path that extracts a `.so` to writable storage without read-only marking.

## Cross-references

- [ROADMAP.md](../../ROADMAP.md) — Iter-20 row "System.load() Read-Only Native Audit"
- [S206](../../ROADMAP.md) — Android 17 native-load read-only requirement
- [Android 17 behavior changes — All apps](https://developer.android.com/about/versions/17/behavior-changes-all)
