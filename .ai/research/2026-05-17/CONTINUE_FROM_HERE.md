<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-17

Continuation pointer for a future autonomous session picking up after the 2026-05-17
research consolidation pass.

---

## 1. Current state

**The 2026-05-17 session completed its full prompt scope.** All required artifacts shipped:

- `PROJECT_CONTEXT.md` (canonical entry point) — written
- `.ai/research/2026-05-17/STATE_OF_REPO.md` — written
- `.ai/research/2026-05-17/MEMORY_CONSOLIDATION.md` — written
- `.ai/research/2026-05-17/SOURCE_REGISTER.md` — written
- `.ai/research/2026-05-17/RESEARCH_LOG.md` — written
- `.ai/research/2026-05-17/COMPETITOR_MATRIX.md` — written
- `.ai/research/2026-05-17/FEATURE_BACKLOG.md` — written
- `.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md` — written
- `.ai/research/2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md` — written
- `.ai/research/2026-05-17/DATASET_MODEL_INTEGRATION_REVIEW.md` — written
- `.ai/research/2026-05-17/CHANGESET_SUMMARY.md` — written

This file exists because the prompt template asked for it explicitly *only* if a hard
limit was hit before full completion. **No hard limits were hit today** — this file is
provided as a forward-looking continuation pointer for the *next* session.

---

## 2. What the next session should start with

Two scenarios:

### Scenario A — iter-24 planning sync follow-through

If the maintainer has reviewed the 2026-05-17 artifacts and wants to action the
`FEATURE_BACKLOG.md` items, the next session's input is the eight **Now**-tier items
listed in `PRIORITIZATION_MATRIX.md` §2. Suggested ordering:

1. Commit the 6 in-progress files as 3 logical commits (F-NEW-01, -02, -03)
2. Refresh `CLAUDE.md` Status + add pointer to `PROJECT_CONTEXT.md` (F-NEW-04)
3. Refresh `README.md` Roadmap preview (F-NEW-05)
4. Append S316 → S320 to ROADMAP Source Appendix (F-NEW-07)
5. Add minSdk-cascade comment in `versions.gradle` (F-NEW-08)
6. Add `docs/audits/README.md` (F-NEW-12)

These can ship as a single `iter-24` housekeeping commit or per-item — the maintainer's
call. Total effort ≤4 hours.

### Scenario B — fresh research iteration (iter-25)

If a few weeks have passed since 2026-05-17 and the maintainer wants a fresh delta,
the next session should:

1. **Read `PROJECT_CONTEXT.md` first** (the canonical entry index). Do not begin with `find . -name '*.md'`.
2. Check ROADMAP "Last updated" line and find the latest iter-N inline section.
3. Diff against the world: which competitor GitHub repos have shipped a new release since the last iter? Which Android docs have a new behaviour-change note? Run the same targeted probes that worked in iter-18 → iter-23:
   - Competitor releases: Canta, Hail, Neo-Backup, UAD-NG, Inure, SD Maid SE, Shizuku, Magisk, KernelSU, Material Files, AppVerifier, Obtainium, LibChecker, Droid-ify
   - Upstream issues: `MuntashirAkon/AppManager` open since the last iter
   - Android platform: 16 QPR3 (Q3 2026 expected); 17 Beta progression; AGP release notes
   - F-Droid: 2.0-alpha progression (currently alpha9 per S316)
4. Output as a new iter section in ROADMAP, not as a separate file (the post-iter-7 pattern).

### Scenario C — F-NEW-14 architecture-docs stand-up

Independent of either scenario, the highest-leverage net-new item in `FEATURE_BACKLOG.md`
is **F-NEW-14**: standing up `docs/architecture/{01-privilege-providers,02-backup-format,03-hidden-api-bypass}.md`. This directly closes the open ROADMAP T11 "Architecture Documentation" row and reduces iter-N+1 onboarding time materially.

A future session can take F-NEW-14 in isolation. The expected effort is 3/5 (a working
day of writing) and the reading prep is:

- `app/src/main/java/io/github/muntashirakon/AppManager/runner/` for the privilege-provider abstraction
- `app/src/main/java/io/github/muntashirakon/AppManager/backup/` + `crypto/` for the backup-format spec (especially `BackupOp`, `RestoreOp`, `AESCrypto`, `BackupItems.Checksum`, the metadata-v6 IV derivation in `AESCryptoTest`)
- `hiddenapi/` module + `app/src/main/assets/api/api-versions-*.json` + `CompatUtil` for the hidden-API bypass story

---

## 3. Known gaps the next session should not waste time on

Per the saturation analysis in `RESEARCH_LOG.md` §3, these are categories the existing
roadmap deliberately stakes as out-of-scope or under-consideration. **Re-litigating them
costs the project nothing:**

- Hardware-key inspection (only KeyAttestation [S73] cited)
- Enterprise MDM beyond Dhizuku (intentionally narrow — Island [S105] and OwnDroid [S158] are referenced, that's enough)
- Watch / Auto / Automotive (intentionally out — [S280] documents the AAOS rejection rationale)
- Compose migration (explicitly Out-of-scope per `codexprompt.md`)
- Full on-device store / in-app update tracking (rejected — Obtainium owns this)
- Multi-device fleet orchestration (rejected — UAD-NG is desktop, NG is on-device)
- DDG Tracker Radar dataset bundling (rejected — CC-BY-NC-SA incompatible with GPL redistribution)
- Shizuku-iptables firewall (rejected — conflicts with "govern, don't proxy" stance)
- AccessibilityService-based auto-freeze (rejected — security/policy posture incompatible)

If a future session has a genuinely new argument for one of these, that's a real
research finding worth surfacing. Otherwise, leave them alone.

---

## 4. Operational tips

- **`ROADMAP.md` is 1135 lines (~30k tokens).** Use `Read` with `offset` and `limit` rather than attempting a single read; the Source Appendix at the bottom (lines ~820–1135) is most useful as referenced lookup, not re-read.
- **Audit-doc convention**: `docs/audits/<yyyy-mm-dd>-<topic>.md` per the existing pattern (14 examples to mimic). Each carries the source link, the sweep methodology, and the verdict in the first 30 lines.
- **CHANGELOG `Unreleased` section** accumulates from the most-recent `v<n.n.n> —` header upward. New entries go *under* `## Unreleased` and *above* the previous version header.
- **Source IDs**: continue numbering from S316+ if you add new sources. Do not renumber existing S-IDs; doing so would invalidate the dense cross-references throughout ROADMAP and CHANGELOG.

---

## 5. End state

If this file is being read today (2026-05-17 or thereabouts), the project is in a
**high-readiness state**. The planning surface is comprehensive; the audit doctrine is
working; CI runs CodeQL + OWASP dep-check + reproducibility verification weekly. The
single load-bearing inefficiency identified by this session — the silent `Pattern.quote`
on user-supplied regex predicates that neutered the iter-23 tracker-name regex work — is
already fixed in the working tree, awaiting a commit.

The session's net contribution was a canonical entry-point index (`PROJECT_CONTEXT.md`)
that should reduce the per-session orientation overhead for future AI work in this repo
from ~30 minutes to ~5.
