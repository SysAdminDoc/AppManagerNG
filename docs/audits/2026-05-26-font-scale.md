<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# 200 Percent Font-Scale Audit Slice

Date: 2026-05-26.
Status: partial source hardening; manual major-screen/device audit remains
open.

## Scope

- Code Editor status row and lock toggle.
- Help search controls.
- Code Editor search and replace controls.
- Open With and searchable multi-choice dialog search rows.
- Source-level layout constraints that can be pinned by JVM resource tests.

## Fixed

- Code Editor status fields now use weighted, wrapping cells instead of fixed
  width text that can crowd or clip at 200 percent font scale.
- The Code Editor lock toggle keeps a 48 dp touch target with a 24 dp icon.
- Help and Code Editor search icon buttons now use 48 dp touch targets with
  24 dp icons, preserving accessibility hit targets while avoiding oversized
  icons at high font scale.
- Open With and searchable multi-choice search rows now use 48 dp height.
- The searchable multi-choice "Select all" row can grow vertically for larger
  text while preserving a 48 dp minimum target.
- Added resource contract coverage for these constraints.

## Still Open

- Manual 200 percent font-scale walkthrough of major screens on a device or
  emulator: Main list, App Details, Backup/Restore, File Manager, Code Editor,
  Logcat, Settings, dialogs, sheets, and onboarding.
- Screenshot-based visual QA for dense tables, RecyclerView rows, menus,
  bottom sheets, and multi-line preference rows.
- Text clipping checks in translated strings, especially Germanic,
  right-to-left, and long technical labels.
