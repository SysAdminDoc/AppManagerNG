<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Audit: Android 17 BAL hardening on `IntentSender` + `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` migration

**Date:** 2026-05-17
**Source:** https://developer.android.com/about/versions/17/behavior-changes-17 (S206); research/iter-20-delta.md §"Android 17 — BAL hardening"
**Audited against:** repo at `47eb040` (iter-25 deliverables commit)
**Roadmap row:** ROADMAP §"Engineering Debt Register" — Android 17 `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` migration; closes one of the five open sub-audits in the targetSdk=37 batch.

## Premise

Android 17 (API 37) hardens background-activity-launch (BAL) on `IntentSender.sendIntent()`.
`ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED` is deprecated in favor of the
more restrictive `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE`. Apps that try to
launch an Activity from a Service or BroadcastReceiver while in the background must
either (a) be invoke-visible (foreground or recently-foreground) or (b) hold a
notification deep-link to the destination.

Cross-reference iter-20 row: AM upstream's profile-trigger Activity-launch-from-Service
path is exactly this pattern. NG inherits the same surface unless it migrated to a
BroadcastReceiver-driven model.

## Sweep methodology

- `grep -rn "MODE_BACKGROUND_ACTIVITY_START\|MODE_BACKGROUND_ACTIVITY_START_ALLOWED\|MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE\|setPendingIntentBackgroundActivityStartMode" app/src/main/java/`
- `grep -rn "ActivityOptions\.makeBasic\|setRequireDefaultIntent" app/src/main/java/`
- Verified by inspecting the AM/NG profile-trigger path against the iter-20 finding.

## Findings

- **Zero `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` matches** across `app/src/main/java/`. ✅
- **Zero `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE` matches** (i.e. the migration is unnecessary because the deprecated value is never set).
- **Zero `setPendingIntentBackgroundActivityStartMode(...)` calls**. ✅
- **Zero `ActivityOptions.makeBasic()` chained to a background-activity-start mode**.
- The grep over `app/src/main/java/` for `ActivityOptions\.` returns no production matches; NG uses default `Intent` + `Context.startActivity()` from foreground UI callers and doesn't try to launch from background services. ✅

The iter-20 [S206] note about AM's profile-trigger path was about **upstream** AM
(`MuntashirAkon/AppManager`) on the v4.0.x line. NG's iter-23 work moved the
Tasker-friendly path to the `am://` broadcast surface; the documented direction for
upstream issue #1968 is to use a `BroadcastReceiver` rather than an Activity launcher
service, which sidesteps the BAL hardening entirely.

## Verdict

✅ **clean** — zero remediation required.

NG does not currently use the deprecated `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` BAL
flag. The profile-trigger Activity launch is invoked from foreground UI contexts (the
Profiles screen, the home-screen shortcut chain) where BAL is permitted regardless of
targetSdk. The `am://` URI scheme and the future broadcast-API row (ROADMAP iter-25
Tasker-parameterized-intent work) are receiver-driven and unaffected by BAL hardening.

## Follow-ups

- If the planned Routine Operations (T8) scheduler ever introduces a Service that
  launches a profile-apply Activity from background, that surface must use
  `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE` and verify visibility before the
  launch.
- The `BroadcastReceiver` migration for `ProfileApplierActivity` → `ProfileApplierReceiver`
  noted in [`research/iter-20-delta.md`](../../research/iter-20-delta.md) §"#1968" is
  the architecturally-aligned fix and dovetails with this audit.

This is **audit 3 of 5** of the open Android 17 targetSdk=37 compliance batch.
