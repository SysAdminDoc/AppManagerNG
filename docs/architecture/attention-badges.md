<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Attention Badges on the Main List — Architecture

**Date:** 2026-05-26
**Roadmap reference:** [ROADMAP.md](../../ROADMAP.md) — T21-G
"Attention badges on app list rows".
**Status:** Data layer complete 2026-05-26 (calculator + source + tracker);
adapter rendering and Settings -> Glossary copy remain on the roadmap row.

## Goal

Surface a tiny circular badge counter on main-list rows where actionable
state exists: pending dangerous permissions, user-disabled components,
or recent OS reverts. The badge nudges users toward rows they likely
want to revisit without flooding the screen with permanent indicators.

## Priority order

The calculator collapses multiple concurrent signals into a single
badge — only one badge per row, in this order from highest to lowest:

1. **OS Revert** — the OS reverted a recent privileged mutation. Almost
   always means the user's change did not stick and needs investigation.
   Rendered with `Severity.WARN` (warning tint).
2. **Dangerous Permission** — the app has requested a dangerous
   permission at install/upgrade time that the user has not granted.
   Rendered with `Severity.INFO`.
3. **Disabled Component** — the app has user-disabled components or
   rules (AppManagerNG-managed). Rendered with `Severity.INFO`.
4. **None** — no badge.

The OS-revert badge wins because it represents a state the user did
*not* choose and likely wants to know about; permissions and disabled
components are intentional configurations.

## Count semantics

The badge displays the count from the winning signal — not the sum
across signals. Counts above 99 collapse to `"99+"`, matching the
Material badge component's default behavior.

| Signal | Count semantics |
|---|---|
| OS Revert | Number of recent revert events within the TTL window (see below). |
| Dangerous Permission | `max(0, dangerousPermTotal - dangerousPermGranted)`. |
| Disabled Component | `max(0, rulesCount)` — total rules for this package. |

`rulesCount` is the closest available proxy for "disabled components"
in the `App` cache. It includes app-op rules and permission overrides
too, so in practice the badge means "this package has AppManagerNG-
managed state" rather than strictly "components are disabled". Treat
this as a v0.6.x approximation; a future cache column could split
disabled components out specifically.

## OS revert TTL

`OsRevertCountTracker` keeps a per-package event log with a 7-day TTL
(`OsRevertCountTracker.DEFAULT_TTL_MILLIS`). The TTL is the central
"how long is a revert recent?" knob:

- 7 days is long enough to surface multi-day OEM cleanup (e.g. a phone
  that reverts app-ops nightly during idle maintenance).
- 7 days is short enough that a one-off revert from months ago doesn't
  permanently light the badge.
- Future: expose the TTL as a Settings preference if user feedback
  requests it. Hardcoded for now.

The tracker is bounded: `MAX_EVENTS_PER_PACKAGE` = 256 (oldest events
drop first when a single package logs more), `MAX_TRACKED_PACKAGES`
= 8192 (oldest-touched packages evict first). Memory ceiling under
worst-case usage: ~256 KB.

## Data-layer modules

| Module | Role |
|---|---|
| [`AttentionBadgeCalculator`](../../app/src/main/java/io/github/muntashirakon/AppManager/main/AttentionBadgeCalculator.java) | Pure-function badge picker. Inputs: three ints. Output: `Badge(kind, count, severity)`. |
| [`AttentionBadgeSource`](../../app/src/main/java/io/github/muntashirakon/AppManager/main/AttentionBadgeSource.java) | Single integration point with the `App` cache row. Derives the calculator's three inputs. |
| [`OsRevertCountTracker`](../../app/src/main/java/io/github/muntashirakon/AppManager/revert/OsRevertCountTracker.java) | Thread-safe per-package counter that backs `recentOsRevertCount`. |

## Adapter wiring (deferred)

The main-list adapter should:
1. Call `AttentionBadgeSource.badgeFor(app, osRevertCount)` per row,
   where `osRevertCount` is
   `OsRevertCountTracker.countRecent(packageName, now, DEFAULT_TTL_MILLIS)`.
2. Draw the badge at the trailing edge of the row icon, sized at
   16dp x 16dp (Material Badge default), with the tint chosen by
   `severity`:
   - `WARN` -> `colorErrorContainer` (Material You)
   - `INFO` -> `colorTertiaryContainer`
3. Use `AttentionBadgeCalculator.formatCount(count)` to render the
   numeric label; `Badge.none()` rows skip the draw call entirely.

## Glossary copy (for the future in-app entry)

> **Attention badges** — small numeric chips on the App List that flag
> rows where actionable state exists. AppManagerNG shows at most one
> badge per row, prioritised in this order:
>
> - **OS Revert** (warning tint) — the system reverted a recent
>   privileged change you made (a setting, an app op, a freeze). Tap
>   the row to investigate.
> - **Dangerous Permission** — the app requested a dangerous permission
>   that's not granted. Tap to review.
> - **Disabled Component** — AppManagerNG (or another tool) has
>   AppManagerNG-managed rules for this package.
>
> Counts above 99 show as "99+". OS-revert badges decay automatically
> 7 days after the event; the other two reflect current state.

## Open follow-ups

- [x] Main-list adapter wiring — shipped 2026-05-28 in
  `MainRecyclerAdapter.bindAttentionBadge` via the
  `AttentionBadgeSource.badgeFor(ApplicationItem, int)` overload. The visible
  indicator is a true-circle severity dot (`bg_attention_dot`); the exact count
  + reason are exposed through the row `contentDescription` (a stadium-shaped
  count chip would violate the no-pill rule), so the count is not drawn in the
  dot itself.
- [x] Glossary entry — shipped 2026-05-28 (Settings -> Glossary -> "Attention
  badges", `help_attention_badges_body`).
- [x] Wire `OsRevertMonitor` -> `OsRevertCountTracker.recordRevert` — shipped
  2026-05-28. `OsRevertCountTracker.getInstance()` is a process-wide singleton;
  `OsRevertMonitor.schedule(packageName, probe, …)` records into it on every
  detected revert (freeze / component / app-op / doze battery-optimization), and
  the main-list adapter reads `countRecent(pkg, now, DEFAULT_TTL_MILLIS)` per row.
- [x] Eviction heartbeat — shipped 2026-05-28 via `evictExpired` in
  `MainActivity.onResume`, so the tracker map cannot grow unbounded.
- [ ] Optional v0.6.x: split `rulesCount` proxy into a dedicated
  `disabledComponentCount` cache column for sharper badge semantics.
- [ ] Optional: a no-pill numeric rendering of the count (e.g. a fixed-size
  circle for 1-digit counts) if user feedback wants the number on-screen.
