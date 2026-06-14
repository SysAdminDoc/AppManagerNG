# Research — AppManagerNG

_Pass 4 — 2026-06-14 (post-v0.6.0). Supersedes the 2026-06-13 snapshot, several of whose
top opportunities shipped between then and the v0.6.0 release._

## Executive Summary

AppManagerNG is a GPL-3.0-or-later Android package-manager fork (minSdk 21, target 36) on
Android Views + Material 1.13.0, Java/Kotlin, Room, WorkManager, Gradle 9.4.1/AGP 9.2.0,
`floss`/`full` flavors, and root/ADB/Shizuku/Dhizuku privilege lanes. v0.6.0 (versionCode 8)
shipped 2026-06-14.

Between the prior research pass and v0.6.0 the fork **closed most of its previously-identified
top opportunities**: human-readable split-APK labels, debloat preset export/import, backup
restore API-level warnings, the Android 17 behavior-change audit batch, installer caller-result
support, AppFunctions chips, ApplicationExitInfo history, standby-bucket inspect/set, Advanced
Protection detection + installer pre-flight block, and the unified destructive-action
confirmation with kill-safety gate. Independently of competitor framing, the fork **already
ships** a full cross-app Finder query engine (32 filter dimensions incl. permissions, app-ops,
trackers, signatures, tags), IFW+PM dual-mode component blocking, per-app dex2oat control, a
file-manager directory filter, installer duplicate-package detection with version/signature
diffs, and metadata-v6/v7 AES backup encryption that derives a **per-file IV and per-archive
HKDF key** — so the "GCM nonce reuse" class is resolved, not open.

The remaining high-value direction is therefore **not breadth** — it is **Android 17 survival
and the last reliability edges**: validate (and fix, if present) the A17 app-enumeration
regression that is breaking upstream and has no working answer anywhere in the ecosystem;
re-validate the Shizuku/Dhizuku lanes (both upstream tools have shipped no A17 release); close
the installer's fire-and-forget OBB-copy race; and extend the biometric gate to uninstall to
match the installer-app field. These are few, verified, and load-bearing.

Top opportunities in priority order:
1. Validate/fix Android 17 app-list enumeration on-device (upstream #1948 — empty/sparse list).
2. Installer awaits OBB copy before reporting success (OBB-bearing installs race today).
3. Biometric-gate option for uninstall / destructive installer ops (parity with InstallerX).
4. Confirm BouncyCastle 1.84 actually patches the full 2026 CVE line; bump if not.
5. (Already roadmapped, still open) ApplicationStartInfo "why did this app start" panel — now
   reinforced by fresh upstream demand (#1984).
6. (Already roadmapped, still open) backup restore data-loss rollback, master-key verify,
   commit() atomicity; dedicated freeze surface + widget; scheduled cache-clearing routine.

## Product Map

- **Core workflows**: app-list inspection; app details (components/permissions/app-ops/
  signatures/usage/exit-history/standby-bucket); install/update/split-package handling with
  duplicate + version/signature diff; batch ops; encrypted multi-format backup/restore;
  profiles + routine scheduler + am:// automation; debloating with preset export/import;
  IFW+PM component blocking; tracker/library scanning; Permission Inspector; cross-app Finder;
  file manager; code editor; terminal (Preview).
- **User personas**: privacy-conscious power users, ROM/root tinkerers, corporate device
  maintainers, debloat-and-forget casual users, automation-heavy sysadmins.
- **Platforms/distribution**: Android 21→36 (API 37 audited, not targeted), phone/tablet with
  declared leanback; GitHub Releases/Obtainium (primary), F-Droid/IzzyOnDroid (planned);
  `floss` (offline) + `full` (opt-in network) flavors.
- **Key integrations**: PackageManager/PackageInstaller, AppOps, UsageStats, ActivityManager
  exit/standby APIs, WorkManager, Room, SAF, split APK/APKS/APKM/XAPK parsing, libsu root
  shell, ADB TCP, Shizuku 13.6.0, Dhizuku 2.11.2, OpenPGP (BouncyCastle 1.84), optional
  VirusTotal/Pithus (full flavor), Tasker/automation via am:// + app-manager:// schemes.

## Competitive Landscape

**Upstream App Manager (MuntashirAkon)** — Canonical implementation. v4.1.0 milestone is 95%
(41/43 closed), due 2026-06-21, not yet released (latest public stable is still v4.0.5). Uses
`Feature`/`Bug`/`Priority`/`Severity` labels, **not** `enhancement` (a filter on `enhancement`
returns nothing — relevant for any tooling that mirrors upstream triage). Most important open
item for the fork is **#1948 "Android 17: no/very few apps appear in main page"** — an
enumeration regression with no working answer in the ecosystem. Other live opportunities NG has
*not* yet built: #1973 assistant-launched privileged services/broadcasts (accepted; already
roadmapped here), #1953 reorderable action panel, #1863 finer-grained app-op control. Many
upstream "gaps" (#321 cross-app Finder, dex2oat #1985, FM filter #1964) are **already shipped in
NG**. Learn: upstream's accepted bug queue. Avoid: importing the swallowed-exception patterns
behind stale UI (#1982).

**InstallerX-Revived (wxxsfxyzm)** — Now the bar for the installer lane: biometric-gated
install/uninstall (v2.3.2, Feb 2026), per-profile signature policy, duplicate-package
detection, dex2oat/compile-mode selection, cross-arch install, M3-Expressive redesign. NG
matches duplicate detection + dex2oat + version/signature diffs already; the open delta is
**biometric on uninstall** (NG gates terminal/FM/backup-delete, not uninstall). Avoid the
per-profile signature-policy complexity — niche.

**Hail (aistra0528)** — v1.10.0 (Jan 2026) added multi-tag-per-app, URI-scheme API actions,
launch-as-digital-assistant, KernelSU App Profile, auto-freeze QS tile, and an Xposed
auto-unfreeze hook. NG already ships multi-tag, am:// automation, and a QS freeze tile; the
still-open delta is the **dedicated frozen-apps grid + home-screen widget** (already roadmapped
here). Learn the freeze grid/widget; avoid Xposed dependence.

**Neo-Backup (NeoApplications)** — 8.3.18 (May 2026) was onboarding/prefs/theme hardening with
no headline backup capability; the real battleground is **backup reliability** (the GCM-cipher
class, large-app resilience), which NG has already addressed via per-file IV + per-archive HKDF
key. Learn nothing new on features; the lesson is that reliability > feature count here.

**SD Maid SE (d4rken-org)** — v1.7.1-rc0 (Apr 2026): targetSdk 36, new corpse signatures, and
**accessibility-shortcut accidental-enable warnings tuned for A17 AAPM**. NG already detects
AAPM; the copyable lesson is the *UX of warning users when an accessibility/automation service
is risky under AAPM*. Scheduled cache-clearing remains NG's borrowable pattern (already
roadmapped).

**Shizuku / Dhizuku** — **Stalled, and that is the opportunity.** Shizuku's latest is v13.6.0
(Jul 2025); Dhizuku's is v2.11.2 (Nov 2024). Neither has shipped an Android 17 release. Every
privilege tool in the niche currently has open A17 breakage or no A17 build. Whichever fork
validates and patches its Shizuku/Dhizuku lanes against final A17 first owns the rootless lane.

**Inure (Hamza417) / AppDash** — Analytics-dashboard competitors. NG already computes every
datapoint and an analytics/discovery dashboard is roadmapped (INIT-4b). No new delta.

## Security, Privacy, and Reliability

- **Verified resolved — GCM multi-file nonce reuse (upstream #1958 class)**: `AESCrypto.handleFiles()`
  (`crypto/AESCrypto.java:237-294`) creates a fresh `GCMBlockCipher` per file and derives a
  **unique per-file IV** (v6+, `deriveIvForFile`) plus a **per-archive HKDF key** (v7+,
  `deriveArchiveKey`); `BackupCryptSetupHelper.java:59,88` wires both by metadata version. The
  `docs/audits/2026-05-08-gcm-cipher-reuse-large-backup.md` "not fixed" conclusion is stale —
  do not re-open.
- **Verified bug — installer OBB-copy race**: `apk/installer/PackageInstallerCompat.java:684-689`
  fires `copyObb()` on a background thread (`ThreadUtils.postOnBackgroundThread`) with a
  standing TODO "Wait for this task to finish before returning." For OBB-bearing apps (large
  games) the install can complete/return before OBBs land, so the app can launch without its
  expansion data. (New roadmap item.)
- **Verified open (already roadmapped)** — `RestoreOp.restoreData()` clears app data before
  extraction (`RestoreOp.java:581-598`); `checkMasterKey()` is permanently disabled
  (`RestoreOp.java:303-307`); `BackupItems.commit()` deletes-then-moves (`BackupItems.java:520-547`).
  These remain the backup engine's top reliability debts.
- **Needs live validation — BouncyCastle CVE line**: pinned at 1.84 (`versions.gradle:26`).
  The repo audit claims 1.84 closes CVE-2026-3505 (PGP AEAD chunk-size DoS — reachable via
  OpenPGP backup decrypt), CVE-2026-5588 (PKIX composite-verifier signature bypass — NG does
  not use composite PKIX), and CVE-2026-5598 (FrodoKEM timing — NG does not use FrodoKEM). The
  audit predates some of those CVE publications, so the "covered" claim is unverified. CVE-2026-3505
  is the only one on a reachable path. (New low-priority validation item.)
- **Verified open (already roadmapped)** — `MainViewModel` swallows app-list load failures
  without a retry/support state (upstream #1982); hostile-APK fixture corpus
  (`ApkFile.java:237` FIXME #227); RootService external-storage staging TOCTOU.
- **Platform risk — Android 17 (API 37, near-final)**: confirmed app-enumeration regression
  (upstream #1948); AdvancedProtectionManager hardens sideloading (NG detects it, must keep
  degrading installer lanes gracefully); `ACCESS_LOCAL_NETWORK` runtime permission for LAN
  discovery; read-only native DCL requirement for targetSdk 37; non-system-app KeyStore key cap
  (50k) touching backup key handling; APK Signature Scheme PQC/ML-DSA hybrid block (NG's
  apksig 4.4.0 parses v3.2 safely per the 2026-06 audit). targetSdk stays 36 for now, so the
  read-only-DCL and KeyStore-cap items are not yet active — but the enumeration regression and
  AAPM apply at the OS level regardless of target.
- **Needs live validation**: A17 enumeration behavior on a real/emulated A17 device; Shizuku
  13.6.0 + Dhizuku 2.11.2 lanes on A17; TV/Wear permission-prompt behavior; IzzyOnDroid
  artifact sizes.

## Architecture Assessment

- **Installer OBB copy must be synchronous (or gated) before completion**: the fire-and-forget
  `copyObb()` in `PackageInstallerCompat.java` should be awaited (or the install result should
  reflect OBB-copy status) so OBB apps are not reported installed before their data is present.
  The adjacent `FIXME: Needed only for one user?` (line 686) should also be resolved while the
  multi-user OBB path is touched.
- **Biometric gate should extend to uninstall**: `crypto/auth/ActionAuthGate` already gates
  terminal/FM/backup-delete via `BiometricPrompt`. Routing the uninstall (and optionally batch
  uninstall) destructive path through the same gate, behind the existing privacy toggle,
  matches InstallerX and closes a destructive-op asymmetry.
- **A17 enumeration path needs a device-validated fallback**: the app-list query path
  (`MainViewModel` → `PackageManagerCompat.getInstalledPackages`/`queryIntent*`) should be
  validated on A17; upstream #1948 suggests a behavior change empties the result. The fix is
  likely a flag/permission/query-filter adjustment, but it is device-gated and must not be
  patched blind.
- **Local privileged channel transport**: `servermanager/LocalServerManager.java:350` carries a
  TODO to use a per-session SSL cert. Lower value than the already-roadmapped HMAC mutual-auth
  port (the channel is loopback), but worth folding into that channel-hardening change rather
  than tracking separately.
- **Tests**: 354 unit tests; privileged packages (`ipc/`, `logcat/`, `magisk/`,
  `servermanager/`, `intercept/`) remain thin. Backup round-trip + hostile-archive integration
  suites are already roadmapped; an A17 enumeration smoke test on the emulator runner would
  ride the existing `android17-emulator.yml`.

## Rejected Ideas

- **"GCM nonce reuse" backup fix (upstream #1958)**: Rejected — already resolved via per-file
  IV (v6) + per-archive HKDF key (v7); `crypto/AESCrypto.java` + `BackupCryptSetupHelper.java`.
  The 2026-05-08 audit doc is stale. Source: upstream #1958; repo code.
- **Cross-app "Finder" / global query (upstream #321)**: Rejected — already shipped with 32
  filter dimensions including permissions, app-ops, trackers, signatures, tags
  (`filters/options/`). NG leapfrogs upstream's 4-year-old request.
- **dex2oat / compile-mode control (upstream #1985)**: Rejected — already present
  (`apk/dexopt/DexOptOptions.java`, `DexOptimizer.java`).
- **File-manager directory filter (upstream #1964)**: Rejected — already present
  (`fm/FmFragment.java` SearchView).
- **IFW+PM dual-mode component blocking (Blocker pattern)**: Rejected — already present
  (`ComponentRule.COMPONENT_BLOCKED_IFW_DISABLE` applies both).
- **Installer duplicate-package detection (InstallerX pattern)**: Rejected — already present
  with version + signing-cert diff (`PackageInstallerActivity`, `apk/whatsnew/ApkWhatsNewFinder`).
- **Reorderable/horizontal action panel (upstream #1953)**: Rejected — UI customization, which
  is off-axis from NG's control/trust identity; low value for the churn.
- **PQC/ML-DSA hybrid-signature display feature**: Rejected as a net-new item — NG already has
  `apk/signing/SigSchemes.java` + signature display, and the 2026-06 audit verified v3.2 hybrid
  parses safely with apksig 4.4.0. No evidence of an absent surface to build.
- **URI-scheme automation / assistant-launch as new work**: Rejected as net-new — am:// +
  app-manager:// automation with backup/restore/install/component/profile/scan actions already
  ships; the assistant-launched *privileged* services case (#1973) is already roadmapped.
- **Compose rewrite / Material 1.14.0**: Rejected — repo keeps Views; 1.14.0 raises minSdk to
  23 against the minSdk-21 ceiling policy.
- **Cloud backup/sync, theme engine, Play-Store price tracking, tracker-rule cloud sync**:
  Rejected — contradict local-first/control identity (carried from prior passes).

## Sources

Upstream and OSS:
- https://github.com/MuntashirAkon/AppManager/milestones
- https://github.com/MuntashirAkon/AppManager/issues/1948
- https://github.com/MuntashirAkon/AppManager/issues/1958
- https://github.com/MuntashirAkon/AppManager/issues/1967
- https://github.com/MuntashirAkon/AppManager/issues/1973
- https://github.com/MuntashirAkon/AppManager/issues/1982
- https://github.com/MuntashirAkon/AppManager/issues/1984
- https://github.com/MuntashirAkon/AppManager/issues/1985
- https://github.com/MuntashirAkon/AppManager/issues/1953
- https://github.com/MuntashirAkon/AppManager/issues/321
- https://github.com/wxxsfxyzm/InstallerX-Revived/releases
- https://github.com/aistra0528/Hail/releases/tag/v1.10.0
- https://github.com/NeoApplications/Neo-Backup/releases
- https://github.com/d4rken-org/sdmaid-se/releases
- https://github.com/RikkaApps/Shizuku
- https://github.com/iamr0s/Dhizuku/releases

Android platform and policy:
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/about/versions/17/behavior-changes-all
- https://developer.android.com/about/versions/17/features
- https://developer.android.com/reference/android/app/ApplicationStartInfo
- https://developer.android.com/reference/android/security/advancedprotection/AdvancedProtectionManager

Advisories:
- https://app.opencve.io/cve/?vendor=bouncycastle
- https://www.cvedetails.com/vendor/7637/Bouncycastle.html
- https://developer.android.com/privacy-and-security/risks/zip-path-traversal

## Open Questions

- Does the A17 app-enumeration regression (#1948) reproduce in AppManagerNG on an API-37
  emulator, and is the cause a query-filter, permission, or behavior-change issue? (Blocks the
  fix approach for the top item — device-gated.)
- Do Shizuku 13.6.0 and Dhizuku 2.11.2 bind successfully on Android 17, or do their stalled
  releases break NG's rootless lanes? (Determines whether NG must ship a compat shim.)
- Does BouncyCastle 1.84 actually contain the patches for CVE-2026-3505/5588/5598, or only the
  earlier CVE-2026-0636 line? (Determines whether a dependency bump is required.)
