<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Android 17 Static-Final Field Reflection Audit

**Date:** 2026-06-13
**Source:** https://developer.android.com/about/versions/17/behavior-changes-17
**Audited against:** `cc1e7711d`
**Roadmap row:** P0 — Android 17 behavior-change audit batch (API 37)
**Outcome:** ✅ **CLEAN (audit) — matches present, verified guarded.**

## Premise

Android 17 (API 37) makes `static final` fields unmodifiable via reflection.
`Field.set()` on a `static final` field throws `IllegalAccessException` at runtime
regardless of `setAccessible(true)`. This targets the hidden-API bypass pattern of
mutating system-class static finals to inject wrappers.

## Sweep methodology

```
grep -rn "Field\.set\b" app/src/ libcore/ libserver/ libopenpgp/ hiddenapi/ server/
grep -rn "setAccessible" app/src/ libcore/ libserver/ libopenpgp/ hiddenapi/ server/
grep -rn "Modifier\.FINAL\|STATIC.*FINAL" app/src/ libcore/ libserver/ libopenpgp/ hiddenapi/ server/
grep -rn "getDeclaredField" app/src/ libcore/ libserver/ libopenpgp/ hiddenapi/ server/
```

All source roots searched: `app/src/`, `libcore/`, `libserver/`, `libopenpgp/`,
`hiddenapi/`, `server/`. Build outputs excluded.

## Findings

### 1. `RootServiceMain.java` — `Resources.mSystem` field mutation

- **File:** `server/src/main/java/io/github/muntashirakon/AppManager/server/RootServiceMain.java:207-209`
- **Pattern:** `systemResField.set(null, wrapper)` on `Resources.mSystem` (static final)
- **Guard:** Already wrapped in `if (Build.VERSION.SDK_INT < 37)` at line 197
- **Verdict:** clean (audit) — guarded below A17; the field mutation is skipped on API 37+

### 2. `TypefaceUtil.java` — `Typeface.sSystemFontMap` in-place mutation

- **File:** `app/src/main/java/io/github/muntashirakon/AppManager/utils/appearance/TypefaceUtil.java:49-51, 94-96`
- **Pattern:** `field.get(null)` to obtain the Map, then `put()`/`remove()` on the Map object
- **Verdict:** clean — uses `Field.get()` only (read), never `Field.set()`; mutates the Map contents in-place, which is not affected by the static-final restriction

### 3. `HiddenAPIs.java` — method-based reflection only

- **File:** `app/src/main/java/io/github/muntashirakon/AppManager/ipc/HiddenAPIs.java`
- **Pattern:** `getDeclaredMethod()` + `Method.invoke()` for `ServiceManager.addService`, `DdmHandleAppName.setAppName`, `ContextWrapper.attachBaseContext`
- **Verdict:** clean — pure method reflection, no field mutation

### 4. Test contract

- **File:** `app/src/test/java/io/github/muntashirakon/AppManager/compat/android17/Android17BehaviorContractTest.java`
- **Pattern:** Regression guard that enforces the static-final guard patterns
- **Verdict:** clean — test infrastructure already validates this contract

## Verdict

✅ **CLEAN (audit)** — one `Field.set()` on a static final exists (`RootServiceMain.java:209`)
but is already guarded with `Build.VERSION.SDK_INT < 37`. All other reflection uses
method-based approaches or read-only field access. The `Android17BehaviorContractTest`
regression guard is in place.

## Follow-ups

None. The existing SDK guard and test contract are sufficient.
