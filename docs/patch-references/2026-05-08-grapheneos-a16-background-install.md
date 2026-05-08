<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# Patch Reference: GrapheneOS A16 Background-Install-Confirmation Fix

**Date:** 2026-05-08
**Roadmap reference:** [ROADMAP.md](../../ROADMAP.md) — Iter-20 / T11 / Now row "GrapheneOS A16 Background-Install-Confirmation Fix" ([S198]).
**Status:** Documented patch reference (no NG code change yet — the fix requires a real Android 16 test device for safe deployment).

---

## What

[GrapheneOS / AppStore Release 36](https://github.com/GrapheneOS/AppStore/releases) shipped two
fixes in the `PackageInstaller` callback path that are directly applicable to AppManagerNG's
`PackageInstallerActivity`:

1. **A16 background-install confirmation crash** — on Android 16, when the system fires the
   user-confirmation dialog for a background-initiated `PackageInstaller.Session.commit()`,
   the activity that received the `EXTRA_STATUS` callback can be in a state where it has
   already received its `onSaveInstanceState()` and a subsequent `startActivity()` for the
   confirmation intent crashes with `IllegalStateException: Can not perform this action after onSaveInstanceState`.
   GrapheneOS's fix wraps the confirmation-intent dispatch in a state-aware send that defers
   the call to `onPostResume()` if the activity is paused.
2. **`PendingActions` leak from untrusted MainActivity callers** — installer activities can be
   re-targeted by external callers via `Intent.ACTION_VIEW`-style invocations. If the caller
   is untrusted, the queued `PendingActions` for the *prior* trusted install survive and replay
   into the wrong context. GrapheneOS's fix audits the calling package against an allowlist
   (the system package + the app's own package) and drops queued `PendingActions` when the
   current invocation comes from an untrusted caller.

Both fixes are in [`InstallerActivity.kt`](https://github.com/GrapheneOS/AppStore/blob/release-36/AppStore/src/main/java/app/grapheneos/apps/ui/InstallerActivity.kt) of GrapheneOS AppStore Release 36.

## Why this matters for AppManagerNG

AppManagerNG's [`PackageInstallerActivity.java`](../../app/src/main/java/io/github/muntashirakon/AppManager/apk/installer/PackageInstallerActivity.java) sits on the same `PackageInstaller` callback surface and is reachable from external callers via:

- `Intent.ACTION_VIEW` on `application/vnd.android.package-archive`,
- `Intent.ACTION_INSTALL_PACKAGE`,
- The `.apk` / `.apks` / `.apkm` / `.xapk` MIME-type-bound activity-aliases declared in [`AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml).

NG users on Android 16 are exposed to both bug classes. The fixes are platform-correct and
behavior-preserving.

## Why we are documenting rather than cherry-picking now

GrapheneOS AppStore is licensed GPL-3.0-or-later — license compatible. The fix could be ported
mechanically. We are not doing so this iteration because:

1. **No Android 16 test device on hand.** The bug only reproduces on Android 16, not Android
   15-or-earlier. Mirroring the fix without a reproducer on the same OS version risks landing
   a logically-equivalent-but-state-machine-misaligned patch that papers over the symptom in
   one path while moving it to another. This is the same calibre of patch as
   [the iter-19 16 KB page-size fix](../../ROADMAP.md) — wrong-state-machine fixes in installer
   paths historically cause silent install-cancellation regressions on the still-current path.
2. **The fix is a port, not an invention.** When NG acquires an Android 16 device (in flight
   for the v0.5.0 milestone alongside the Onboarding Capability Wizard work), the patch can be
   applied and validated in a single commit. Rushing it now would split the validation across
   two commits and introduce a window where the iter-21 ROADMAP audit can't tell whether the
   regression source is NG's port or NG's pre-existing path.

## What to do when porting

1. Read [`PackageInstallerActivity.java`](../../app/src/main/java/io/github/muntashirakon/AppManager/apk/installer/PackageInstallerActivity.java).
2. Locate the `PackageInstaller.Session.commit()` callback dispatcher (sends back via
   `BroadcastReceiver` registered against the `PackageInstaller.EXTRA_STATUS_RECEIVED` action).
3. For each `startActivity()` of the user-confirmation `Intent`:
   - Wrap the call in an `isResumed` check; if paused, queue the intent to a field and dispatch
     it from `onPostResume()` instead.
4. For `onCreate()` / `onNewIntent()`:
   - Read `getCallingPackage()` (if non-null) and `getReferrer()` (always present for `ACTION_VIEW`).
   - If the caller is not the system package (`"android"`) and not the app's own package,
     clear any queued `PendingActions` from the previous (trusted) flow before processing the
     new intent.
5. Test path:
   - Trigger an install from another app's `Intent.ACTION_VIEW` while AppManagerNG is in the
     foreground. Confirm the prior session's status callback does not replay against the new
     install.
   - Background AppManagerNG mid-install. Confirm the user-confirmation dialog dispatches
     cleanly when AppManagerNG is brought back to the foreground (not before).

## Cross-references

- [GrapheneOS AppStore Release 36](https://github.com/GrapheneOS/AppStore/releases/tag/36) — upstream patch source
- [`PackageInstallerActivity.java`](../../app/src/main/java/io/github/muntashirakon/AppManager/apk/installer/PackageInstallerActivity.java) — NG site to patch
- [ROADMAP.md](../../ROADMAP.md) — Iter-20 row "GrapheneOS A16 Background-Install-Confirmation Fix" (T11, Now)
- [ROADMAP.md](../../ROADMAP.md) — Iter-20 row "InstallerX-Revived Privilege-Elevation Cascade" (T11, Next) — the natural next-iteration installer-flow rework that should land both fixes together

---

When NG ports the fix, this document should be updated with the commit SHA + the iter-N row
that closes the ROADMAP entry. Until then it stands as the engineering record of *why* we
are deferring a known-good upstream patch — so a future maintainer can resume the work without
re-reading the GrapheneOS Release 36 changelog from scratch.
