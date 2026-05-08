<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Google Play Contacts / Location-Button Policy Audit

**Date:** 2026-05-08
**Roadmap reference:** [ROADMAP.md](../../ROADMAP.md) — Iter-19 / Eng-Debt / Now row "Google Play Contacts/Location-Button Policy" ([S171](../../ROADMAP.md)). Deadline: enforcement begins ~2026-05-15 on the Google Play Console.
**Outcome:** ✅ **CLEAN — policy does not apply.**

## Background

Google Play's Contacts and Location-Button policy ([S171](../../ROADMAP.md)) begins
enforcement on or around 2026-05-15. The policy targets apps that:

- Surface a UI button that reveals **personal contact info** (name, email, phone, etc.) of the
  device user or anyone in their contacts, or
- Surface a UI button that reveals the user's **precise location**,

without first showing a runtime-permission rationale dialog explaining what is about to happen.

Apps that do not declare `READ_CONTACTS` / `WRITE_CONTACTS` / `GET_ACCOUNTS` /
`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` / `ACCESS_BACKGROUND_LOCATION` are
unaffected.

## Scope

Two independent searches:

1. `AndroidManifest.xml` — does AppManagerNG itself **declare** any of the policy-relevant
   runtime permissions?
2. UI surfaces — do any in-app buttons reveal contact info or precise location?

## Method

```
grep -n "uses-permission" app/src/main/AndroidManifest.xml \
  | grep -iE "CONTACT|LOCATION|FINE|COARSE|GET_ACCOUNTS|READ_PHONE_NUMBERS|CALL_LOG"

grep -rn "ACCESS_FINE_LOCATION\|ACCESS_COARSE_LOCATION\|FusedLocation\|LocationManager\|getLastKnownLocation\|requestLocationUpdates" \
  app/src/main/java/ app/src/main/AndroidManifest.xml

grep -rn "READ_CONTACTS\|WRITE_CONTACTS\|getContactsList\|ContactsContract" \
  app/src/main/java/
```

## Findings

### 1. Manifest declarations

[`AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml) declares the following
privacy-sensitive permissions:

- `READ_PHONE_STATE` — used for the **telephony-side mobile/Wi-Fi data-usage split** in App
  Usage. The app reads aggregated `NetworkStatsManager` records and uses `READ_PHONE_STATE`
  only to obtain the current subscription ID for the per-SIM split. **Does not surface
  contact info or location.**

NG **does not** declare any of:

- `READ_CONTACTS`
- `WRITE_CONTACTS`
- `GET_ACCOUNTS`
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION`
- `READ_PHONE_NUMBERS`
- `READ_CALL_LOG`
- `WRITE_CALL_LOG`
- `READ_SMS`

### 2. Policy-relevant API usage

The grep returned a single relevant file:
[`PermissionGroupCatalog.java`](../../app/src/main/java/io/github/muntashirakon/AppManager/permissions/PermissionGroupCatalog.java).

This file is a **constant catalog** mapping Android permission strings to UI groups for the
Permission Inspector feature. It enumerates `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`,
`READ_CONTACTS`, etc., as **labels** used to render groups in the Permission Inspector UI.
None of these strings are passed to a runtime-permission request. The Permission Inspector
inspects the permission state of *other* installed apps; AppManagerNG itself does not
request, hold, or use any contact or location permission.

### 3. UI surface review

No in-app button in AppManagerNG reveals:

- Device-user or contact-list contact info — there is no "show my contacts" affordance, no
  contact picker, no autocomplete from the device contact database. The *export contacts*
  utility hinted at in the iter-19 roadmap row was never implemented and no scaffold remains.
- Precise location — there is no map view, no GPS surface, no "show my location" affordance,
  no nearest-cell-tower display. App Usage's per-SIM split uses subscription ID only, not
  location.

## Conclusion

The Google Play Contacts and Location-Button policy does not apply to AppManagerNG.
AppManagerNG does not declare any contact or location permission and exposes no UI button
that reveals contact info or precise location.

**No remediation required before the 2026-05-15 enforcement window.**

## Forward-looking notes

If a future feature ever adds:

- A contact picker / autocomplete over device contacts → declare `READ_CONTACTS` *and* show
  a runtime-permission rationale dialog before the picker opens.
- A map view / GPS surface → declare `ACCESS_FINE_LOCATION` *and* show a runtime-permission
  rationale dialog before the map renders.

For both, the rationale dialog should fire on the **button tap**, not on app start, and must
state the specific data flow being unlocked. Use [`shouldShowRequestPermissionRationale()`](https://developer.android.com/reference/androidx/core/app/ActivityCompat#shouldShowRequestPermissionRationale(android.app.Activity,java.lang.String))
to determine whether the rationale should be repeated.

## Cross-references

- [ROADMAP.md](../../ROADMAP.md) — Iter-19 / Now / Eng-Debt row referencing [S171](../../ROADMAP.md)
- [Google Play Contacts and Location policy](https://support.google.com/googleplay/android-developer/answer/9047303) — official documentation
- [`AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml) — single source of truth for declared permissions
- [`PermissionGroupCatalog.java`](../../app/src/main/java/io/github/muntashirakon/AppManager/permissions/PermissionGroupCatalog.java) — clarification of why permission strings appear in source without runtime requests against them
