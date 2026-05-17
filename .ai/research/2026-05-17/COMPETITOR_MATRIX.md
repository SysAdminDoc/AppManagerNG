<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# COMPETITOR_MATRIX — 2026-05-17

**This file is a pointer + delta, not a re-derivation.** The full competitor landscape
for AppManagerNG is mined exhaustively in `ROADMAP.md` (Iter-18 through Iter-23
research deltas) and in the four research files under `docs/research/`. Re-running a
fresh competitive sweep against the same set today would duplicate that work.

This file gives:

1. The canonical competitor list, with primary positioning notes
2. Where in the ROADMAP each competitor's features were mined
3. A small handful of competitors not in the primary sweep that may be worth touching
4. Stars snapshot (from ROADMAP S291 → S302 / S312 → S315, 2026-05-16)

---

## 1. Direct competitors (Android on-device power tools, FOSS)

| Competitor | Stars (2026-05-16) | Primary positioning | Mined under (ROADMAP) | NG action |
|---|---:|---|---|---|
| **`MuntashirAkon/AppManager`** | 8,025 | The upstream NG is forked from; the canonical implementation | `Upstream Sync Strategy`; pull-policy section | Pull security + bug fixes immediately; pull upcoming features (Finder, APK editing, Routine Ops, Crash Monitor, Database Viewer, Terminal) when upstream ships; reimplement only after 12-month stall. |
| **`samolego/Canta`** | 4,780 | Rootless Shizuku debloater + UAD-NG list integration | T5, T7, T8 rows; iter-18, iter-19, iter-20 deltas; [S19, S43, S140] | Match Debloat Presets (✅ shipped 2026-05-14); match cross-user verification (open in T7); match factory-reset before uninstall (✅ shipped). NG is broader-scope than Canta — Canta is debloater-only. |
| **`Universal-Debloater-Alliance/UAD-NG`** | 6,606 | Cross-platform desktop debloater + curated `uad_lists.json` (5,357 entries, GPL-3.0) | T7 / Debloat tier; [S18, S67, S113, S134, S212, S213] | Auto-fetch debloat definitions (open in T7); UAD-NG dataset is **the** dependency graph reference. Multi-device fleet orchestration is rejected — UAD-NG is a desktop tool, NG is on-device. |
| **`aistra0528/Hail`** | 5,821 | App freezer with QS-tile + Digital-Assistant launch + multi-tag + URI automation | T5, T8, T9; iter-18, iter-19, iter-20; [S65, S130, S144, S145, S146, S183, S246, S294] | Match URI automation API (open in T8); match Auto-Freeze QS tile (open in T8); match multi-tag (open in T8); reject AccessibilityService-based auto-freeze (security/policy posture). |
| **`Hamza417/Inure`** | 1,757 | Polished M3 Expressive AM-class tool with Logs + Analytics + Shizuku/root app management | iter-18, iter-19; [S66, S131, S270, S295] | UI polish reference; match AppOps IGNORE flag (✅ shipped 2026-05-08); MiUI ops mappings (open in T9); native-lib sizes (✅ shipped 2026-05-03); batch APK installer (open in T11). |
| **`d4rken-org/sdmaid-se`** | 6,740 | Storage cleaner + scheduler + corpse finder + Shizuku integration | T8, T19, T20; [S24, S42, S112, S133, S273, S296] | Adopt CorpseFinder model in T19 storage analyzer (open); adopt scheduler-auto-fix-battery-optimization (✅ shipped 2026-05-02); CI workflow templates (open — CI smoke pack pending). |
| **`NeoApplications/Neo-Backup`** | 3,614 | The Android backup-tool benchmark | T6; [S20, S41, S114, S135, S142, S190 → S193, S229, S297] | Match KeepAndroidOpen banner (✅ shipped 2026-05-02); match backup-sharing (✅ shipped 2026-05-16); match existing-tag suggestions (✅ shipped 2026-05-08); match launcher shortcuts for schedules (open). PGP/AES-256 already shipped (closed in 2026-05-16 hygiene pass). |
| **`XayahSuSuSu/Android-DataBackup`** | 6,690 | Multi-cloud + SMB backup destinations | T6; [S79, S298] | Reference for SMB / WebDAV backup destination (open in T6). |
| **`RikkaApps/Shizuku`** | 25,130 | The rootless-privilege provider | T5 throughout; [S22, S121, S139, S178, S179, S180, S181, S209, S210, S226, S232, S299] | Core dep; pinned `shizuku_version = 13.1.5`. Hidden-Shizuku fork detection (open in T5 [S139, S152]); Sui detection (✅ shipped via onboarding sheet 2026-05-08); Auto-revoke warning on data-clear (open). |
| **`LibChecker/LibChecker`** | 6,866 | APK + native-lib analyzer | T9, T12; [S72, S245, S300] | Reference for permission-provider attribution (open in T9); per-lib 16 KB page-alignment indicator (open in T12). LibChecker ships a flavor split (FOSS vs full) that NG should mirror — inverse direction (FOSS default). |
| **`soupslurpr/AppVerifier`** | 1,046 | APK fingerprint verifier — NG publishes its fingerprint for AppVerifier to consume | Distribution; [S63, S90, S301] | Integration pattern, not competitor. NG publishes at `docs/fingerprints.txt` per AppVerifier's record format. |
| **`ImranR98/Obtainium`** | 17,115 | Multi-source update tracker | T1; [S26, S51, S200, S302] | Distribution channel; NG ships `obtainium-config.json` for direct import. **Update tracking is delegated to Obtainium**, not reimplemented in NG (Rejected row). |
| **`lihenggui/blocker`** | (n/a snapshot) | Visual IFW rule editor + community debloat ruleset | T9 iter-19; [S155] | Match IFW visual editor (open in T9). |
| **`deltazefiro/Amarok-Hider`** | (n/a snapshot) | Per-app `pm hide` toggle | T7 iter-19; [S162] | Match `pm hide` toggle (open in T7). |
| **`BinTianqi/OwnDroid`** | (n/a snapshot) | Embedded Dhizuku DPM | T9 iter-19; [S158] | Reference for embedded DPM mode (open / under-consideration). |
| **`wxxsfxyzm/InstallerX-Revived`** | (n/a snapshot) | Biometric install gate + LSPosed install-progress hook | T9 iter-19; [S159, S197] | Match biometric install gate (open in T9). |
| **`SanmerApps/PI`** | (n/a snapshot) | Default-installer override | T11 UC; [S160] | Architectural pattern (open under-consideration). |
| **`DUpdateSystem/UpgradeAll`** | (n/a snapshot) | Pluggable getter-API for updates | T11; [S163] | Reference for plugin contract if NG ever ships update tracking (currently rejected). |
| **`VegaBobo/Language-Selector`** | (n/a snapshot) | Per-app locale via Shizuku | T9 iter-19; [S164, S269] | Match per-app locale write path (open in T9). |
| **`pass-with-high-score/universal-installer`** | (n/a) | VirusTotal + split APK + Shizuku silent install | iter-20; [S199] | Reference for installer dependency check (✅ uses_library / minSdk check shipped 2026-05-16 in `InstallDependencyChecker`). |
| **`yume-chan/VolumeManager`** | (n/a) | Per-app volume via Shizuku-driven AppOps | iter-19; [S196] | Reference for AppOps per-UID precision (open in T9 [S37]). |
| **`AhmetCanArslan/ShizuWall`**, **`dorumrr/de1984`**, **`shynoiddev/FireWall-Blocks`** | (n/a) | Shizuku-iptables per-app firewall | iter-19 [S156, S157] | **Rejected as core feature** — conflicts with "audit and govern apps, not proxy their traffic" stance. |

---

## 2. Adjacent / inspirational (non-competitor but cited)

These are pulled from ROADMAP's Premium Polish Track and Material 3 Adaptive references:

| Project | Why cited | ROADMAP source |
|---|---|---|
| `LawnchairLauncher/Lawnchair` | Nova Launcher backup restoration model | [S86] |
| `Mullvad/mullvadvpn-app` | Two-column landscape, in-app language selector | [S110] |
| `ProtonMail/android-mail` | Premium discreet icon mode, M3 patterns | [S111] |
| `OxygenCobalt/Auxio` | Material Design refresh, native tag parsing (Musikr) speed | [S109] |
| `android/nowinandroid` | M3 reference app | [S108] |
| `SkyD666/PodAura` | M3 Expressive migration tracker | [S118] |
| `bcgit/bc-java`, `google/gson`, `Google/perfetto`, `square/leakcanary`, `frida/frida`, `skylot/jadx`, `iBotPeaches/Apktool`, `REAndroid/APKEditor` | Tooling / deps | [S50, S58, S97, S96, S93, S94, S128, S89] |
| `MMRLApp/MMRL` | Magisk module distribution channel | [S223, S276] |
| `KDE/kdeconnect-android` | Cross-device API pattern | [S278] |

---

## 3. Commercial-product references (decidedly different model)

| Product | Why noted | ROADMAP |
|---|---|---|
| **ADB AppControl** (desktop) | Demand signal for low-friction desktop debloat | [S303] |
| **AppDash** (Play, paid) | App history, tags/notes | [S304] |
| **Swift Backup** (Play, paid) | Polished scheduled-backup destination UX | [S305] |
| **Titanium Backup** (legacy / root) | Long-running batch backup reference | [S306] |
| **GeminiMan WearOS Manager** (Play, closed-source) | Sole player in phone-side Wear OS package management | [S265] — opportunity flagged as "first FOSS competitor" in T18 Watch theme |

All five are **explicitly rejected** as scope for NG core (different product model: desktop / paid / closed-source). They inform feature priorities, not implementation paths.

---

## 4. Competitors *not* deeply mined but possibly worth a probe in iter-24+

Saturation check on the existing register found three under-represented categories. Whether any of these matters is a judgment call for the maintainer.

| Category | Closest competitor (in register) | Possible probe targets |
|---|---|---|
| **Enterprise MDM beyond Dhizuku** | Dhizuku [S71], OwnDroid [S158], Island [S78, S105] | `gpicchio/Headwind-MDM`, `airgap-it/airgap` (deep enterprise context; likely off-mission). |
| **Hardware attestation** | KeyAttestation [S73] | `GrapheneOS/Auditor` v52+ (Auditor is the most-current attestation app; only briefly referenced). |
| **Watch / Auto** | GeminiMan [S265] (closed-source, only Wear OS) | Nothing on Android Auto / Android Automotive OS surface (deliberately out of scope per [S280]). |

These are **not** recommended for inclusion absent a clear maintainer mandate — adding them widens the maintenance lane without obvious user wins.

---

## 5. Stars / activity snapshot (2026-05-16, from ROADMAP S291 → S302)

Sorted by stars; only the ones already snapshotted in ROADMAP. Numbers are point-in-time
and will drift; use this as a relative-ranking reference, not a current dashboard.

```
Shizuku            25,130 ★    25k  ▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰
Obtainium          17,115 ★    17k  ▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰
AppManager          8,025 ★     8k  ▰▰▰▰▰▰▰▰
LibChecker          6,866 ★     7k  ▰▰▰▰▰▰▰
SD Maid SE          6,740 ★     7k  ▰▰▰▰▰▰▰
Android-DataBackup  6,690 ★     7k  ▰▰▰▰▰▰▰
UAD-NG              6,606 ★     7k  ▰▰▰▰▰▰▰
Hail                5,821 ★     6k  ▰▰▰▰▰▰
Canta               4,780 ★     5k  ▰▰▰▰▰
Neo Backup          3,614 ★     4k  ▰▰▰▰
Inure               1,757 ★     2k  ▰▰
AppVerifier         1,046 ★     1k  ▰
NG (today)            ~ 0 ★     ?
```

NG is a brand-new project (2026-04-30 bootstrap) and has not yet been listed publicly on
IzzyOnDroid / F-Droid / Accrescent (open in T1). Star-count will not be a useful signal
for several quarters; ROADMAP captures the right strategy: distribution gating on
reproducible builds (✅ shipped 2026-05-14, hardened 2026-05-16) and rename being public
(✅ done — repo lives at `SysAdminDoc/AppManagerNG`).
