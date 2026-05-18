<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Android 17 Static-Final Reflection Audit

**Date:** 2026-05-08
**Roadmap reference:** [ROADMAP.md](../../ROADMAP.md) — Iter-20 / Eng-Debt / Now row "Android 17 Static-Final Reflection Audit (Severity Promotion)" ([S205], [S207]). Promoted from "future audit" to **Now** in iter-20 after two independent Android 17 docs confirmed the breakage threshold.
**Outcome:** ✅ **2 candidate sites identified — both mitigated.**

**2026-05-18 update:** Iter-124 closed the deferred server-side candidate by
gating the legacy LG `Resources.mSystem` workaround to API levels below 37. The
targetSdk=37 batch audit is recorded in
[`2026-05-18-android17-targetsdk37-batch.md`](2026-05-18-android17-targetsdk37-batch.md).

## Background

Android 17 ([S205], [S207]) tightens reflective access: `Field.set()` against a `static final`
field with `setAccessible(true)` now throws `IllegalAccessException` for apps targeting
SDK level 37. Reads (`Field.get()`) are unaffected. Writes against non-final or non-static
fields are also unaffected. The ban is specifically on writing to `static final` fields
even with `setAccessible(true)`.

AppManagerNG carries 20 `setAccessible(true)` call sites across `app/`, `libcore/`, and
`server/`. The audit task is to identify which of them perform a `Field.set()` write
against a `static final` target.

## Scope

All source roots:

- `app/src/`
- `libcore/`
- `libserver/`
- `libopenpgp/`
- `hiddenapi/`
- `server/`

Build outputs and Gradle caches excluded.

## Method

```
grep -rn "setAccessible(true)" --include="*.java" --include="*.kt" \
  app/src/ libcore/ libserver/ libopenpgp/ hiddenapi/ server/
```

20 matches. For each, classify:

| Class | Reflective set/get? | Target field static-final? | Verdict |
|-------|---------------------|---------------------------|---------|
| `Constructor` / `Method` setAccessible | not Field, ignored | n/a | safe |
| `Field.get()` only | no write | n/a | safe |
| `Field.set()` against instance field | write to non-static | n/a | safe |
| `Field.set()` against `static final` field | write | **yes** | **candidate** |

## Findings

### Methods / Constructors (10 sites, all safe)

These are `Method.setAccessible` / `Constructor.setAccessible` — the static-final reflection
ban applies to `Field`, not `Method` / `Constructor`. Method invocation and constructor
invocation paths are unaffected.

- [`HiddenAPIs.java:48`](../../app/src/main/java/io/github/muntashirakon/AppManager/ipc/HiddenAPIs.java#L48) — `attachBaseContext` Method
- [`RootServiceServer.java:215`](../../app/src/main/java/io/github/muntashirakon/AppManager/ipc/RootServiceServer.java#L215) — Constructor
- [`FileUtils.java:207`](../../libcore/io/src/main/java/io/github/muntashirakon/io/FileUtils.java#L207) — Method/Constructor (dual)
- [`RootServiceMain.java:60`](../../server/src/main/java/io/github/muntashirakon/AppManager/server/RootServiceMain.java#L60) — `attachBaseContext` Method
- [`RootServiceMain.java:218,228,230`](../../server/src/main/java/io/github/muntashirakon/AppManager/server/RootServiceMain.java#L218) — Constructor + Methods

### `Field.get()` reads (8 sites, all safe)

Read paths are unaffected by the Android 17 change.

- [`AppOpsManagerCompat.java:174`](../../app/src/main/java/io/github/muntashirakon/AppManager/compat/AppOpsManagerCompat.java#L174) — `sOpToString` static read on API < M only
- [`AppOpsManagerCompat.java:183`](../../app/src/main/java/io/github/muntashirakon/AppManager/compat/AppOpsManagerCompat.java#L183) — `MODE_*` int constant reads
- [`CodeEditorWidget.java:92,111,130`](../../app/src/main/java/io/github/muntashirakon/AppManager/editor/CodeEditorWidget.java#L92) — instance field reads
- [`Utils.java:667`](../../app/src/main/java/io/github/muntashirakon/AppManager/utils/Utils.java#L667) — toString helper, instance field reads
- [`BottomSheetBehavior.java:1571`](../../libcore/ui/src/main/java/io/github/muntashirakon/dialog/BottomSheetBehavior.java#L1571) — instance field read on `ViewPager.LayoutParams`
- [`AutoCompleteTextViewCompat.java:24,39`](../../libcore/ui/src/main/java/io/github/muntashirakon/view/AutoCompleteTextViewCompat.java#L24) — instance field reads on view

### `Field.set()` writes (3 sites)

#### Safe — instance write

- [`SecretKeyCompat.java:41`](../../app/src/main/java/io/github/muntashirakon/AppManager/crypto/ks/SecretKeyCompat.java#L41) — `KEY.set(secretKey, null)` writes to the **instance** field `private byte[] key` of a specific `SecretKeySpec` instance to zero out a key. Not static. **Safe.**

#### Static-final candidate — fixed in this audit

- [`TypefaceUtil.java:65`](../../app/src/main/java/io/github/muntashirakon/AppManager/utils/appearance/TypefaceUtil.java#L65) — `field.set(null, allFontsForThisApp)` against `Typeface.sSystemFontMap`, which is declared `private static final HashMap<String, Typeface> sSystemFontMap` in AOSP. **Candidate.**

  **Mitigation applied:** the map *contents* are already mutated in place via `remove()` / `put()` on lines 59-62. The `field.set(null, allFontsForThisApp)` write-back is therefore redundant — the underlying static-final map reference does not change; only its contents do. Removed the `Field.set()` call; left a comment at the site documenting why. Behavior preserved.

#### Static-final candidate — server-side, gated for legacy devices

- [`RootServiceMain.java:206`](../../server/src/main/java/io/github/muntashirakon/AppManager/server/RootServiceMain.java#L206) — `systemResField.set(null, wrapper)` against `Resources.mSystem`, which is declared `static final` in AOSP `android.content.res.Resources`. The intent is to swap the system-wide `Resources` instance in the privileged-server process for one that wraps the existing impl with a `ResourcesWrapper`.

  **Mitigation applied 2026-05-18:** the workaround exists only to avoid
  `createPackageContext` crashes on old LG ROMs. Iter-124 now runs the reflective
  static-field swap only when `Build.VERSION.SDK_INT < 37`, preserving the
  legacy-device behavior while avoiding Android 17's targetSdk=37 static-final
  write ban. The privileged server still takes the normal
  `createPackageContextAsUser` path on Android 17+.

## Mitigations applied

1. [`TypefaceUtil.java`](../../app/src/main/java/io/github/muntashirakon/AppManager/utils/appearance/TypefaceUtil.java) — removed the redundant `field.set(null, allFontsForThisApp)` call; left a comment at the site. Behavior preserved (the map's contents are mutated in place).
2. [`RootServiceMain.java`](../../server/src/main/java/io/github/muntashirakon/AppManager/server/RootServiceMain.java) — gated the legacy LG `Resources.mSystem` swap to pre-API 37 runtimes.

## Forward work

- No open static-final reflection follow-up remains for the targetSdk=37 bump.
  Re-run this audit when adding new hidden-API or resource-workaround
  reflection.

## Cross-references

- [ROADMAP.md](../../ROADMAP.md) — Iter-20 row "Android 17 Static-Final Reflection Audit (Severity Promotion)"
- [ROADMAP.md](../../ROADMAP.md) — Engineering Debt Register row "Android 17 targetSdk=37 compliance" (parent batch)
- [S205](../../ROADMAP.md), [S207](../../ROADMAP.md) — Android 17 platform docs
- [Android 17 behavior changes — All apps](https://developer.android.com/about/versions/17/behavior-changes-all)
