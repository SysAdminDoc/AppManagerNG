# Iter 138 - Material Components 1.14 floor gate

Date: 2026-05-18

## Roadmap row

- T17: Material Components 1.13 -> 1.14 (FocusRingDrawable + SplitButton)

## Finding

- Google Maven now publishes `com.google.android.material:material:1.14.0`.
- The dependency still raises `minSdkVersion` to 23.
- AppManagerNG remains pinned to `min_sdk = 21` by an explicit product/support
  contract captured in `docs/policy/minsdk-21-ceiling.md`.

## Decision

- Do not bump Material Components as a standalone dependency update.
- Park the roadmap row as blocked by the minSdk-23 floor decision.
- Update the minSdk ledger and `versions.gradle` comment so future dependency
  sweeps do not treat "1.14 stable exists" as enough to raise the floor.

## Verification

- Queried Google Maven metadata for `com.google.android.material:material` and
  confirmed `latest` / `release` are `1.14.0`.
- Re-read `docs/policy/minsdk-21-ceiling.md` before deciding against the bump.
- `git diff --check` is sufficient for this docs/comment-only change.
