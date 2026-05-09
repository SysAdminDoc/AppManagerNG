# AppManagerNG Intent / URI Schema

**Status:** v0.4.x — partial. App-Info short alias `am://app/<pkg>` shipped 2026-05-09; broadcast-intent automation surface (`com.sysadmindoc.appmanagerng.action.*`) is roadmapped under iter-22 T8 [S246] and not yet implemented.

This file documents how external apps (Tasker, MacroDroid, launcher pinned shortcuts, KDE Connect, custom URLs) should drive AppManagerNG. Two surfaces:

1. **URI deep links** (`app-manager://`, `am://`) — public, dispatched via the launcher. Anyone with the link can fire them; treat them as user-initiated UI navigation.
2. **Broadcast actions** (`com.sysadmindoc.appmanagerng.action.*`) — gated behind a signature-protected permission. Only callers signed with the AppManagerNG release certificate (or holders of the documented permission) can fire. Roadmapped, not yet wired.

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

These shapes are reserved by the schema below but **not wired** in v0.4.x. Implementations land alongside the broadcast-intent automation surface (next section), which provides the authorization model these need.

| URI | Intended behaviour | Authorization gate |
|-----|--------------------|--------------------|
| `am://freeze/<pkg>` | Freeze the named package. | Signature permission *or* user-confirmed dialog. |
| `am://profile/<id>/run` | Run the named profile (`id` matches the profile JSON file). | Signature permission *or* user-confirmed dialog. |
| `am://install?source=<url>` | Download an APK from the given URL and offer install. | Same Android `REQUEST_INSTALL_PACKAGES` flow as a manual share. |

Until these land, the canonical way to drive a freeze / profile-run from an external app is to share a profile JSON or use the in-app pinned shortcut produced by `CreateShortcutDialogFragment`.

---

## Broadcast intents *(roadmapped — not yet implemented)*

ROADMAP iter-22 T8 row "Broadcast Intent API (`com.sysadmindoc.appmanagerng.action.*`)" [S247]. Intended schema mirrors Hail v1.10.0:

| Action | Required extras |
|--------|-----------------|
| `com.sysadmindoc.appmanagerng.action.FREEZE` | `EXTRA_PACKAGE`, optional `EXTRA_USER`. |
| `com.sysadmindoc.appmanagerng.action.UNFREEZE` | `EXTRA_PACKAGE`, optional `EXTRA_USER`. |
| `com.sysadmindoc.appmanagerng.action.FORCE_STOP` | `EXTRA_PACKAGE`, optional `EXTRA_USER`. |
| `com.sysadmindoc.appmanagerng.action.CLEAR_CACHE` | `EXTRA_PACKAGE`, optional `EXTRA_USER`. |
| `com.sysadmindoc.appmanagerng.action.CLEAR_DATA` | `EXTRA_PACKAGE`, optional `EXTRA_USER`. |
| `com.sysadmindoc.appmanagerng.action.UNINSTALL` | `EXTRA_PACKAGE`, optional `EXTRA_USER`, optional `EXTRA_DRY_RUN` (boolean). |
| `com.sysadmindoc.appmanagerng.action.BACKUP` | `EXTRA_PACKAGE` *or* `EXTRA_PROFILE_ID`. |
| `com.sysadmindoc.appmanagerng.action.RESTORE` | `EXTRA_PACKAGE`, optional `EXTRA_BACKUP_NAME`. |
| `com.sysadmindoc.appmanagerng.action.DISABLE_COMPONENT` | `EXTRA_PACKAGE`, `EXTRA_COMPONENT`. |
| `com.sysadmindoc.appmanagerng.action.ENABLE_COMPONENT` | `EXTRA_PACKAGE`, `EXTRA_COMPONENT`. |
| `com.sysadmindoc.appmanagerng.action.RUN_PROFILE` | `EXTRA_PROFILE_ID`. |
| `com.sysadmindoc.appmanagerng.action.INSTALL_FROM_URI` | `EXTRA_URI` (HTTP(S) source). |
| `com.sysadmindoc.appmanagerng.action.SCAN_TRACKERS` | `EXTRA_PACKAGE`. |

Authorization: a `<permission android:name="com.sysadmindoc.appmanagerng.permission.AUTOMATION" android:protectionLevel="signature"/>` declared by the AppManagerNG release manifest. Only NG-signed callers receive the grant by default. A user-granted Tasker permission flow is part of the Tasker Plugin work (iter-22 T8 [S250]).

---

## Tasker / MacroDroid integration

Until the broadcast surface lands, the simplest way to drive AppManagerNG from Tasker is:

1. Use the in-app **Pinned shortcut** flow (`CreateShortcutDialogFragment` — long-press an app's row in App Info) to produce a launcher shortcut for the desired action (Freeze / Force-Stop / Clear Cache).
2. Configure Tasker's "Launch Shortcut" action to fire the produced shortcut.

Once the broadcast surface ships, Tasker will also be able to fire the actions directly through `Send Intent → Broadcast Receiver` with the `com.sysadmindoc.appmanagerng.action.*` action and the extras above. The Tasker Plugin work (`com.twofortyfouram` Locale-spec plugin) is the same surface, packaged for Tasker / Automate / MacroDroid auto-discovery.

---

## Versioning policy

The `app-manager://` and `am://` schemes are part of AppManagerNG's stable public surface. Backwards-incompatible changes (rename, remove, or alter parsing semantics) are a major-version concern. Adding new hosts / actions / query parameters is non-breaking and may ship in any minor release.
