<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Architecture: hidden-API bypass

AppManagerNG reaches deep into `@hide`-annotated Android APIs that ordinary apps can't
call. This document maps the four-layer machinery that makes those calls compile, link,
and actually execute on a real device — and the constraints that future contributors
need to respect when adding to it.

The hidden-API surface is the **single most-fragile architectural choice in NG**.
Upstream's maintainer estimated *"a migration to a new version of Android roughly takes
80 hours alone as it is necessary to revise entire hidden API library"* ([S137]). This
doc exists so that future contributors can find the load-bearing pieces fast.

**Source roots:**
- [`hiddenapi/`](../../hiddenapi/) — hand-curated stub declarations of Android `@hide` APIs (`compileOnly` source set, ~80 files).
- [`hiddenapi/src/main/java/misc/utils/HiddenUtil.java`](../../hiddenapi/src/main/java/misc/utils/HiddenUtil.java) — the throw-away stub bodies that the compiler eats and the runtime never executes.
- [`hiddenapi/README.md`](../../hiddenapi/README.md) — AOSP source-pull instructions (use `android-latest-release` branch only, per [S204]).
- `compat/` — runtime wrappers that catch reflection failures, branch on SDK level, and provide a stable Java API to the rest of `app/`.
- `hiddenapibypass` library — LSPosed's [AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass) `6.1`, pinned in `versions.gradle`.
- `dev.rikka.tools.refine` Gradle plugin (`refine_version = "4.0.0"`) — strips the stub method bodies at compile time and rewrites bytecode to call into real `@hide` symbols at runtime.

---

## 1. The four layers

```
                  app/ source                Layer 4 — call sites
                       │
                       ▼ uses
              compat/*Compat.java            Layer 3 — runtime wrappers
                       │
                       ▼ reflects against
              hiddenapi/*.java stubs         Layer 2 — compile-time stubs
                       │
                       ▼ rewritten by
       dev.rikka.tools.refine + AOSP runtime Layer 1 — bytecode + system
```

### Layer 1 — `dev.rikka.tools.refine` + the AOSP runtime

The compile-time output (an `.aar` containing AM's classes) is processed by the
`refine` plugin. The plugin recognizes the `@RefineAs(...)` annotations on the stub
classes and rewrites bytecode so that, at runtime, a call to e.g. the stub
`PackageInfoHidden.someMethod()` resolves against the **real** AOSP `PackageInfo.someMethod()`
hidden-API.

In parallel, `AndroidHiddenApiBypass 6.1` is loaded at app startup and disables the
post-API-28 reflection blocklist for AM's own UID — without it, even successfully-resolved
hidden methods throw `NoSuchMethodError` at the first call from API 28+. Bypass is
applied once in [`AppManager.onCreate()`](../../app/src/main/java/io/github/muntashirakon/AppManager/AppManager.java).

### Layer 2 — `hiddenapi/` stubs

Every `@hide` API NG touches is declared as a stub source file under
[`hiddenapi/src/main/java/`](../../hiddenapi/src/main/java/) in the **same package** as
the real Android class. Stubs are organised by Android subsystem:

| Subsystem | Files |
|---|---|
| `android.app` | `ActivityManagerNative`, `ActivityThread`, `AppOpsManagerHidden`, `IActivityManager`, `IApplicationThread`, `INotificationManager`, `IServiceConnection`, `IUriGrantsManager`, `ContentProviderHolder`, `ContextImpl`, `GrantedUriPermission`, `ProfilerInfo`, `usage/IStorageStatsManager`, `usage/IUsageStatsManager`, `backup/IBackupManager` |
| `android.content` | `Context`, `IContentProvider`, `IIntentReceiver`, `IIntentSender`, `IntentHidden`, `om/IOverlayManager`, `om/OverlayInfoHidden` |
| `android.content.pm` | `ApplicationInfoHidden`, `IPackageManager`, `IPackageManagerN`, `IPackageInstaller`, `PackageInstallerHidden`, `IPackageInstallerSession`, `IPackageDeleteObserver`/`2`, `IPackageInstallObserver2`, `KeySet`, `PackageCleanItem`, `PackageInfoHidden`, `ParceledListSlice`, `permission/SplitPermissionInfoParcelable`, `SuspendDialogInfo`, `UserInfo`, `VerifierDeviceIdentity`, `verify/domain/IDomainVerificationManager` |
| `android.hardware.input` | `IInputManager`, `InputManagerHidden` |
| `android.miui` | `AppOpsUtils` |
| `android.net` | `IConnectivityManager`, `ConnectivityManagerHidden`, `INetworkPolicyListener`, `INetworkPolicyManager`, `NetworkPolicyManager`, `INetworkStatsService`, `INetworkStatsSession`, `NetworkStats`, `NetworkTemplate` |
| `android.os` | `IBinderHidden`, `IDeviceIdleController`, `IUserManager`, `ResultReceiver`, `SELinux`, `ServiceManager`, `ServiceSpecificException`, `ShellCallback`, `SystemProperties`, `UserHandleHidden`, `storage/IMountService`, `storage/IStorageManager`, `storage/StorageManagerHidden`, `storage/StorageVolumeHidden` |
| `android.permission` | `IOnPermissionsChangeListener`, `IPermissionManager` |
| `android.provider` | `SettingsHidden` |
| `android.system` | `OsHidden`, `StructPasswd` |
| `android.util` | `TypedXmlPullParser`, `TypedXmlSerializer`, `XmlHidden` |
| `com.android.internal.app` | `IAppOpsActiveCallback`, `IAppOpsCallback`, `IAppOpsNotedCallback`, `IAppOpsService` |
| `com.android.internal.os` | `PowerProfile` |
| `com.android.internal.telephony` | `IPhoneSubInfo`, `ISub` |
| `com.android.org.conscrypt` | `Conscrypt` |
| `misc.utils` | `HiddenUtil` — throw-stub bodies the compiler accepts |

[`hiddenapi/README.md`](../../hiddenapi/README.md) specifies the **AOSP source-pull
target** when refreshing these stubs: clone `--branch android-latest-release` from
`https://android.googlesource.com/platform/frameworks/base.git`. Do **not** use
`master`/`main`/`android-mainline` or a date-stamped tag, because AOSP switched to a
Q2+Q4 publishing cadence in 2026 ([S204]) and the trunk branches are mid-cycle snapshots
whose private-API surface may not survive to a published Android release.

### Layer 3 — `compat/*Compat.java` wrappers

The compat layer is the only thing the rest of `app/` sees. Each `*Compat.java` wraps a
particular subsystem's hidden APIs with:

1. **SDK-level branching** — e.g. `PackageManagerCompat` calls method A on Android 11 and method B on Android 13+ because the AOSP-side method shape changed.
2. **Reflection-failure catching** — every `IllegalReflectiveAccess` / `NoSuchMethodError` / `NoSuchFieldException` becomes a sane fallback or a logged-and-no-op.
3. **A stable Java API** for the rest of the app — `app/` never imports anything from `hiddenapi/`.

Examples currently in [`compat/`](../../app/src/main/java/io/github/muntashirakon/AppManager/compat/):
`ActivityManagerCompat`, `AppOpsManagerCompat`, `ApplicationInfoCompat`, `BackupCompat`,
`BinderCompat`, `BiometricAuthenticatorsCompat`, `ConnectivityManagerCompat`,
`DeviceIdleManagerCompat`, `DomainVerificationManagerCompat`, `InputManagerCompat`,
`InstallSourceInfoCompat`, `ManifestCompat`, `NetworkPolicyManagerCompat`,
`NetworkStatsCompat`, `NetworkStatsManagerCompat`, `OverlayManagerCompact` (sic),
`PackageInfoCompat2`, `PackageManagerCompat`, `PermissionCompat`, `ProcessCompat`,
`SensorServiceCompat`, `StorageManagerCompat`, `SubscriptionManagerCompat`,
`ThumbnailUtilsCompat`, `UriCompat`, `UsageStatsManagerCompat`,
`VirtualDeviceManagerCompat`.

### Layer 4 — call sites

The rest of `app/` uses `compat/` and is mostly unaware of the reflection underneath.
The `app/` package tree treats each `*Compat` like an ordinary library API.

---

## 2. The Android-version migration cliff

Every new Android release potentially:

- Removes a hidden method NG depended on (e.g. Android 16 QPR2 removing `clearApplicationUserData` — see ROADMAP iter-19 [S137]).
- Changes a hidden method's signature (extra args, return type, exceptions).
- Adds a hidden field NG could/should consume.
- Changes the reflection-blocklist enforcement (Android 17's static-final-field reflection ban — see [`docs/audits/2026-05-08-android17-static-final-reflection.md`](../audits/2026-05-08-android17-static-final-reflection.md)).

The existing audit doctrine catches some of this (a per-version audit file lands when
NG bumps `targetSdk`), but the audit was **manual** until the pass-29 Hidden-API
Compatibility Harness shipped:

- `scripts/generate-hidden-api-baseline.ps1` parses `hiddenapi/src/main/java` and
  regenerates `app/src/androidTest/assets/api/api-versions-appmanagerng-hiddenapi.json`.
- `HiddenApiDescriptorBaselineTest` verifies the checked-in baseline still covers every
  hiddenapi source file after a stub refresh.
- `HiddenApiCompatibilityInstrumentedTest` loads that baseline at instrumented-test time,
  applies HiddenApiBypass exemptions, reflectively probes active-SDK hidden
  classes/methods/fields, writes `hidden-api-compat-sdk<api>.json`, and fails on
  required missing APIs while reporting deprecated removals as warnings.

Run the generator whenever a hiddenapi stub file is added, removed, or refreshed from
AOSP. The runtime test is device/SDK-specific by design; keep reports from preview and
new-stable Android images with the associated audit notes.

---

## 3. The Android 17 (API 37) compliance batch

Six audits are required before NG bumps `targetSdk = 37`. Status as of 2026-05-17:

| Audit | Verdict | File |
|---|---|---|
| Static-final reflection ban | 1 fix, 1 deferred | [2026-05-08-android17-static-final-reflection.md](../audits/2026-05-08-android17-static-final-reflection.md) |
| `MessageQueue` lock-free impl | clean | [2026-05-02-android17-messagequeue.md](../audits/2026-05-02-android17-messagequeue.md) |
| Per-UID 50K keystore key cap | clean | [2026-05-02-android17-keystore-key-cap.md](../audits/2026-05-02-android17-keystore-key-cap.md) |
| `System.load()` read-only | clean | [2026-05-08-android17-system-load-readonly.md](../audits/2026-05-08-android17-system-load-readonly.md) |
| `ACCESS_LOCAL_NETWORK` runtime perm | **open** | (audit pending) |
| `usesCleartextTraffic` enforcement | **open** | (audit pending) |
| `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` migration | **open** | (audit pending) |
| ECH default-on for TLS | **open** | (audit pending) |
| ML-DSA Keystore OID recognition | **open** | (audit pending) |

The **open** items each need their own audit before the targetSdk bump can land. None
are urgent (Android 17 stable is June 2026); cumulative effort is the largest single
Eng-Debt item on the board.

---

## 4. Constraints for future contributors

When adding a hidden-API call:

1. **Check if the call already lives in `compat/*`.** Don't add a parallel reflection path; extend the existing wrapper.
2. **Add a stub in `hiddenapi/`** if the symbol isn't already declared. Source the stub from the `android-latest-release` AOSP branch per the [`hiddenapi/README.md`](../../hiddenapi/README.md) protocol.
3. **Use `dev.rikka.tools.refine`'s `@RefineAs(...)` annotation** so the stub class binds at runtime to the real AOSP class. Never call `Class.forName(...)` directly when a stub can resolve at compile time — the compiler-resolved path is faster and survives ProGuard better.
4. **Branch on SDK level** in the `compat/` wrapper. Hidden APIs change shape across versions; no contributor should assume a method has the same signature on Android 12 and Android 16.
5. **Catch reflection failures.** Every `IllegalReflectiveAccess`, `NoSuchMethodError`, and `NoSuchFieldException` becomes a fallback path in the wrapper, not an unhandled crash.
6. **Run the audit doctrine** when a new Android version's behavior-change page mentions hidden-API changes. New `docs/audits/<YYYY-MM-DD>-<topic>.md` per [`docs/audits/README.md`](../audits/README.md).

---

## 5. Cross-references

- [`hiddenapi/README.md`](../../hiddenapi/README.md) — AOSP source-pull protocol.
- [`docs/audits/`](../audits/) — every Android-version-behavior-change verdict; 14 audits as of 2026-05-17.
- ROADMAP **iter-19 [S137]** — Hidden-API Compatibility Harness (highest-leverage eng-debt).
- ROADMAP **Engineering Debt Register → Android 17 targetSdk=37 compliance batch**.
- LSPosed AndroidHiddenApiBypass: https://github.com/LSPosed/AndroidHiddenApiBypass (pinned `6.1`).
- Rikka HiddenApiRefinePlugin: https://github.com/RikkaApps/HiddenApiRefinePlugin (pinned `4.0.0`).
- AOSP source: https://android.googlesource.com/platform/frameworks/base.git on `android-latest-release` (per [S204]).
