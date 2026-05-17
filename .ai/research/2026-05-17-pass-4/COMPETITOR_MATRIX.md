<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# COMPETITOR_MATRIX — 2026-05-17 pass 4

Pass 4 did not re-open the full Android app-manager competitor matrix. It added one
targeted ecosystem comparison relevant to NG's Shizuku integration.

| Project | Surface | Current signal | Lesson for NG |
|---------|---------|----------------|---------------|
| RikkaApps/Shizuku | Official privilege broker NG integrates with | Latest official release remains `v13.6.0`; Android 17 issues `#1965`, `#1967`, `#1988` remain open. | Warn Android 17 users without disabling Shizuku; keep fixed-version floor unknown until an official fix is verified. |
| thedjchi/Shizuku | Active fork of Shizuku | Android 17 support issue is assigned, high priority, milestone `v14.0.0`; notes `ACCESS_LOCAL_NETWORK` pairing pressure. | Keep Hidden-Shizuku / fork detection on the roadmap; release watcher should monitor official Shizuku first, but fork activity is a practical fallback signal. |
| AppManagerNG Wireless ADB path | Internal fallback, not a competitor | Existing onboarding flow already has Wireless ADB setup and pairing callbacks. | The Shizuku warning should deep-link to Wireless ADB instead of creating a new fallback path. |
