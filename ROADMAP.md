<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Historical surfaces are archived under
`docs/roadmap/archive/`. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

All remaining items are in `Roadmap_Blocked.md` — gated on device access,
visual verification, privileged-mode testing, or external dependencies.

## Research-Driven Additions (2026-06-19)



### P3



## Audit Findings (2026-06-20)

- [ ] P2 — PackageInstallerCompat verifier-disable setting not crash-safe
  Why: When disableVerification is true and UID is shell, SETTINGS_VERIFIER_VERIFY_ADB_INSTALLS is set to 0 globally; if the process is killed (OOM/force-stop) between openSession and restoreVerifySettings, the setting stays disabled permanently
  Where: app/src/main/java/io/github/muntashirakon/AppManager/apk/installer/PackageInstallerCompat.java:907-912

- [ ] P2 — RootServiceServer allows arbitrary class instantiation from connected clients
  Why: bindInternal uses className from client Intent to loadClass without verifying it is a RootService subclass; a compromised client with an existing connection could instantiate arbitrary classes
  Where: app/src/main/java/io/github/muntashirakon/AppManager/ipc/RootServiceServer.java:213-216

- [ ] P2 — LocalServerManager stop() not synchronized on mLock
  Why: stop() nulls mAdbStream and mSession without holding mLock, racing with getSession() which reads under mLock; concurrent calls can NPE
  Where: app/src/main/java/io/github/muntashirakon/AppManager/servermanager/LocalServerManager.java:122-127

- [ ] P3 — 114 printStackTrace() calls should use project Log class
  Why: inconsistent logging; on userdebug builds stack traces go to logcat without tag filtering; makes production debugging harder
  Where: app/src/main/java/io/github/muntashirakon/AppManager/ (114 instances across 50+ files)

All remaining blocked items are in `Roadmap_Blocked.md`.
