<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# ROM F-Droid Pre-seed Guide

ROM builders who ship a pre-seeded F-Droid repository can include AppManagerNG
so users receive it (and updates) without manually adding a repo or sideloading.

## Repository format

F-Droid is migrating from the legacy XML index to a JSON-based index (v2).
During the transition, ship **both** formats so the pre-seeded repo works with
older F-Droid client versions:

| Format | File | F-Droid client version |
|---|---|---|
| Legacy XML | `index.xml` + `index.jar` | All versions |
| JSON v2 | `entry.json` + `index-v2.json` | 1.16+ |

## Package metadata

```
Package: io.github.sysadmindoc.AppManagerNG
Name: AppManagerNG
License: GPL-3.0-or-later
Categories: System
Source Code: https://github.com/SysAdminDoc/AppManagerNG
Web Site: https://github.com/SysAdminDoc/AppManagerNG
Issue Tracker: https://github.com/SysAdminDoc/AppManagerNG/issues
```

## APK placement

1. Use the **`floss`** flavor APK — it compiles out optional network features,
   matching the F-Droid Anti-Features posture. See
   [build-flavors.md](build-flavors.md) for details.
2. Place per-ABI APKs (recommended: `arm64-v8a` for modern devices, `universal`
   for broad compatibility) under the repo's `repo/` directory.
3. Include the APK signing certificate fingerprint for pinning:
   ```
   21:5F:B4:70:63:2E:A6:CD:59:A4:BA:AB:35:0A:9E:0B:99:AD:11:0F:DD:FA:F5:A9:EA:64:61:E5:D0:C2:38:6C
   ```

## Update channel

Point the repo's update-check URL at the GitHub Releases API or the stable
fingerprint URL:

```
https://raw.githubusercontent.com/SysAdminDoc/AppManagerNG/main/docs/fingerprints.txt
```

## Anti-Features

The `floss` flavor has **no Anti-Features** under current F-Droid policy:
- No tracking, analytics, or telemetry
- No non-free network services (optional network features compiled out)
- No non-free dependencies
- No promotion of non-free services

## Signing

ROM-preseeded APKs should use the upstream release signing key (fingerprint
above). If the ROM re-signs APKs, document the re-signing in the repo metadata
so users understand the signature mismatch with GitHub Releases.
