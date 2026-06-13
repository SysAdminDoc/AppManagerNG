<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Large-list rendering audit (T21-I)

**Date:** 2026-05-28
**Roadmap reference:** ROADMAP.md — T21-I "Fast list rendering for 10k+
installed apps". Baseline target: < 2 s cold filter on 10 k apps, no behavior
change for typical (< 300-app) devices.

## Current architecture

The App List is a `RecyclerView` (`R.id.item_list`) driven by
`MainRecyclerAdapter` over an `ArrayList<ApplicationItem>` guarded by
`mAdapterList`. Key facts established by source audit:

- **Stable IDs are on** — `mAdapter.setHasStableIds(true)`
  (`MainActivity`), backed by `getItemId(position)`.
- **Updates already diff** — `MainRecyclerAdapter.setDefaultList` routes
  through `AdapterUtils.notifyDataSetChanged(adapter, base, new)`, which calls
  `DiffUtil.calculateDiff(...)` and `dispatchUpdatesTo(adapter)`. So the list
  does *not* call `notifyDataSetChanged()` wholesale; only changed rows rebind.
- **Icons load async** — `ImageLoader.getInstance().displayImage(...)` with a
  disk/memory cache, so per-bind work is dominated by view binding, not icon
  decode.
- **Tracker-category counts are cached** — `MainActivity` keeps a
  `ConcurrentHashMap<String,int[]>` so filter/sort passes don't re-walk
  components.

## Dominant cost for 10 k apps

`AdapterUtils.notifyDataSetChanged` runs `DiffUtil.calculateDiff` **on the
calling (main) thread** inside the `synchronized (mAdapterList)` block in
`setDefaultList`. For a 10 k-row cold filter this O(N·D) diff is the single
largest UI-thread stall — it dominates the < 2 s target far more than per-row
bind cost or fling smoothness. The upstream filter/sort that produces the new
list (in `MainViewModel`) already runs on a background executor; only the diff
+ dispatch is on-main.

## Changes applied this pass (behavior-preserving, zero-risk)

1. `recyclerView.setHasFixedSize(true)` — the row layout never changes the
   RecyclerView's own bounds (it is `match_parent` inside a `CoordinatorLayout`),
   so each adapter update can skip a full `requestLayout()` of the list.
2. `recyclerView.setItemViewCacheSize(15)` (default is 2) — keeps more
   just-scrolled-off rows hot, so a fling re-binds fewer holders. This trades a
   small amount of memory (≈ 13 extra inflated rows) for smoother scrolling on
   large installs; negligible for typical (< 300-app) devices.

These help scroll/fling smoothness and shave layout passes, but they do **not**
move the cold-filter diff off the main thread.

## Primary remaining lever (device-gated)

Move `DiffUtil.calculateDiff` off the main thread for the App List:

- Either adopt `AsyncListDiffer` / `ListAdapter` for the App List, or add an
  async variant of `AdapterUtils.notifyDataSetChanged` that computes the diff on
  a background executor and only `dispatchUpdatesTo(adapter)` on the main thread.
- Care points: `setDefaultList` is synchronized on `mAdapterList` and also
  refreshes `mSearchQuery` + selection state; the async path must snapshot the
  new list, keep the base list immutable during calculation, and coalesce
  rapid successive filter changes (search-as-you-type) so an in-flight diff is
  cancelled when a newer list arrives.

This is the change most likely to hit the < 2 s target, but it is a threading
change to the core list path and **must be measured on a real device** with a
seeded 10 k-app profile (Robolectric does not model layout/diff timing). It is
therefore tracked as the open half of T21-I rather than landed blind.

## Measurement plan (for the device pass)

1. Seed a device/emulator with ~10 k installed-or-cached `ApplicationItem`
   rows (PackageManager mocks or a debug fixture loader).
2. `Trace.beginSection("MainList.coldFilter")` around the filter→`setDefaultList`
   path; capture with Perfetto (now exportable per T20-A) or `systrace`.
3. Record main-thread stall for: (a) current main-thread diff, (b) the
   async-diff variant. Target: cold filter < 2 s, no main-thread frame drop
   > 16 ms during steady-state scroll.
4. Confirm no regression on a typical (< 300-app) device: cold load and filter
   should be visually identical.
