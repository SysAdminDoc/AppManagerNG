<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# docs/audits/ — AppManagerNG audit doctrine

This directory holds **per-behaviour-change audit verdicts**: one Markdown file per audit,
documenting whether AppManagerNG's source tree is affected by a specific Android-version
behaviour change, library CVE, or platform-policy enforcement window.

This pattern is the project's primary regression-test surface for things that compilers
and unit tests can't catch — silent platform drift, deprecated-API removal, policy
changes that don't fail builds but break user flows.

---

## When to write an audit

Stand up an audit doc whenever **any** of the following happens:

1. Google publishes an Android behaviour-change page for a new API level (e.g. Android 17 (API 37) `behavior-changes-17`).
2. An Android-platform policy starts enforcing a previously-warn-only rule (Google Play Contacts/Location-button policy, Developer Verification rollout).
3. A direct or transitive dependency ships a security fix that may or may not affect us (BouncyCastle CVE bumps, Gson strict-parsing change).
4. A library we depend on deprecates / removes a public API we touch (libsu 6.0.0 `Shell.cmd` migration, `announceForAccessibility` deprecation).
5. An upstream issue surfaces a regression on a specific device/ROM that we want a recorded verdict on (predictive-back WebView freeze, Android 16 QPR2 `clearApplicationUserData` no-op).
6. A maintainer-facing concern about an existing-implementation pattern needs a clean / dirty verdict (zip-slip protection, GCM cipher reuse, static-final reflection).

The audit doc records that **somebody actually looked**. That's its job.

---

## Filename convention

`<YYYY-MM-DD>-<topic-kebab-case>.md`

Examples in this directory:

- `2026-05-01-elegant-text-height.md`
- `2026-05-02-android17-keystore-key-cap.md`
- `2026-05-08-bouncycastle-1-84-cve-bump.md`
- `2026-05-09-predictive-back-webview.md`

The date is the date the audit was performed (or the audit doc was committed — within a day of each other). The topic is short, lowercase, kebab-case. Android version goes in the topic when the audit is platform-version-specific (`android17-`, `android18-`).

---

## Document shape

A minimum-viable audit doc has these sections:

```markdown
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Audit: <one-line title>

**Date:** YYYY-MM-DD
**Source:** <one or more URLs — Android docs, CVE record, upstream issue, library changelog>
**Audited against:** <git revision / tag of the working tree being audited>
**Roadmap row:** <ROADMAP.md tier / iter row that this closes, if any>

## Premise

What behaviour change / CVE / policy / regression are we checking against?
Cite the source verbatim in 1–3 sentences.

## Sweep methodology

How did the audit search the source tree? Be specific so the audit is reproducible.

- `Grep` patterns used (literal: `grep -rn "MessageQueue" app/`, etc.)
- File roots covered (`app/`, `libcore/`, `libserver/`, `libopenpgp/`, `hiddenapi/`, `server/`)
- Manual review surfaces (e.g. AndroidManifest.xml `<activity>` enumeration)
- Tooling invoked (`apksigner verify`, `dependency-check`, `osv-scanner`)

## Findings

For each finding:

- **File:** [path:line](path#L<line>)
- **Pattern:** what was matched
- **Verdict:** clean / matches-found-but-pattern-correct / remediated / confirmed-needs-fix / n/a

## Verdict

One of:

- ✅ **clean** — zero matches; no remediation required
- ✅ **clean (audit)** — matches present but verified correct under the new behaviour
- ✅ **remediated** — matches found and fixed in this audit pass; commit reference
- ⚠️ **confirmed, needs-design** — matches found; fix requires architectural decision; ROADMAP row added
- ⚠️ **deferred** — matches found; fix gated on dependency / API floor / upstream
- ❌ **n/a** — audit premise turned out stale (e.g. NG doesn't ship the affected surface)

## Follow-ups

Anything the audit surfaced that's *not* the primary finding — typically other rows for
the engineering-debt register or other audits to schedule.
```

---

## Verdict vocabulary

The verdict line at the top of each audit is the artifact's most-quoted output. Use exactly one of:

| Verdict | When to use | Example |
|---|---|---|
| `clean` | Zero matches in the source tree | `2026-05-01-elegant-text-height.md` |
| `clean (audit)` | Matches present but verified correct under the new rule | `2026-05-02-android17-messagequeue.md` |
| `remediated` | Matches found, fix shipped in the same commit/PR | `2026-05-02-android18-implicit-uri-grant.md` |
| `confirmed, needs-design` | Matches found, fix non-trivial; opens / updates a ROADMAP row | `2026-05-08-gcm-cipher-reuse-large-backup.md` |
| `deferred` | Matches found but fix gated externally (dep, API floor, upstream) | (audit pattern; see roadmap Eng-Debt for current examples) |
| `n/a` | Premise turned out stale — NG doesn't ship the affected surface | `2026-05-08-google-play-contacts-location-policy.md`, `2026-05-09-predictive-back-webview.md` |

---

## Cross-references

- **ROADMAP**: every audit should be cited from the ROADMAP row it closes (search the doc for `2026-MM-DD-<topic>`).
- **CHANGELOG**: any audit that produced source changes lands as a `### Compliance —` or `### Security —` entry under `## Unreleased` in [`../../CHANGELOG.md`](../../CHANGELOG.md).
- **Engineering Debt Register**: deferred audits link from there too — see ROADMAP §"Engineering Debt Register".
- **Source register**: external citations in audits use the same `[Sxxx]` keys as ROADMAP (see ROADMAP §"Source Appendix").

---

## Why this pattern is load-bearing

Upstream `MuntashirAkon/AppManager` documented that **"a migration to a new version of Android roughly takes 80 hours alone as it is necessary to revise entire hidden API library"** ([ROADMAP S137]). The audit doctrine is the project's defence against that cliff: every behaviour-change page that affects NG ends with a dated, recorded verdict — so when the next Android version ships, the contributor's first move is `ls docs/audits/ | grep android17` rather than redoing 80 hours of sweep work.

As of 2026-05-17 the directory holds 21 audits. The pattern scales: a new contributor can add an audit for an Android-18 behaviour change without reading the rest of the codebase, by cloning the structure of an existing audit doc.
