# AppManagerNG Intent / URI Schema

**Status:** v0.4.x. App-Info short alias `am://app/<pkg>` shipped 2026-05-09; signature-gated broadcast-intent automation surface (`io.github.sysadmindoc.AppManagerNG.action.*`) shipped 2026-05-17; public user-confirmed `am://` automation actions and Tasker-style activity intents shipped 2026-05-18; read-only SAF documents provider and Locale-compatible Tasker plugin broker shipped 2026-06-04.

This file documents how external apps (Tasker, MacroDroid, launcher pinned shortcuts, KDE Connect, custom URLs) should drive or browse AppManagerNG. Five surfaces:

1. **URI deep links** (`app-manager://`, `am://`) — public, dispatched via the launcher. Anyone with the link can fire them; treat them as user-initiated UI navigation.
2. **Public automation activity** ([`AutomationUriActivity`](../app/src/main/java/io/github/muntashirakon/AppManager/automation/AutomationUriActivity.java)) — accepts `am://` operation URIs and `startActivity` intents using the same action constants as the broadcast API. It requires AppManagerNG authentication and a confirmation dialog before privileged work starts.
3. **Broadcast actions** (`io.github.sysadmindoc.AppManagerNG.action.*`) — gated behind a signature-protected permission. Only callers signed with the AppManagerNG release certificate, or an in-app broker, can fire them without the public confirmation UI.
4. **Locale-compatible plugin broker** ([`TaskerPluginEditActivity`](../app/src/main/java/io/github/muntashirakon/AppManager/automation/TaskerPluginEditActivity.java) + [`TaskerPluginFireReceiver`](../app/src/main/java/io/github/muntashirakon/AppManager/automation/TaskerPluginFireReceiver.java)) — Tasker/Locale edit/fire integration that stores signed `am://` automation bundles and routes trusted fires to the in-app receiver.
5. **SAF documents provider** (`${applicationId}.documents`) — read-only Android file-picker roots for AppManagerNG-managed backups and profiles.

---

## URI deep links

### `app-manager://details?id=<pkg>&user=<uid>`

Open the App Info / App Details screen for a given installed package.

| Query param | Required | Format |
|-------------|----------|--------|
| `id` | yes | Package name, validated by `PackageUtils.validateName(...)`. |
| `user` | no | User ID (digits-only). Defaults to current user. |

Example:

```
app-manager://details?id=com.android.chrome
app-manager://details?id=com.android.chrome&user=10
```

### `am://app/<pkg>?user=<uid>` *(short alias — shipped 2026-05-09)*

Equivalent of `app-manager://details?id=<pkg>` with the package name in the URL path. Mirrors `hail://`'s shape.

Example:

```
am://app/com.android.chrome
am://app/com.android.chrome?user=10
```

Parsed by [`SelfUriManager.getUserPackagePairFromUri()`](../app/src/main/java/io/github/muntashirakon/AppManager/self/SelfUriManager.java); both schemes share the same code path. Adding a new alias scheme without touching the rest of the deep-link machinery is by design — schema changes here should never require updating consumers of `getUserPackagePairFromUri`.

### Public `am://` automation actions *(shipped 2026-05-18)*

These URIs are public and route through `AutomationUriActivity`, not the signature-only receiver. AppManagerNG authenticates the user, shows a confirmation dialog summarizing the action, target, and user id, then starts the existing batch/profile/installer flow. `dry_run=1` validates the request and exits without starting work.

| URI | Behaviour |
|-----|-----------|
| `am://freeze/<pkg>?user=<uid>` | Freeze package. |
| `am://unfreeze/<pkg>?user=<uid>` | Unfreeze package. |
| `am://force-stop/<pkg>?user=<uid>` | Force-stop package. |
| `am://clear-cache/<pkg>?user=<uid>` | Clear package cache. |
| `am://clear-data/<pkg>?user=<uid>` | Clear package data. |
| `am://uninstall/<pkg>?user=<uid>` | Queue uninstall. |
| `am://backup/<pkg>?backup_name=<name>&backup_flags=<int>` | Queue backup using optional backup override parameters. |
| `am://restore/<pkg>?backup_name=<name>&backup_flags=<int>` | Queue restore using optional backup override parameters. |
| `am://disable-component/<pkg>?component=.Receiver` | Disable/block a component for one package. |
| `am://enable-component/<pkg>?component=.Receiver` | Enable/unblock a component for one package. |
| `am://scan-trackers/<pkg>?user=<uid>` | Open App Details tracker view. |
| `am://profile/<id>/run?state=on` | Run profile id/name with optional state (`on`/`off`). |
| `am://run-profile/<id>?state=off` | Compatibility alias for profile run. |
| `appmanager://run-profile/<id>?state=on` | Legacy compatibility alias for profile run. |
| `am://install?source=<url>` | Open the installer for `file:`, `content:`, HTTP, or HTTPS sources accepted by the installer. |

Common URI query parameters:

| Query param | Applies to | Notes |
|-------------|------------|-------|
| `package`, `pkg`, `packages` | Package and profile actions | Adds package targets. `packages` accepts comma/newline-separated values. For `am://profile/...`, package targets are merged into the profile as a temporary `packages` override. |
| `user`, `users` | Package actions | `users` accepts comma/newline-separated ids. A single user fans out to all packages. |
| `dry_run`, `dry-run`, `dryRun` | All public actions | Boolean (`1`, `true`, `yes`, `on`, or false equivalents). |
| `component`, `cmp` | Component actions | Relative, short, flattened, or fully-qualified component class. |
| `backup_name`, `backup-name` | Backup/restore/profile actions | For profile actions, merged into temporary `backup_data.name`. |
| `backup_flags`, `backup-flags` | Backup/restore/profile actions | Integer `BackupFlags` bitmask. For profile actions, merged into temporary `backup_data.flags`. |
| `state`, `profile_state`, `profile-state` | Profile actions | `on` or `off`. |
| `profile_overrides`, `profile-overrides`, `overrides` | Profile actions | URL-encoded JSON object merged into the saved profile for this run only. `id`, `name`, and `type` are preserved from the saved profile. |
| `source`, `uri` | Install action | Installer source URI. |

---

## Public activity intents *(shipped 2026-05-18)*

Tasker/MacroDroid can also use "Send Intent" / "Start Activity" with the same action constants and extras listed in the broadcast table below. Target the AppManagerNG package and start an Activity, not a broadcast. This public path is handled by `AutomationUriActivity`, accepts string forms for common extras (`EXTRA_BACKUP_FLAGS="7"`, `EXTRA_DRY_RUN="true"`, comma-separated `EXTRA_PACKAGES` / `EXTRA_USERS`), and requires the same user confirmation as public URIs.

For profile runs, either use `ACTION_RUN_PROFILE` + `EXTRA_PROFILE_ID`, or use `ACTION_BACKUP` with `EXTRA_PROFILE_ID` and no package target for compatibility with the signature receiver.

---

## Broadcast intents *(shipped 2026-05-17)*

ROADMAP iter-22 T8 row "Broadcast Intent API" [S247]. The receiver is [`AutomationReceiver`](../app/src/main/java/io/github/muntashirakon/AppManager/automation/AutomationReceiver.java), constants live in [`AutomationIntents`](../app/src/main/java/io/github/muntashirakon/AppManager/automation/AutomationIntents.java), and the manifest gates the receiver with:

```xml
<permission
    android:name="io.github.sysadmindoc.AppManagerNG.permission.AUTOMATION"
    android:protectionLevel="signature" />
```

The schema mirrors Hail v1.10.0 while routing through AppManagerNG's existing service-backed operations:

| Action | Required extras |
|--------|-----------------|
| `io.github.sysadmindoc.AppManagerNG.action.FREEZE` | `EXTRA_PACKAGE`, optional `EXTRA_USER`. |
| `io.github.sysadmindoc.AppManagerNG.action.UNFREEZE` | `EXTRA_PACKAGE`, optional `EXTRA_USER`. |
| `io.github.sysadmindoc.AppManagerNG.action.FORCE_STOP` | `EXTRA_PACKAGE`, optional `EXTRA_USER`. |
| `io.github.sysadmindoc.AppManagerNG.action.CLEAR_CACHE` | `EXTRA_PACKAGE`, optional `EXTRA_USER`. |
| `io.github.sysadmindoc.AppManagerNG.action.CLEAR_DATA` | `EXTRA_PACKAGE`, optional `EXTRA_USER`. |
| `io.github.sysadmindoc.AppManagerNG.action.UNINSTALL` | `EXTRA_PACKAGE`, optional `EXTRA_USER`, optional `EXTRA_DRY_RUN` (boolean). |
| `io.github.sysadmindoc.AppManagerNG.action.BACKUP` | `EXTRA_PACKAGE` *or* `EXTRA_PROFILE_ID`; optional `EXTRA_BACKUP_NAME`, `EXTRA_BACKUP_FLAGS`, `EXTRA_USER`, `EXTRA_PROFILE_OVERRIDES`. |
| `io.github.sysadmindoc.AppManagerNG.action.RESTORE` | `EXTRA_PACKAGE`, optional `EXTRA_BACKUP_NAME`, `EXTRA_BACKUP_FLAGS`, `EXTRA_USER`. |
| `io.github.sysadmindoc.AppManagerNG.action.DISABLE_COMPONENT` | `EXTRA_PACKAGE`, `EXTRA_COMPONENT`; optional `EXTRA_USER`. |
| `io.github.sysadmindoc.AppManagerNG.action.ENABLE_COMPONENT` | `EXTRA_PACKAGE`, `EXTRA_COMPONENT`; optional `EXTRA_USER`. |
| `io.github.sysadmindoc.AppManagerNG.action.RUN_PROFILE` | `EXTRA_PROFILE_ID`, optional `EXTRA_PROFILE_STATE`, `EXTRA_PROFILE_OVERRIDES`. |
| `io.github.sysadmindoc.AppManagerNG.action.INSTALL_FROM_URI` | `EXTRA_URI` (file/content/HTTP(S) source accepted by the package installer). |
| `io.github.sysadmindoc.AppManagerNG.action.SCAN_TRACKERS` | `EXTRA_PACKAGE`, optional `EXTRA_USER`; opens App Details with tracker sort. |

Common extras:

| Extra | Type | Notes |
|-------|------|-------|
| `EXTRA_PACKAGE` | `String` | Single package name; validated with `PackageUtils.validateName(...)`. |
| `EXTRA_PACKAGES` | `String[]` or `ArrayList<String>` | Multi-package batch operations. |
| `EXTRA_USER` | `int` | Single user id; defaults to current user. |
| `EXTRA_USERS` | `int[]` or `ArrayList<Integer>` | Must match package count, or one id may fan out to all packages. |
| `EXTRA_COMPONENT` | `String` | Fully-qualified, relative (`.Receiver`), short (`Receiver`), or flattened (`pkg/.Receiver`) component class. Component actions require exactly one package. |
| `EXTRA_DRY_RUN` | `boolean` | Validates and exits without starting an operation. |
| `EXTRA_PROFILE_OVERRIDES` | `String` JSON object | Merged into the saved profile for this run only. Nested objects merge recursively; `id`, `name`, and `type` are preserved from the saved profile. |

Authorization: the manifest declares `io.github.sysadmindoc.AppManagerNG.permission.AUTOMATION` with `protectionLevel="signature"` and the receiver requires that permission. Generic Tasker/MacroDroid `Send Intent` broadcasts cannot call this receiver directly unless they are signed with NG's key or routed through an in-app broker. Use the public activity intent surface above for untrusted automation apps.

---

## Tasker / MacroDroid integration *(plugin broker shipped 2026-06-04)*

Use either:

1. A public URI, for example `am://freeze/com.example.app?user=0`.
2. A Tasker/MacroDroid **Start Activity** intent with action `io.github.sysadmindoc.AppManagerNG.action.FREEZE` and extra `EXTRA_PACKAGE=com.example.app`.
3. The Locale-compatible AppManagerNG plugin action for Tasker. Configure the action with any supported `am://` automation URI, for example `am://profile/nightly/run?state=on`.
4. The in-app pinned-shortcut flow for launcher-native shortcuts.

The public Activity path is intentionally confirmation-gated. The plugin path is the confirmation-free Tasker path because the setup UI runs inside AppManagerNG, stores Tasker's `com.twofortyfouram.locale.intent.extra.BUNDLE`, and signs the configured URI before Tasker can fire it later.

Locale plugin protocol details:

| Constant | Value |
|----------|-------|
| Edit action | `com.twofortyfouram.locale.intent.action.EDIT_SETTING` |
| Fire action | `com.twofortyfouram.locale.intent.action.FIRE_SETTING` |
| Bundle extra | `com.twofortyfouram.locale.intent.extra.BUNDLE` |
| Blurb extra | `com.twofortyfouram.locale.intent.extra.BLURB` |

Do not hand-craft the bundle. `TaskerPluginEditActivity` writes a private signed bundle containing the automation URI and a short blurb; `TaskerPluginFireReceiver` rejects missing, unsigned, tampered, or unsupported bundles before handing the request to `AutomationReceiver`. The signature broadcast receiver remains reserved for NG-signed companions and this in-app broker.

---

## SAF documents provider *(shipped 2026-06-04)*

`AppManagerDocumentsProvider` exposes AppManagerNG-managed files to Android's Storage Access Framework under authority `io.github.sysadmindoc.AppManagerNG.documents` for the default build.

| Root | Contents | Notes |
|------|----------|-------|
| `backups` | Configured local AppManagerNG backup volume. | SAF/network backup destinations are not re-exported as local files. |
| `profiles` | App-private profile JSON directory. | Read-only; profile edits still go through AppManagerNG. |

The provider rejects write modes, hidden children, unknown roots, and document IDs that resolve outside their canonical root. Consumers should use standard SAF picker or `DocumentsContract` APIs rather than `am://` URIs for this surface.

---

## Versioning policy

The `app-manager://` and `am://` schemes are part of AppManagerNG's stable public surface. Backwards-incompatible changes (rename, remove, or alter parsing semantics) are a major-version concern. Adding new hosts / actions / query parameters is non-breaking and may ship in any minor release.
