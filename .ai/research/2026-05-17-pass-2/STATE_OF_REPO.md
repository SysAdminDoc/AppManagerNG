<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# STATE_OF_REPO — 2026-05-17 pass 2

Delta against [`../2026-05-17/STATE_OF_REPO.md`](../2026-05-17/STATE_OF_REPO.md). Pass 2
is the iter-24/iter-25 execution pass that landed the in-progress fixes the pass-1
audit had identified as commit-ready.

---

## 1. Commits added this session

Three logical commits closing the iter-24 backlog F-NEW-01/02/03, plus the iter-25
deliverables commit (queued, see §3):

```
25c629a fix(onboarding): capture Application context before background root-manager probe
bcb2874 security(installer): redact userinfo / query / fragment in install transcript URI
73387cd fix(finder): user-supplied regex predicates compiled as regex, not literals
2846225 docs: add PROJECT_CONTEXT + 2026-05-17 research-run consolidation
```

The branch is now **20 commits ahead of `origin/main`** (was 16 at session start; +1
PROJECT_CONTEXT consolidation in pass-1, +3 maintainer-fix commits in pass-2). Push
remains deferred per the `swiftfloris-git-auth.md` VM constraint.

---

## 2. What state the working tree is in

After the three fix commits and before the iter-25 deliverables commit:

```
git status --short:
M ROADMAP.md          (iter-25 section + S321-S325 added)
M CHANGELOG.md        (3 fix entries + iter-25 docs entry)
?? docs/architecture/ (new — 4 files)
?? .ai/research/2026-05-17-pass-2/ (this dir)
```

No source-code modifications outstanding — the working tree is clean except for the
docs / planning artifacts being staged for the iter-25 deliverables commit.

---

## 3. Iter-25 deliverables to commit

A single follow-up commit captures:

| Path | Change | Purpose |
|---|---|---|
| `ROADMAP.md` | Iter-25 Research Additions section + S321-S325 in Source Appendix + Last-updated header refresh | Record the iter-25 findings + S-source additions |
| `CHANGELOG.md` | 4 Unreleased entries (3 fixes + iter-25 docs) | Document the iter-25 work for release-prep |
| `docs/architecture/01-privilege-providers.md` | new file | Closes ROADMAP T11 + iter-24 F-NEW-14 |
| `docs/architecture/02-backup-format.md` | new file | (same) |
| `docs/architecture/03-hidden-api-bypass.md` | new file | (same) |
| `docs/architecture/README.md` | new file | Navigation guide for the new dir |
| `.ai/research/2026-05-17-pass-2/*` | 11 new files | Audit trail for this pass |

Total ~10 new docs + 2 modified docs. Single commit; subject `docs: iter-25 — architecture stand-up + research-run pass 2`.

---

## 4. Verification on the source-code commits

Each of the three fix commits was inspected before commit:

### `73387cd fix(finder): user-supplied regex predicates compiled as regex, not literals`

- **Files**: `FilterOption.java`, `AppOpsOption.java`, `TrackersOption.java`, new `FilterOptionTest.java` (89 lines, Robolectric).
- **Risk**: Low — `FilterOption.setKeyValue()` is the user-input parsing layer for filter rows; the change is from "broken (literal match)" to "working (regex match)". A user with a pre-existing filter whose value *was* a literal substring will still work because the substring is also a valid regex matching itself. Filters whose value contained regex-metacharacters (`.`, `*`, etc.) will start matching *more broadly* — that's the intended behaviour for the regex predicate.
- **Test coverage**: regex-not-literal positive case; `PatternSyntaxException` surfacing; `TYPE_STR_MULTIPLE` fall-through fix.

### `bcb2874 security(installer): redact userinfo / query / fragment in install transcript URI`

- **Files**: `InstallTranscript.java`, expanded `InstallTranscriptTest.java`.
- **Risk**: Very low — the redactor is pure-function URI string manipulation; the surface is the user-initiated "Copy diagnostic info" button. No reachability change.
- **Test coverage**: query-string redaction, userinfo redaction, fragment redaction, path-less authority-only case.

### `25c629a fix(onboarding): capture Application context before background root-manager probe`

- **File**: `OnboardingFragment.java`.
- **Risk**: Very low — moves a one-line context retrieval from worker thread to main thread. Captured value is non-null because `requireContext().getApplicationContext()` is invoked on the main thread where the fragment is attached (the call site is `refreshRootManagerStatus()` which is only invoked from main-thread lifecycle callbacks).
- **No new test** — the failure mode is a silent worker-thread `IllegalStateException` that's hard to assert from Robolectric without a fragment-lifecycle harness.

---

## 5. Updated dep + tooling versions (none today)

No `versions.gradle` version changes in pass-2 — only the comment annotation on
`material_version`. Pass-1's audit of dep currency stands.

---

## 6. CI status

No CI changes this pass. The pass-1 commit landed `tests.yml` and `lint.yml` running on
`main`; those will pick up the new test class and lint the new docs automatically when
the next push happens.

The three fix commits and the iter-25 deliverables commit have not been pushed yet (VM
auth constraint per `swiftfloris-git-auth.md`).

---

## 7. Health snapshot

- The repository's planning surface is **internally consistent** across pass-1 and pass-2 — no contradictions emerged during the iter-25 work.
- The single new external risk discovered today is the **Shizuku 13.6.0 / Android 17 Beta 3 regression** ([S321, S322]) — documented in ROADMAP iter-25 as a T5 Now-tier row.
- The audit-doc doctrine is now documented ([`docs/audits/README.md`](../../../docs/audits/README.md)) and the architecture stack is now documented ([`docs/architecture/`](../../../docs/architecture/)) — two of the highest-leverage future-contributor-onboarding gaps closed in one pass.
- The targetSdk=37 audit batch (5 open audits) is the single largest outstanding Eng-Debt item, and Android 17 stable lands in June 2026 ([S324]). The audit batch is sized to land in the v0.7.x window — neither urgent nor abandoned.

---

**Continuation**: see [`CONTINUE_FROM_HERE.md`](CONTINUE_FROM_HERE.md). Next session
should run the Shizuku Android-17 audit (highest-priority new item) and start the
five open targetSdk=37 sub-audits.
