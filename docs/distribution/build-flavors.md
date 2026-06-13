<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Build Flavors — Maintainer Contract

AppManagerNG ships two build flavors via the `distribution` flavor dimension
in `app/build.gradle`. Both produce the same application; the only difference
is a compile-time boolean that gates optional network features.

## Flavor matrix

| Flavor | `ALLOW_OPTIONAL_NETWORK_FEATURES` | Default | Target channels |
|---|---|---|---|
| **`floss`** | `false` | **Yes** | F-Droid, IzzyOnDroid, reproducibility audits, privacy-first users |
| **`full`** | `true` | No | GitHub Releases, Obtainium |

## What the flag controls

When `BuildConfig.ALLOW_OPTIONAL_NETWORK_FEATURES` is `false`, the following
features are **compiled out** — there is no runtime toggle to re-enable them:

- VirusTotal APK upload and report lookup
- Pithus hash lookup
- Debloat-definition manifest auto-fetch / auto-update
- Settings → Privacy → "Use the Internet" master switch (hidden)

Local networking (ADB-over-TCP, wireless pairing, localhost privileged-server
channel) is **always available** regardless of flavor.

## Maintainer rules

1. **`floss` is the default.** `isDefault = true` in `app/build.gradle`.
   Building without specifying a flavor produces `floss`.
2. **No behavioral divergence beyond the flag.** Both flavors share the same
   source sets, resources, and native libraries. Do not add flavor-specific
   source directories unless a new compile-time gate requires it.
3. **F-Droid metadata targets `floss`.** The `fdroid-listing.md` Gradle field
   specifies the `floss` flavor. fdroidserver composes the assemble task.
4. **GitHub Releases ship both.** The release workflow produces per-ABI APKs
   for both `floss` and `full` variants. Asset naming distinguishes them.
5. **The `full` flavor never phones home silently.** Every network feature is
   gated behind a user-visible toggle and the master "Use the Internet"
   preference. The flavor enables the *option*; the user enables the *action*.

## Build commands

```bash
# FLOSS (default)
./gradlew assembleFlossRelease
./gradlew assembleFlossDebug

# FULL
./gradlew assembleFullRelease
./gradlew assembleFullDebug
```

## Adding a new online feature

1. Guard the call site with `if (BuildConfig.ALLOW_OPTIONAL_NETWORK_FEATURES)`.
2. Gate UI entry points behind the same check so `floss` users never see
   unreachable options.
3. Add a per-feature user toggle (default off) under Settings → Privacy.
4. Document the feature in `docs/policy/permissions.md` if it requires a new
   permission.
