<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# TalkBack Action-Label Audit Slice

Date: 2026-05-26.
Status: partial source hardening; manual TalkBack traversal remains open.

## Scope

- Layout-level icon-only controls in `app/src/main/res/layout`.
- Java-bound action labels for debloat suggestions and the UI tracker overlay.
- Source-level contract checks for controls whose labels can regress without a
  full device walkthrough.

## Fixed

- Labeled the UI tracker floating window controls: expand, move, open tracked
  app details, minimize, pause/resume, and close.
- Updated the UI tracker play/pause content description when the state changes,
  including minimize and expand transitions.
- Raised UI tracker controls from 40 dp to 48 dp with 24 dp icons.
- Labeled debloat details and suggestion action buttons for app-info and
  app-store destinations.
- Raised debloat details action buttons from 40 dp to 48 dp with 24 dp icons.
- Added a JVM resource/source contract test for these labels and targets.

## Static Follow-Up

The remaining icon-only layout templates without XML content descriptions are
shared rows whose adapters set labels at bind time:

- `item_icon_title_subtitle.xml`
- `item_op_history.xml`
- `item_profile.xml`
- `item_right_standalone_action.xml`
- `item_shared_lib.xml`
- `item_title_action.xml`

These should stay on the manual TalkBack traversal list because their labels
depend on runtime data and adapter branch coverage.

## Still Open

- Manual TalkBack traversal of the major screens on device/emulator: Main list,
  App Details, Backup/Restore, File Manager, Code Editor, Logcat, Settings,
  dialogs, sheets, onboarding, and the UI tracker overlay.
- Runtime checks for adapter-bound row actions, contextual menus, toolbar
  action overflow, bottom-sheet ordering, focus restoration after dialogs, and
  selection-mode announcements.
- RTL traversal and hardware-key focus order for dense rows with right-side
  actions.
