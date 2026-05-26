<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Distribution Build Flavors

AppManagerNG ships two Android application flavors in the `distribution`
dimension:

| Flavor | Intended channel | Optional online surfaces |
|---|---|---|
| `floss` | F-Droid, IzzyOnDroid, reproducibility audits | Disabled at compile time |
| `full` | GitHub Releases / Obtainium power users | Available behind the existing Settings -> Privacy opt-in gates |

`floss` is the default flavor. It keeps the local networking required by
AppManagerNG's ADB-over-TCP, wireless-pairing, and localhost privileged-server
plumbing, but `BuildConfig.ALLOW_OPTIONAL_NETWORK_FEATURES` is `false`.
That flag disables the user-facing optional network surfaces:

- Settings -> Privacy -> "Use the Internet"
- debloat-definition auto-update fetches
- VirusTotal and Pithus online scan report lookups

`full` sets `BuildConfig.ALLOW_OPTIONAL_NETWORK_FEATURES` to `true`; those
features are still opt-in and still require the existing "Use the Internet"
feature gate.

## F-Droid Metadata Contract

F-Droid metadata should select the `floss` flavor, which builds the
`flossRelease` variant instead of `fullRelease`:

```yaml
Builds:
  - versionName: <tag version>
    versionCode: <version code>
    gradle:
      - floss
```

F-Droid's `gradle` field takes flavor names, not the full Gradle task name; the
server composes `assemble<flavor>Release` from that list.

The reason is F-Droid's anti-feature policy. F-Droid marks apps from the user's
point of view and lists `Non-Free Network Services` for apps that promote or
depend entirely on proprietary network services, and `Tracking` for activity
reports sent without knowledge or by default. See
<https://f-droid.org/en/docs/Anti-Features/>.

The split follows the same channel pattern used by LibChecker's `foss` and
`market` flavors, but inverted for AppManagerNG's privacy posture: the
F-Droid-safe flavor is the default, while the optional online-report flavor is
explicit. Reference:
<https://github.com/LibChecker/LibChecker/blob/master/app/build.gradle.kts>.

## Maintainer Checks

Before changing this contract:

1. Keep `floss` first and default in `app/build.gradle`.
2. Never add telemetry, crash uploads, MOTD, update checks, or third-party
   online report lookups to `floss`.
3. Keep optional online work gated by
   `FeatureController.areOptionalNetworkFeaturesAvailable()` and the existing
   "Use the Internet" user preference.
4. Update `docs/distribution/package-visibility.md` if the manifest permission
   surface changes.
