# AppManagerNG Intent / URI Schema

**Status:** v0.4.x — partial. App-Info short alias `am://app/<pkg>` shipped 2026-05-09; signature-gated broadcast-intent automation surface (`io.github.sysadmindoc.AppManagerNG.action.*`) shipped 2026-05-17.

This file documents how external apps (Tasker, MacroDroid, launcher pinned shortcuts, KDE Connect, custom URLs) should drive AppManagerNG. Two surfaces:

1. **URI deep links** (`app-manager://`, `am://`) — public, dispatched via the launcher. Anyone with the link can fire them; treat them as user-initiated UI navigation.
2. **Broadcast actions** (`io.github.sysadmindoc.AppManagerNG.action.*`) — gated behind a signature-protected permission. Only callers signed with the AppManagerNG release certificate, or an in-app broker such as the planned Tasker plugin, can fire them.

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

### Roadmapped — additional `am://` actions *(iter-22 T8 [S246], not yet implemented)*

These shapes are reserved by the schema below but **not wired** in v0.4.x. The broadcast-intent automation surface below is the shipped non-public execution layer; public URI actions still need user-confirmed dialogs before they should execute privileged work.

| URI | Intended behaviour | Authorization gate |
|-----|--------------------|--------------------|
| `am://freeze/<pkg>` | Freeze the named package. | Signature permission *or* user-confirmed dialog. |
| `am://profile/<id>/run` | Run the named profile (`id` matches the profile JSON file). | Signature permission *or* user-confirmed dialog. |
| `am://install?source=<url>` | Download an APK from the given URL and offer install. | Same Android `REQUEST_INSTALL_PACKAGES` flow as a manual share. |

Until these land, the canonical way to drive a freeze / profile-run from an external app is to share a profile JSON or use the in-app pinned shortcut produced by `CreateShortcutDialogFragment`.

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
| `io.github.sysadmindoc.AppManagerNG.action.BACKUP` | `EXTRA_PACKAGE` *or* `EXTRA_PROFILE_ID`; optional `EXTRA_BACKUP_NAME`, `EXTRA_BACKUP_FLAGS`, `EXTRA_USER`. |
| `io.github.sysadmindoc.AppManagerNG.action.RESTORE` | `EXTRA_PACKAGE`, optional `EXTRA_BACKUP_NAME`, `EXTRA_BACKUP_FLAGS`, `EXTRA_USER`. |
| `io.github.sysadmindoc.AppManagerNG.action.DISABLE_COMPONENT` | `EXTRA_PACKAGE`, `EXTRA_COMPONENT`; optional `EXTRA_USER`. |
| `io.github.sysadmindoc.AppManagerNG.action.ENABLE_COMPONENT` | `EXTRA_PACKAGE`, `EXTRA_COMPONENT`; optional `EXTRA_USER`. |
| `io.github.sysadmindoc.AppManagerNG.action.RUN_PROFILE` | `EXTRA_PROFILE_ID`, optional `EXTRA_PROFILE_STATE`. |
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

Authorization: the manifest declares `io.github.sysadmindoc.AppManagerNG.permission.AUTOMATION` with `protectionLevel="signature"` and the receiver requires that permission. Generic Tasker/MacroDroid `Send Intent` broadcasts cannot call this receiver directly unless they are signed with NG's key or routed through a future in-app broker. The planned Tasker Plugin work (iter-22 T8 [S250]) should broker these same actions from a user-configured plugin UI.

---

## Tasker / MacroDroid integration

Until the Tasker plugin lands, the simplest way to drive AppManagerNG from Tasker is still:

1. Use the in-app **Pinned shortcut** flow (`CreateShortcutDialogFragment` — long-press an app's row in App Info) to produce a launcher shortcut for the desired action (Freeze / Force-Stop / Clear Cache).
2. Configure Tasker's "Launch Shortcut" action to fire the produced shortcut.

The broadcast surface is intentionally not directly callable by arbitrary automation apps. The Tasker Plugin work (`com.twofortyfouram` Locale-spec plugin) is the same operation surface packaged for Tasker / Automate / MacroDroid auto-discovery and user confirmation.

---

## Versioning policy

The `app-manager://` and `am://` schemes are part of AppManagerNG's stable public surface. Backwards-incompatible changes (rename, remove, or alter parsing semantics) are a major-version concern. Adding new hosts / actions / query parameters is non-breaking and may ship in any minor release.
