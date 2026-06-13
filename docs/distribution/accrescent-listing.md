<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Accrescent Listing Packet

Status: packaging helper ready; listing blocked by current Accrescent policy
and maintainer-only external access.
Checked: 2026-05-26.

This packet is the source-of-truth for preparing AppManagerNG's Accrescent
submission. It is not proof that an external developer-console upload has been
filed or accepted. Under the current published Accrescent requirements,
AppManagerNG should not be submitted without either an Accrescent-specific
product flavor or an explicit policy exception.

## Current Release

- Repository: `https://github.com/SysAdminDoc/AppManagerNG`
- Tag: `v0.5.0`
- Package name: `io.github.sysadmindoc.AppManagerNG`
- Display name: `AppManagerNG`
- Version name: `0.5.0`
- Version code: `7`
- Target SDK: `36`
- Release: `https://github.com/SysAdminDoc/AppManagerNG/releases/tag/v0.5.0`
- Signing certificate SHA-256:
  `21:5F:B4:70:63:2E:A6:CD:59:A4:BA:AB:35:0A:9E:0B:99:AD:11:0F:DD:FA:F5:A9:EA:64:61:E5:D0:C2:38:6C`

## Upload Inputs

- APK set: generate with `scripts/build_accrescent_apks.sh`.
- Icon: `fastlane/metadata/android/en-US/images/icon.png` (512 x 512 PNG).
- Suggested display name: `AppManagerNG`.
- Suggested package notes: power-user package manager, file manager, backup and
  restore utility, installer, tracker scanner, and privileged Android
  diagnostics tool.

The Accrescent upload target is a signed bundletool `.apks` file, not the
GitHub Release universal APK and not the legacy repacked `.apks` archive format.
The helper script preserves bundletool's APK-set metadata and enforces
bundletool `1.11.4` or newer plus Accrescent's 128 MiB APK-set limit.

```bash
KEYSTORE=/path/to/AppManagerNG-release.jks \
KEYSTORE_PASS='...' \
KEY_ALIAS='...' \
KEY_ALIAS_PASS='...' \
BUNDLETOOL=bundletool \
bash scripts/build_accrescent_apks.sh flossRelease
```

If `app/keystore.properties` exists, the script can read the same release
signing fields used by the Gradle release build.

Expected output:

```text
app/build/outputs/accrescent/AppManagerNG-0.5.0-floss-accrescent.apks
app/build/outputs/accrescent/AppManagerNG-0.5.0-floss-accrescent.apks.sha256
```

## Automated Checks

- Accrescent account creation currently requires an allowlisted GitHub account,
  and the public docs say new allowlist requests are not being accepted.
- AppManagerNG is developer-signed; Accrescent does not remotely sign apps.
- The published signing certificate is documented in `docs/fingerprints.txt`.
- The generated APK set must stay under 128 MiB; the current universal
  `flossRelease` APK is 29,496,173 bytes, so the split APK set is expected to
  fit but must be checked at generation time.
- The current 512 x 512 PNG icon is present.
- The current merged `flossRelease` manifest does not set
  `android:debuggable`, `android:testOnly`, or `android:usesCleartextTraffic`.

## Policy Blockers

Sensitive permissions and service filters requiring review are documented in
`docs/distribution/package-visibility.md` and `docs/policy/permissions.md`.
The current Accrescent requirements make at least two of them likely blockers:

- `QUERY_ALL_PACKAGES`: required for the app-manager, backup/restore,
  debloater, scanner, and package-visibility surfaces.
- `MANAGE_EXTERNAL_STORAGE`: required for file-manager and cross-app backup
  workflows where SAF-only access is insufficient.
- `REQUEST_INSTALL_PACKAGES`: required because AppManagerNG is an APK installer
  and split-package installer. Accrescent's current published requirements only
  list web browsers, file sharing, and messengers as accepted use cases and say
  the permission may not be used for updating or installing other apps.
- `SYSTEM_ALERT_WINDOW`: used by the opt-in foreground-component tracking
  surface.
- `android.accessibilityservice.AccessibilityService`: used by an opt-in
  foreground-component tracker. Accrescent's current published requirements
  restrict accessibility services to helping users with disabilities interact
  with the device.

Accrescent submission therefore needs a maintainer/product decision before
external filing:

1. Build an Accrescent-specific flavor that removes installer and
   accessibility-tracker surfaces and strips their manifest declarations.
2. Ask Accrescent reviewers for an explicit exception before building/uploading.
3. Keep the row blocked until Accrescent policy changes.

Relevant Accrescent references checked on 2026-05-26:

- `https://accrescent.app/docs/guide/getting-started/new-app.html`
- `https://accrescent.app/docs/guide/publish/requirements.html`
- `https://accrescent.app/features`

## Maintainer Action Required

Choose one of the policy paths above. Only then generate a signed `.apks` with
the release keystore, upload it plus the 512 x 512 icon through an allowlisted
Accrescent Developer Console account, complete domain/package ownership
verification if requested, and attach the permission justification docs when
reviewers ask about sensitive permissions or the accessibility service.
