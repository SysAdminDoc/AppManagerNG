# Research — AppManagerNG

_Pass 5 — 2026-06-14 (post-v0.6.0, same-day re-verification of Pass 4). This pass re-checked
every open Pass-4 claim against current source and live advisories; two of Pass 4's open items
are now **closed by verification** (biometric-on-uninstall already shipped; BouncyCastle 1.84
confirmed to carry the CVE-2026-3505 fix) and one verified-bug class (FGS `specialUse` without
the required subtype property) is newly surfaced. Supersedes the earlier 2026-06-14 snapshot._

## Executive Summary

AppManagerNG is a GPL-3.0-or-later Android package-manager fork (minSdk 21, target 36) on
Android Views + Material 1.13.0, Java/Kotlin, Room, WorkManager, Gradle 9.4.1/AGP 9.2.0,
`floss`/`full` flavors, and root/ADB/Shizuku/Dhizuku privilege lanes. v0.6.0 (versionCode 8)
shipped 2026-06-14. The fork has already closed most of the breadth gaps that distinguish it
from upstream (cross-app Finder, dex2oat control, IFW+PM dual blocking, installer duplicate
detection, per-file-IV/per-archive-HKDF AES backups, A17 static behavior-change audits) — so the
remaining value is **reliability and Android-17 survival, not features**.

This same-day re-verification corrects two Pass-4 conclusions and adds one verified bug:
- **Biometric gate already covers uninstall.** Pass 4 claimed uninstall was ungated; current
  code gates install (`PackageInstallerActivity.java:695`), single-app uninstall
  (`AppInfoFragment.java:2831/2850/2869`, `MainRecyclerAdapter.java:833/912`), batch
  uninstall + batch clear-data (`MainActivity.java:1264-1268`), and clear-data, all through
  `ActionAuthGate` behind the privacy toggle. The InstallerX biometric-parity item is **done**.
- **BouncyCastle 1.84 is the patched line.** bcgit's own CVE-2026-3505 wiki, the GitLab and Red
  Hat advisories confirm the unbounded-PGP-AEAD-chunk DoS affected 1.74–1.83 and is fixed in
  1.84 — NG's pin (`versions.gradle:26`). No bump needed; the verification item is **resolved**.
- **New verified bug — FGS `specialUse` without subtype property.** The manifest declares
  `FOREGROUND_SERVICE_SPECIAL_USE` (`AndroidManifest.xml:54`) and eight services with
  `foregroundServiceType="dataSync|specialUse"` (`:1655-1727`) but declares **no**
  `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property anywhere — the exact configuration
  behind upstream #1978's FOREGROUND_SERVICE crash on Android 16 / OxygenOS 16. NG targets
  SDK 36, so this is live, and the fix (declare the property, or drop the redundant `specialUse`
  since `dataSync` already covers the real use) is headless-safe.

Top opportunities in priority order:
1. FGS `specialUse` subtype property (or drop `specialUse`) — crash on shipping A16 OEM builds.
2. Validate/fix Android 17 app-list enumeration on-device (upstream #1948 — empty/sparse list).
3. Backup reliability trio (still open): restore-clears-data-before-extract rollback, master-key
   verify, `commit()` atomicity.
4. Installer awaits OBB copy before reporting success (OBB-bearing installs still race today).
5. ApplicationStartInfo "why did this app start" panel — reinforced by fresh upstream demand
   (#1984, #1986 still flowing in).
6. Dedicated freeze surface + widget; scheduled cache-clearing routine (carried from Pass 3).

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

**Upstream App Manager (MuntashirAkon)** — Canonical implementation. v4.1.0 milestone is still
unreleased (latest public stable remains v4.0.5; milestone due 2026-06-21). Uses
`Feature`/`Bug`/`Priority`/`Severity` labels, **not** `enhancement`. The most important open item
for the fork remains **#1948 "Android 17: no/very few apps appear in main page"** — an
enumeration regression with no working answer in the ecosystem. Newest issues since the last pass
sharpen the picture: **#1986** (backup fails + "Remove all rules" freezes — the freeze does **not**
apply to NG, see Rejected), **#1978** (FOREGROUND_SERVICE_SPECIAL_USE crash on Android 16/OOS 16 —
**does** apply, see Security), **#1984** (per-app "why did this start" demand — reinforces the
ApplicationStartInfo panel), **#1975** (no wireless-debugging pairing prompt on Quest 3 — folds
into the already-roadmapped wireless-ADB resilience item). Learn: upstream's accepted bug queue.
Avoid: the swallowed-exception-behind-stale-UI pattern (#1982).

**InstallerX-Revived (wxxsfxyzm)** — Was the bar for the installer lane (biometric-gated
install/uninstall, v2.3.2). NG now **matches the biometric bar** (install + single/batch
uninstall + clear-data all gated through `ActionAuthGate`), plus duplicate detection, dex2oat,
and version/signature diffs. No open installer-lane delta remains against InstallerX. Avoid the
per-profile signature-policy complexity — niche.

**Hail (aistra0528)** — v1.10.0 (Jan 2026): multi-tag-per-app, URI-scheme API actions,
launch-as-assistant, KernelSU App Profile, auto-freeze QS tile, Xposed auto-unfreeze. NG already
ships multi-tag, am:// automation, and a QS freeze tile; the still-open delta is the **dedicated
frozen-apps grid + home-screen widget** (already roadmapped). Avoid Xposed dependence.

**Neo-Backup (NeoApplications)** — 8.3.18 (May 2026) was onboarding/prefs/theme hardening with
no headline backup capability; the real battleground is **backup reliability**, which NG has
addressed on the crypto axis (per-file IV + per-archive HKDF) but **not** on the
restore-atomicity axis (data cleared before extract; non-atomic `commit()`). The lesson stands:
reliability > feature count here.

**SD Maid SE (d4rken-org)** — v1.7.1-rc0 (Apr 2026): targetSdk 36, new corpse signatures,
accessibility-shortcut accidental-enable warnings tuned for A17 AAPM. NG already detects AAPM;
the borrowable patterns remain the risky-accessibility-service warning UX and scheduled
cache-clearing (both roadmapped).

**Shizuku / Dhizuku** — **Stalled, and that is the opportunity.** Shizuku's latest is v13.6.0
(Jul 2025); Dhizuku's is v2.11.2 (Nov 2024); neither has shipped an Android 17 release. With A17
stable landing this month (June 2026), whichever fork validates and patches its Shizuku/Dhizuku
lanes against final A17 first owns the rootless lane.

## Security, Privacy, and Reliability

- **New verified bug — FGS `specialUse` without subtype property**: `AndroidManifest.xml:54`
  declares `FOREGROUND_SERVICE_SPECIAL_USE` and `:1655-1727` declare eight services with
  `foregroundServiceType="dataSync|specialUse"`, but no `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`
  `<property>` is declared. This matches upstream #1978's crash configuration on Android 16/
  OxygenOS 16. Confidence: bug Verified (property absent); on-device crash repro Likely (device-
  gated). Fix is headless-safe and independently correct (Play requires the property for
  `specialUse`). (New P1 roadmap item.)
- **Verified open — installer OBB-copy race**: `apk/installer/PackageInstallerCompat.java:684-690`
  fires `copyObb()` via `ThreadUtils.postOnBackgroundThread` (fire-and-forget) with a standing
  TODO "Wait for this task to finish before returning" and an adjacent FIXME "Needed only for one
  user?". OBB-bearing apps can be reported installed before expansion data lands. (Roadmapped.)
- **Verified open — backup reliability trio**: `RestoreOp.restoreData()` calls
  `clearApplicationUserData` at `RestoreOp.java:584` before extraction with no rollback;
  `checkMasterKey()` is permanently disabled (`RestoreOp.java:304-306`, `if (true) { return; }`);
  `BackupItems.commit()` deletes the old backup before moving temp→final (`BackupItems.java:526-530`).
  These remain the backup engine's top reliability debts. (All roadmapped.)
- **Resolved — biometric gate on destructive installer ops**: `ActionAuthGate` now gates install,
  single + batch uninstall, and clear-data behind the privacy toggle (call sites above). The
  Pass-4 "uninstall ungated" gap is closed.
- **Resolved — BouncyCastle CVE line**: 1.84 (`versions.gradle:26`) is the fixed release for
  CVE-2026-3505 (PGP AEAD chunk-size DoS, reachable via OpenPGP backup decrypt); CVE-2026-5588
  (PKIX composite verifier — NG unaffected) and CVE-2026-5598 (FrodoKEM timing — NG unaffected)
  are not on reachable paths. No bump required. Confidence: Verified (bcgit wiki + GitLab/Red Hat
  advisories).
- **Verified open (already roadmapped)** — `MainViewModel` reports app-list load failure with no
  retry/support affordance (`MainViewModel.java:633-640`; upstream #1982); hostile-APK fixture
  corpus (`ApkFile.java:236` FIXME #227); RootService external-storage staging TOCTOU.
- **Platform risk — Android 17 (API 37, stable this month)**: confirmed app-enumeration regression
  (#1948); AdvancedProtectionManager sideload hardening (NG detects it); `ACCESS_LOCAL_NETWORK`
  runtime permission for LAN discovery; certificate-transparency-by-default for targetSdk 37
  (touches `full`-flavor network endpoints if/when NG retargets); read-only native DCL and the
  non-system KeyStore key cap (target-37-gated, inactive at target 36). The enumeration regression
  and AAPM apply at OS level regardless of target.

## Architecture Assessment

- **FGS manifest hygiene**: declare `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` on the `specialUse`
  services, or remove `specialUse` from `foregroundServiceType` (the real workloads — backup,
  install, batch ops — are `dataSync`). Either way removes the #1978 crash class and the Play
  policy gap. (`AndroidManifest.xml`.)
- **Installer OBB copy must be synchronous (or status-gated)** before completion; resolve the
  adjacent multi-user FIXME (`PackageInstallerCompat.java:686`) in the same change.
- **A17 enumeration path needs a device-validated fallback**: `MainViewModel` →
  `PackageManagerCompat.getInstalledPackages`/`queryIntent*` should be validated on an API-37
  emulator (upstream #1948). Device-gated; do not patch blind.
- **Privileged-server startup uses fixed sleeps, not readiness probes**:
  `LocalServerManager.java:262,326` sleep a hardcoded ~3s after start/stop with no readiness
  check or retry — flaky on slow/embedded devices. Fold a bounded readiness poll into the
  already-roadmapped channel-hardening work, or track as its own small reliability fix.
  (`LocalServerManager.java:350` also carries the per-session SSL TODO — lower value, loopback.)
- **OEM installer-flag completeness**: `PackageInstallerCompat.java:1198` carries a
  `TODO: Check for HyperOS?` — Xiaomi HyperOS isn't in the installer-source detection, so install
  flags/labels can be wrong on HyperOS devices. Small, self-contained.
- **Tests**: ~354 unit tests; privileged packages (`ipc/`, `logcat/`, `magisk/`,
  `servermanager/`, `intercept/`) remain thin. Backup round-trip + hostile-archive integration
  suites are roadmapped; an A17 enumeration smoke test would ride the existing
  `android17-emulator.yml`.

## Rejected Ideas

- **Biometric-gate uninstall as net-new work**: Rejected — already shipped; `ActionAuthGate`
  gates install + single/batch uninstall + clear-data. Source: code (`MainActivity.java:1264`,
  `AppInfoFragment.java:2831`, `MainRecyclerAdapter.java:833`).
- **Bump BouncyCastle off 1.84**: Rejected — 1.84 is the CVE-2026-3505 fix release. Source: bcgit
  CVE-2026-3505 wiki; GitLab/Red Hat advisories; `versions.gradle:26`.
- **"Remove all rules" freeze fix (upstream #1986)**: Rejected — NG already runs `removeAllRules`
  on a background thread (`MainPreferencesViewModel.java:180`), so the upstream main-thread freeze
  does not reproduce here; only a progress affordance is missing (minor). Source: #1986; code.
- **"GCM nonce reuse" backup fix (upstream #1958)**: Rejected — resolved via per-file IV (v6) +
  per-archive HKDF key (v7); `crypto/AESCrypto.java`, `BackupCryptSetupHelper.java`.
- **Cross-app Finder (#321), dex2oat (#1985), FM directory filter (#1964), IFW+PM dual blocking,
  installer duplicate detection (InstallerX), reorderable action panel (#1953), PQC/ML-DSA
  display, URI-scheme automation as net-new**: Rejected — all already shipped or off-axis (see
  Pass-4 record; unchanged).
- **Compose rewrite / Material 1.14.0**: Rejected — repo keeps Views; 1.14.0 raises minSdk to 23
  against the minSdk-21 ceiling policy.
- **Cloud backup/sync, theme engine, price tracking, tracker-rule cloud sync**: Rejected —
  contradict local-first/control identity.

## Sources

Upstream and OSS:
- https://github.com/MuntashirAkon/AppManager/milestones
- https://github.com/MuntashirAkon/AppManager/issues/1948
- https://github.com/MuntashirAkon/AppManager/issues/1978
- https://github.com/MuntashirAkon/AppManager/issues/1982
- https://github.com/MuntashirAkon/AppManager/issues/1984
- https://github.com/MuntashirAkon/AppManager/issues/1986
- https://github.com/MuntashirAkon/AppManager/issues/1975
- https://github.com/wxxsfxyzm/InstallerX-Revived/releases
- https://github.com/aistra0528/Hail/releases/tag/v1.10.0
- https://github.com/NeoApplications/Neo-Backup/releases
- https://github.com/d4rken-org/sdmaid-se/releases
- https://github.com/RikkaApps/Shizuku
- https://github.com/iamr0s/Dhizuku/releases

Android platform and policy:
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/about/versions/17/behavior-changes-all
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/reference/android/app/ApplicationStartInfo
- https://developer.android.com/reference/android/security/advancedprotection/AdvancedProtectionManager

Advisories:
- https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902026%E2%80%903505
- https://advisories.gitlab.com/maven/org.bouncycastle/bcpg-jdk18on/CVE-2026-3505/
- https://www.bouncycastle.org/resources/new-releases-bouncy-castle-java-1-84-and-bouncy-castle-java-lts-2-73-11/
- https://developer.android.com/privacy-and-security/risks/zip-path-traversal

## Open Questions

- Does the A17 app-enumeration regression (#1948) reproduce in AppManagerNG on an API-37
  emulator, and is the cause a query-filter, permission, or behavior-change issue? (Blocks the
  fix approach for the top device-gated item.)
- Do Shizuku 13.6.0 and Dhizuku 2.11.2 bind successfully on Android 17, or do their stalled
  releases break NG's rootless lanes? (Determines whether NG must ship a compat shim.)
- Does the missing `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` actually crash a backup/install FGS on a
  real OxygenOS 16 device (as #1978 reports), or only fail Play submission? (Affects priority, not
  whether to fix — the property/drop change is correct either way.)
