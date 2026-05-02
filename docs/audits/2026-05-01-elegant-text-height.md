<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# elegantTextHeight Audit (Android 16 / targetSdk=36)

**Date:** 2026-05-01
**Roadmap reference:** [ROADMAP.md](../../ROADMAP.md) — T2 row "elegantTextHeight Audit"; also the L11 Android 16 compliance row "All layouts | Compliance | Audit for `elegantTextHeight` attribute usage".
**Outcome:** ✅ **CLEAN — no remediation required.**

## Background

[Android 16 behavior changes for targetSdk=36](https://developer.android.com/about/versions/16/behavior-changes-16) deprecate the `android:elegantTextHeight` XML attribute and its programmatic counterpart `TextView.setElegantTextHeight(boolean)`. Apps targeting API 36 will have the attribute silently ignored. Affects text rendering for scripts that rely on extended ascender/descender metrics — primarily Arabic, Thai, and several Indic scripts.

Apps that previously toggled `elegantTextHeight=false` to compress these scripts must migrate to other text-metric APIs or accept the platform-default elegant rendering.

## Scope

Recursive search for the attribute (XML) and method (Java/Kotlin) across every source root of AppManagerNG, excluding generated build outputs and lint caches.

Source roots searched:

- `app/src/` (full tree — main, debug, release, androidTest, test variants; all res/ qualifiers; Java + Kotlin + XML + native)
- `libcore/`
- `libserver/`
- `libopenpgp/`
- `hiddenapi/`
- `server/`

## Method

```
grep -rln "elegantTextHeight\|setElegantTextHeight" \
  app/src/ libcore/ libserver/ libopenpgp/ hiddenapi/ server/ \
  | grep -v "/build/" | grep -v "intermediates"
```

Plus res/-only sweep:

```
grep -rln "elegantTextHeight" app/src/main/res/
```

## Result

```
(no source matches)
(no res matches)
```

The only repository matches for the term `elegantTextHeight` are:

1. `ROADMAP.md` lines 80, 345 — these self-references documenting the audit task.
2. `server/build/intermediates/lint-cache/lintAnalyzeDebug/private-apis-18-7541949.bin` — a binary Lint analysis cache containing strings from the deprecation database, NOT source code.

Neither is a remediation target.

## Conclusion

AppManagerNG declares zero usage of `android:elegantTextHeight` or `setElegantTextHeight`. The Android 16 deprecation has no impact on this app's text rendering behaviour at `targetSdkVersion = 36`.

No code or resource changes required. The roadmap item is closed by this audit document.

## Ongoing guard

Any future PR that introduces `elegantTextHeight` should be flagged in code review. Lint already reports the attribute as deprecated when targeting API 36+; the existing Lint pipeline (`./gradlew :app:lint`) will catch a regression without additional configuration.
