<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# AppManagerNG and Android Developer Verification

**Status:** Position document; implementation updated 2026-05-17
**Date:** 2026-05-08
**Roadmap reference:** [ROADMAP.md](../ROADMAP.md) — Iter-20 / T1 / Now row "Sideloading-Verification Position Document"; companion to "Android Developer Verification — BR/ID/SG/TH Enforcement".
**Audience:** Users in Brazil, Indonesia, Singapore, and Thailand on certified Android devices; advanced users globally; downstream packagers (F-Droid, IzzyOnDroid, Accrescent, Obtainium).

---

## TL;DR

- Google's [Android Developer Verification](https://developer.android.com/developer-verification) program begins enforcement in **September 2026** for certified Android devices in **Brazil, Indonesia, Singapore, and Thailand**.
- After that date, on those devices, **any app installed via `PackageInstaller` from a developer that has not registered with Android's developer-verification registry** can be blocked at install time by the on-device `Android Developer Verifier` system service.
- AppManagerNG's installer (`InstallerActivity`, batch installer, intent interceptor) uses `PackageInstaller` and is therefore **subject to the same gate as every other on-device installer**, including Obtainium, F-Droid, Aurora Store, Accrescent, and the platform "Install unknown apps" flow.
- AppManagerNG does **not** issue, vouch for, or proxy developer verification on behalf of the apps it installs. We are an installer, not an attestation authority.
- AppManagerNG now detects the verifier service, surfaces an **unknown** per-app verifier chip when that service is present, gates install confirmation with a developer-verification warning, and copies Android's `PackageInstaller` developer-verification failure reason into install diagnostics. Current public Android APIs expose the final failure reason but not a stable verified/unverified preflight result to installer apps.
- Outside the four enforcement regions, and on devices that do not ship the verifier service, **nothing changes**. Sideloading via AppManagerNG continues to behave as it always has.

---

## What is Android Developer Verification?

Android Developer Verification is a developer-identity registration program: developers who distribute Android apps to certified devices in the enforcement regions must register an identity through Google's developer-verification process and sign their APKs with a key tied to that registration.

On enforcement-region certified devices, a system service — surfaced via `Context.getSystemService("developer_verifier")` — checks the signing key of every app handed to `PackageInstaller` against the registry. If the key is not registered, the system can:

- **Block** the install outright with a system dialog ("This app is from an unverified developer"),
- **Warn** the user with an advanced-flow option to proceed at their own risk, depending on device policy and Android version,
- **Allow** the install if the device is in a developer mode that exempts the verifier.

The exact thresholds and UX are set by the platform image, not the installer app. A power-user device in **developer mode** with the verifier disabled behaves identically to today.

The same gate applies to:

- Apps installed via the system "Install unknown apps" flow,
- Apps installed via Obtainium, F-Droid, Aurora Store, Accrescent, or any other store / installer that uses `PackageInstaller`,
- Apps installed via `adb install`, **except** that ADB-installed packages are typically exempted by the platform when the device is in developer mode.

It does **not** apply to:

- Apps already installed before the enforcement date (no retroactive uninstall),
- Apps installed on non-certified devices (custom ROMs without GMS, e.g. LineageOS without Google Apps, /e/OS, GrapheneOS — the verifier service is not present on these images),
- Devices outside Brazil, Indonesia, Singapore, and Thailand at the 2026-09-30 enforcement window. (Google has signaled global rollout but has not announced dates beyond these four.)

---

## What AppManagerNG does

AppManagerNG is a power-user Android package manager. Our installer flows wrap `PackageInstaller` and add user-facing controls that the platform installer does not (custom install options, batch operations, intent interception, install-source attribution, signing-cert preview). The platform-level developer-verifier gate sits above us in the call chain — it runs after `Session.commit()` and before the install completes, regardless of which app called `PackageInstaller`.

**Concretely, in AppManagerNG's installer flow on a certified device in an enforcement region after 2026-09-30:**

1. The user opens an APK / APKS / APKM / XAPK in AppManagerNG.
2. AppManagerNG parses the package, surfaces the install-source attribution panel, and detects verifier-service availability through `Context.getSystemService("developer_verifier")` plus a binder-service fallback.
3. If the verifier service is present, AppManagerNG's install confirmation dialog displays a developer-verification warning before the user commits the install. For split APK selection flows where the split chooser replaces the stock confirmation body, AppManagerNG shows the same warning as a blocking confirmation before launching the install service.
4. The user confirms; `Session.commit()` runs.
5. The system verifier — not AppManagerNG — issues the final verdict. If Android returns `android.content.pm.extra.DEVELOPER_VERIFICATION_FAILURE_REASON`, AppManagerNG appends that reason to the post-install dialog and copied diagnostic transcript.

We do **not**:

- Bypass the verifier gate.
- Mask the verifier verdict to make a blocked install look successful.
- Forge a "verified" badge for an unverified developer.
- Carry a global allowlist of "trusted developers" — that is, by design, not our role.

We **do**:

- Make verifier availability and final failure reasons visible in the install flow, so the user is not surprised by a system dialog.
- Preserve the install-options surface (split selection, install-as-user, signing-cert preview) for users who want to inspect the APK before committing.
- Continue to support `adb install` and root-mode `pm install` paths, which the platform exempts on devices in developer mode.
- Continue to support sideloading on non-certified devices (LineageOS without GMS, /e/OS, GrapheneOS, etc.) without any verifier gate, because those devices do not ship the verifier service.

---

## What this means for users

| Situation | Outcome |
|-----------|---------|
| Certified device in BR / ID / SG / TH after 2026-09-30, installing a verified-developer APK | Should install normally. AppManagerNG shows the verifier warning when the service is present because Android does not expose a stable installer preflight verdict. |
| Certified device in BR / ID / SG / TH after 2026-09-30, installing an unverified-developer APK | AppManagerNG warns before commit; Android may show its advanced flow; final block/allow decision is made by the platform verifier, not AppManagerNG. |
| Certified device anywhere else, any APK | No change unless the device exposes the verifier service. AppManagerNG treats absent service as no local enforcement signal and shows no verifier chip. |
| Non-certified device (LineageOS no-GMS / /e/OS / GrapheneOS), any APK | No change. No verifier service is expected. Sideloading is unchanged. |
| Device in developer mode with `adb install` | Platform exempts ADB-installed packages on developer-mode devices. AppManagerNG's `pm install` shell path is unaffected. |
| Already-installed app on an enforcement device | No retroactive action. The app continues to run. Updates from an unverified developer may be subject to the verifier gate. |

---

## What this means for downstream packagers

If you ship AppManagerNG via F-Droid, IzzyOnDroid, Accrescent, or Obtainium, no action is required from you.

The AppManagerNG release APKs are signed with the [published SHA-256 fingerprint](fingerprints.txt). Once the AppManagerNG developer identity is registered with Google's Android developer-verification program (planned ahead of the 2026-09-30 enforcement window), the same release APKs will pass the verifier gate. We will publish a CHANGELOG entry confirming the registration date.

If you sign a downstream rebuild of AppManagerNG with your own key (community fork, REPO-specific build, etc.), the verifier will treat that build as an unverified developer for users in the enforcement regions until you register your own developer identity with Google's Android developer-verification program. This is a Google policy decision; AppManagerNG cannot grant verifier-trust on your behalf.

---

## What about non-Google verifiers?

Other ecosystems run parallel attestation programs:

- **GrapheneOS App Store** uses its own verification model, completely independent of Google's verifier. AppManagerNG installs on GrapheneOS-installed devices are not subject to either system.
- **Accrescent** runs a curated, key-pinned distribution model. Apps shipped through Accrescent receive Accrescent's pinning guarantee, which is orthogonal to (and stricter than) Google Play's developer-verification program.
- **F-Droid 2.0** is rolling out a new index signing model (protobuf index v2, signed per-repo). This is also independent of Google's verifier and applies only to F-Droid-mediated installs.

AppManagerNG's `InstallerActivity` is agnostic to which of these is in play; we read the active verifier service (if any) and surface its verdict.

---

## Frequently asked questions

**Q: Does AppManagerNG itself need to be a verified developer to install on enforcement devices?**

Yes. AppManagerNG's own APK is subject to the verifier gate when installed via the platform installer or another `PackageInstaller`-using app (e.g. Obtainium) on an enforcement device. Our developer identity registration is being completed ahead of the 2026-09-30 enforcement window and the registration date will be in CHANGELOG.md. Until then, users in the enforcement regions on certified devices may need to use the "advanced flow" path to install AppManagerNG itself.

**Q: Can I disable the verifier on my own device?**

If your device ships the verifier service, the verifier toggle (when present) lives in **Settings → Security & privacy → Google Play Protect → Developer verification**, or under **Developer options** depending on device and Android version. On enforcement-region certified devices, the platform may deny disabling the verifier without an enrolled developer-mode flag. AppManagerNG cannot disable the verifier and does not surface a toggle that pretends to.

**Q: I'm a developer publishing an app I want my users to install via AppManagerNG. Do I need to register?**

If your users are on certified devices in the four enforcement regions after 2026-09-30, **yes** — Google's verifier will gate your APK regardless of which installer your users use, including AppManagerNG. Registration is currently free and does not require a Play Store listing. See [Google's Developer Verification documentation](https://developers.google.com/android/play-protect/developer-verification) for the registration flow.

**Q: I'm in BR / ID / SG / TH on a custom ROM without Google Mobile Services. What happens?**

Nothing changes. The verifier service is part of certified Google Android images; LineageOS without GMS, /e/OS, GrapheneOS, and other de-Googled ROMs are not expected to ship it. AppManagerNG shows no verifier chip when the service is absent, and installs proceed as they always have.

**Q: Is this surveillance? Does AppManagerNG send install metadata to Google?**

No. AppManagerNG does not send install metadata to Google or anyone else. The verifier check is a platform-level call that the system makes during `Session.commit()` — AppManagerNG only **reads** the `PackageInstaller` result extras so we can surface the platform's final reason in the UI. The verifier itself, when present, may make its own network calls; that is part of the certified Android system image, not AppManagerNG.

---

## Cross-references

- [ROADMAP.md](../ROADMAP.md) — T1 row "Android Developer Verification — BR/ID/SG/TH Enforcement"
- [docs/fingerprints.txt](fingerprints.txt) — published signing-certificate SHA-256 fingerprint(s)
- [Google Developer Verification](https://developer.android.com/developer-verification) — official program documentation
- [PackageInstaller API](https://developer.android.com/reference/android/content/pm/PackageInstaller#EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON) — developer-verification failure reason returned via install result intents
- [Obtainium issue #2911](https://github.com/ImranR98/Obtainium/issues/2911) and [discussion #2846](https://github.com/ImranR98/Obtainium/discussions/2846) — community sideload-tooling tracking
- [GrapheneOS App Store Release 36](https://github.com/GrapheneOS/AppStore/releases) — the Android 16 background-install-confirmation fix referenced in iter-20

---

This document will be updated as the enforcement window advances and as additional regions roll out. Material changes will be noted in CHANGELOG.md under a `Docs — sideload-verification` heading.
