<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# 04 — Filter & Finder

How AppManagerNG models per-app predicates and runs cross-app search. This doc
covers the shape and dependencies between the four packages that together form
the filter substrate: `app/src/main/java/io/github/muntashirakon/AppManager/filters/`,
`tags/`, `filters/preset/`, and the `IFilterableAppInfo` consumers.

## 1. Big picture

Every cross-app surface in AppManagerNG — the main list quick-filter chips,
the Finder activity, the Debloater filters, and the Profile picker — composes
the same shape:

```
IFilterableAppInfo  ──▶  FilterOption.test(info, result)  ──▶  TestResult
                                                                 │
                              one per predicate                   │
                                                                 ▼
                                 ┌──── FilterItem.test() ────┐
                                 │   AND / OR / NOT tree     │
                                 └────────────────────────────┘
                                                                 │
                                                                 ▼
                                                          single boolean
```

- **`IFilterableAppInfo`** is the contract every filter consumer sees. It exposes
  `getPackageName()`, `getApplicationLabel()`, `usesPlayAppSigning()`,
  `getMobileDataUsage()` / `getWifiDataUsage()`, `getAllPermissionDetails()`,
  installed-user buckets, backup metadata, and ~40 other accessors. Two
  implementations exist:
  - **`FilterableAppInfo`** — full live read from PackageManager + usage / backup
    DB; used by Finder.
  - **`ApplicationItem`** — lighter weight; backed by the Room `apps` table; used
    by the main list. Both keep the same getter surface so a filter authored
    once works in either surface.
- **`FilterOption`** is the abstract per-predicate base class. Subclasses live in
  `filters/options/` and declare a `Map<String, Integer>` of supported keys plus
  a `test(IFilterableAppInfo, TestResult)` evaluator.
- **`FilterItem`** glues `FilterOption` instances together via an
  `AbsExpressionEvaluator` that walks a Boolean expression (AND/OR/NOT). The
  expression is serialised to JSON via `IJsonSerializer.serializeToJson`.
- **`TestResult`** carries the per-axis outcome — matched packages, matched
  components, matched trackers, matched permissions, matched backups — so a
  caller can inspect not just "did this pass" but "what specifically matched".

## 2. Filter options registry

`filters/options/FilterOptions.create(String)` is the canonical factory. It
hard-codes the registered names so dead-code elimination cannot strip a filter
the user references in a saved preset.

Current options (after iter-145):

```
apk_size           data_size           pkg_name             tags
app_label          data_usage          running_apps         target_sdk
app_ops            freeze_unfreeze     screen_time          times_opened
app_type           installed           signature            total_size
backup             install_date        trackers             uid
bloatware          installer                                version_name
cache_size         last_update
compile_sdk        min_sdk
components         permissions
```

When you add a new `FilterOption` subclass:

1. Pick a stable lowercase-snake-case key (e.g. `tracker_org` not
   `trackerOrg`). The key is the JSON discriminator; renames break saved
   presets.
2. Implement `getKeysWithType()` to declare the supported predicate keys with
   their value types (`TYPE_NONE`, `TYPE_STR_SINGLE`, `TYPE_STR_MULTIPLE`,
   `TYPE_INT`, `TYPE_INT_FLAGS`, `TYPE_LONG`, `TYPE_TIME_MILLIS`,
   `TYPE_DURATION_MILLIS`, `TYPE_SIZE_BYTES`, `TYPE_REGEX`).
3. Implement `test()` — read fields off `IFilterableAppInfo`, populate the
   relevant `TestResult.setMatched*()` accessor, return the result.
4. Register the key in `FilterOptions.create()`.
5. (Optional) Wire a static `KEY_ALL` predicate that matches every app so the
   user can add the row to a filter without first picking a discriminator —
   this is the convention every existing option follows.

## 3. The expression tree

`FilterItem.test()` evaluates the expression with a stack-based
`AbsExpressionEvaluator`. Operators are:

```
&  binary AND   evaluated left to right
|  binary OR    evaluated left to right
!  unary NOT    higher precedence than & or |
(  grouping     parentheses fold to the right
```

The default expression is `"1"` (the always-matches placeholder) so an empty
filter accepts every app. Adding an option appends `& <id>` to the expression
unless the user has manually edited it.

Two serialisation entry points:

- **`FilterItem.serializeToJson()`** — emits a full snapshot including the
  expression string, the option list, and per-option `setKeyValue` state. Used
  by `FilterPresetStore` (iter-145), `SnapshotBundle`, and the Finder's
  share-as-JSON action.
- **`FilterItem(JSONObject)`** — restore from the snapshot. Tolerates
  unknown-option keys (logs a warning) so a downgrade scenario doesn't fail
  loudly.

## 4. Multi-tag store (NF-08 / iter-143)

`tags/AppTagStore` is a SharedPreferences-backed JSON map of
`packageName -> Set<String>` tags. Tags normalise on write (`[a-z0-9][a-z0-9_-]{0,31}`,
lower-case, 32-char cap). The store is intentionally outside Room — the working
set is small (≤200 tagged apps for power users) and a future Room migration can
copy the JSON shape directly.

The shape is consumed by:

- **`filters/options/TagsOption`** — Finder predicate keys `any`, `none`,
  `has_all`, `has_any`, `missing_all`.
- (Future) the App Details tag editor and the main-list tag chip.
- (Future) `profiles/trigger/ProfileTrigger` invocations can carry a
  `requireTag` constraint so a routine fires only for tagged apps.

## 5. Saved Filter Presets (iter-145)

`filters/preset/FilterPresetStore` persists named `FilterItem` snapshots:

```json
[
  {
    "id": "<uuid>",
    "name": "<lowercased>",
    "createdAt": <epoch-ms>,
    "filter": <FilterItem.serializeToJson()>
  }
]
```

Names normalise through a `[a-z0-9][a-z0-9 _-]{0,63}` allowlist; collisions
are case-insensitive. The store offers `save / rename / remove / find / all /
hasAny` and is read-and-write tolerant of corrupted entries (individual
malformed presets are skipped; the rest survive). Pure-JVM coverage at
`FilterPresetStoreTest`.

A future Finder UI iteration can ship a "Save filter" action that calls
`store.save(name, model.getFilterItem())` and a "Load preset" picker that
restores via `model.setFilterItem(preset.filter)`. No further data work needed.

## 6. Relevance scoring

`filters/FinderRelevanceScorer` (iter-15) post-processes a filter pass when the
user supplied literal package-name, component-name, or tracker-name terms. It
ranks matches by Levenshtein distance against:

- full package names
- simple (last-dot) names
- token splits
- sliding windows
- matched component / tracker class names

Regex and negative predicates are excluded from scoring; rows that only
matched unrelated filters keep their original scan order. The scorer is a
pure function — useful for sorting any predicate result against a literal
query, not just Finder.

## 7. Where filter logic *isn't*

- The Debloater list does **not** use this substrate — it has its own
  `BloatwareFilter` over the `DebloatObject` dataset because the predicates
  there are over a different model (`removalType`, `dependencies`,
  `requiredBy`, OEM-protected flag).
- The Permission Inspector apps-screen filter (iter-144 EI-04) similarly
  rolls its own three-chip filter (`All` / `User apps` / `Granted`) directly
  in `PermissionAppsViewModel.applyFilter()` because the model is a flat
  `List<AppRow>` and a full `FilterItem` would be over-engineered.
- Profiles use `BaseProfile.filter` (an opaque list of package names) rather
  than expression trees because the profile contract was authored before
  this substrate landed.

If you find yourself wishing one of these surfaces could compose with a
`FilterItem`, the right next step is the **filters/ Gradle-module split**
now tracked in [`ROADMAP.md`](../../ROADMAP.md). That depends
on the `IFilterableAppInfo` accessor surface stabilising first — which
won't happen until NF-09 lands and adds its trigger-bound `FilterItem`
constraint surface.

## 8. Testing pattern

`FilterOption.test()` implementations should have a pure-JVM unit test that
exercises:

- **Empty / unset value** — does the default key (`KEY_ALL` or equivalent)
  return `setMatched(true)` for every input?
- **Each declared key** — does `setKeyValue(key, value)` accept the
  declared `TYPE_*`, set the right internal field, and produce the expected
  matched / unmatched result for representative `IFilterableAppInfo`
  fixtures?
- **Serialise round-trip** — does
  `new FilterItem(item.serializeToJson()).test(info, result)` produce the
  same result as `item.test(info, result)`?

`PermissionsOptionTest`, `BloatwareOptionTest`, `InstallDateOptionTest`, and
the NF-08-era `TagsOption` tests are the canonical examples.

## 9. Loose ends worth fixing

- `DataUsageOption` had `mobile_le` / `mobile_ge` / `wifi_le` / `wifi_ge`
  added in iter-22 but never got an inline `getFlags(key)` UI surface — the
  Finder still renders it as a single combined predicate. (Roadmap T7.)
- `TrackersOption.test()` ignores the option's `is_blocked` predicate when
  the IFW path returns no rule for a tracker class. A future tracker-state
  cache (paired with NF-07) would unblock the rule.
- `RegexOption` exists as scaffolding but no UI registers it — it was
  intended to compose into `app_label` matching but the implementation
  stalled.
