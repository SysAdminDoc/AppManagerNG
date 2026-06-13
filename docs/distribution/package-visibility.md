<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# QUERY_ALL_PACKAGES Justification

AppManagerNG declares `QUERY_ALL_PACKAGES` in its manifest. This document
explains why the permission is required, which features depend on it, and what
happens without it — written for F-Droid, IzzyOnDroid, Accrescent, and
Obtainium reviewers.

## Why QUERY_ALL_PACKAGES is required

Android 11 (API 30) introduced [package visibility filtering](https://developer.android.com/training/package-visibility).
Without `QUERY_ALL_PACKAGES`, an app can only see itself and a small set of
automatically visible packages. AppManagerNG is a **cross-app package manager**
— every core surface enumerates, inspects, or operates on the full installed
package set.

Replacing `QUERY_ALL_PACKAGES` with targeted `<queries>` elements is not
feasible: the app must discover *all* installed packages, including those
whose package names are not known in advance.

## Per-surface impact

| Surface | What it needs | Without QUERY_ALL_PACKAGES |
|---|---|---|
| **Main app list** | Full package enumeration | Shows only AppManagerNG itself |
| **Finder / search** | Cross-app component, permission, and tracker search | Empty results |
| **Permission Inspector** | Per-permission list of all holding apps | Incomplete / misleading |
| **Debloater** | Full system + user app enumeration | Cannot identify bloatware |
| **Tracker scanner** | Cross-app class-path scanning | Cannot scan other apps |
| **Batch operations** | Multi-app freeze / uninstall / backup | Cannot discover targets |
| **Backup / restore** | Package enumeration for backup scheduling | Incomplete backup set |
| **App usage / running apps** | Cross-reference running processes with packages | Unresolved process names |
| **Profiles** | Apply rules to app sets | Cannot resolve profile members |
| **Component blocking (IFW)** | Target package/component validation | Cannot validate targets |

## Reviewer notes

- **No data exfiltration.** Package enumeration results stay on-device. The
  `floss` flavor compiles out all optional network features. The `full` flavor
  gates every network feature behind user opt-in toggles.
- **No analytics or telemetry.** AppManagerNG ships zero tracking SDKs.
- **Standard for the category.** Every Android package manager in the
  ecosystem (upstream App Manager, Inure, SD Maid SE, Blocker, LibChecker)
  declares `QUERY_ALL_PACKAGES` for the same reason.
- **Detailed permission catalogue.** See [`docs/policy/permissions.md`](../policy/permissions.md)
  for the full manifest permission list with per-permission justifications.

## Google Play policy (reference only)

Google Play's [QUERY_ALL_PACKAGES policy](https://support.google.com/googleplay/android-developer/answer/10158779)
restricts the permission to apps whose core purpose requires broad package
visibility. AppManagerNG is not distributed through Google Play but meets the
policy's "device management" and "device search" exemption categories.
