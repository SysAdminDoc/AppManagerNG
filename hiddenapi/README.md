<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# hiddenapi/

Hand-curated stub declarations of Android private (`@hide`) APIs that AppManagerNG reflects
against. The stubs are `compileOnly` — they exist for the compiler to resolve symbols; the
actual implementations are looked up at runtime via [`dev.rikka.tools.refine`](https://github.com/RikkaApps/HiddenApiRefinePlugin).

## Updating stubs against a new AOSP release

When a new stable Android release lands and a stub here needs to be refreshed, pull source from
the **`android-latest-release`** branch on the official AOSP gerrit mirror:

```bash
git clone --depth 1 \
  --branch android-latest-release \
  https://android.googlesource.com/platform/frameworks/base.git \
  /tmp/aosp-frameworks-base
```

**Do not** pull from `master` / `main` / `android-mainline` / a date-stamped tag. AOSP moved
to a trunk-stable publishing cadence in 2026 — public source publishing now happens on a
**Q2 + Q4 schedule** rather than continuous. `master` reflects a transient mid-quarter snapshot
whose private-API surface may not survive to a published Android release. `android-latest-release`
is the official ref pointing at the most recent stable AOSP branch and is the only safe target
for stub harvesting.

For backporting stubs from a specific Android version, pull the version-tagged branch
(`android-15.0.0_r1`, `android-16.0.0_r1`, etc.) and note the source ref in the commit message.

Reference: ROADMAP iter-20 row "AOSP Source-Pull Retarget to `android-latest-release`" (S204).

## License

GPL-3.0-or-later. Stub source files preserve the SPDX header and are derived from AOSP
source under Apache-2.0 — see individual file headers for attribution.
