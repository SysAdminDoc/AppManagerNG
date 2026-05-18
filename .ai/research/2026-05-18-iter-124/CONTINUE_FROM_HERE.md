<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue from here — after iter 124

## Current state

- Branch: `main`
- Latest completed row: Eng-Debt **Android 17 ACCESS_LOCAL_NETWORK +
  Static-Final Reflection Ban**
- Validation completed:
  - `.\gradlew.bat :app:assembleFlossDebug --console=plain`

## What just shipped

Wireless ADB now has a targetSdk=37-ready local-network permission guard. The
manifest declares `ACCESS_LOCAL_NETWORK`; `Ops` blocks Android 17 targetSdk=37
mDNS discovery until the permission is available; and the blocker is surfaced
from startup, Settings, onboarding, pairing, chooser, and background reconnect
paths. The legacy LG `Resources.mSystem` static-final reflection workaround is
now limited to API levels below 37.

## Next visible roadmap work

The next visible unshipped `Next` row after iter 124 is:

| Row | Tier | Status |
| --- | --- | --- |
| **Android 17 ML-DSA Certificate OID Recognition** | T9 | **Next** |

## Notes for the next pass

- Start by checking whether `CertUtils` / certificate display code already maps
  ML-DSA-65 and ML-DSA-87. A previous 2026-05-17 pass may have handled part of
  this, so verify live source before adding duplicate mappings.
- The adjacent Android 17 cleartext-deprecation row is also small, but do not
  skip the ML-DSA row unless the live audit proves it is already shipped and
  only needs roadmap closure.
