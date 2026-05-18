<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 134 - Per-app audio-volume AppOps

## Roadmap row

T9 **Per-App Volume via AppOps `OP_AUDIO_VOLUME`** is shipped.

## What changed

- `AppOpsManagerCompat` now resolves and names the Android audio-volume app-op
  family:
  - `AUDIO_MASTER_VOLUME`
  - `AUDIO_VOICE_VOLUME`
  - `AUDIO_RING_VOLUME`
  - `AUDIO_MEDIA_VOLUME`
  - `AUDIO_ALARM_VOLUME`
  - `AUDIO_NOTIFICATION_VOLUME`
  - `AUDIO_BLUETOOTH_VOLUME`
  - `AUDIO_ACCESSIBILITY_VOLUME`
- AppOps mode writes now make the UID-mode branch explicit for normal AOSP ops;
  MIUI-only and pre-Marshmallow ops keep the package-mode fallback.
- App Details -> App ops now has a **Set audio volume app ops** action that
  applies the selected mode to every supported audio-volume op for the current
  package.
- The generic custom AppOps input helper now advertises `AUDIO_MEDIA_VOLUME` so
  users can discover the named op in the existing free-form path too.
- Added `AppOpsManagerCompatTest` coverage for supported op enumeration,
  fallback names, and UID-mode routing.
- Updated `ROADMAP.md`, `CHANGELOG.md`, and `PROJECT_CONTEXT.md`.

## Validation

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.AppManager.compat.AppOpsManagerCompatTest" --console=plain` passed.

## Source notes

The Android platform exposes the audio-volume operations as individual app ops
(`AUDIO_MEDIA_VOLUME`, `AUDIO_RING_VOLUME`, etc.) with `DISALLOW_ADJUST_VOLUME`
restrictions and default-allowed modes. NG models the family rather than a
single literal `OP_AUDIO_VOLUME` because current AOSP splits volume streams by
operation. Source: `ROADMAP.md` [S364].
