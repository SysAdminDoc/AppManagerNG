<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue from here — pass 40 (iter-63)

Date: 2026-05-17

## Current state

Pass 40 produced two deliverables:

1. **Code-side reliability/security fix set** (uncommitted in working tree):
   FD leak, lazy-init races, native-allocator UAF race, shell-injection
   defense-in-depth, malformed-URI crash, IFW rules-import truncation,
   null-cursor NPE, and a systemic wake-lock-leak helper wired into the four
   foreground services. Full file list in
   [`CHANGESET_SUMMARY.md`](CHANGESET_SUMMARY.md).

2. **ROADMAP iter-63 section**: three corrections to existing rows
   (Material 1.14.0 is actually stable; AGP 10 dated migration cliff; Bouncy
   Castle 1.85 status), eleven new candidate rows, two new Rejected entries,
   and a closures subsection mirroring the code-side fix set. New sources
   S341–S361 appended to the Source Appendix.

## Verification status

- `git diff --check`: clean.
- New unit test (`PackageUtilsPackageNameValidationTest`) follows project JUnit 4
  convention; not yet run because environment lacks `JAVA_HOME` / `javac`
  (consistent with pass-39 blocker).
- Markdown lint on `ROADMAP.md`: one new MD051 link-fragment warning was
  introduced (em-dash slug) and fixed inline. Remaining MD032/MD060 style
  warnings are pre-existing throughout the file.

## Suggested commit shape

Per the conventional-commit style in `git log --oneline -10`:

```text
fix(audit): pass-40 reliability/security findings

Fixes a multi-class set of reliability and security issues surfaced by
the pass-40 autonomous audit:

  - crypto(openpgp): close streams in handleFiles; guard close() against
    double-unregister
  - uri(grant): null-safe getRealPath substring + Uri.parse + authority
  - adb/keystore/db/static-dataset: synchronized lazy-init
  - utils(package): plausible-name guard before shell-eval pm dump
  - fm(viewer): null-cursor guard on SAF listing
  - utils(cpu): acquireWakeLock helper with fallback timeout; route four
    foreground services through it
  - rules(component): skip single malformed IFW entry instead of dropping
    the whole file
  - test(utils): lock down package-name validator against shell injection
```

Plus a separate roadmap-only commit:

```text
docs(roadmap): iter-63 research additions, 21 new sources

Adds Iter-63 Research Additions section, updates two Engineering Debt
Register rows in place (Material 1.14.0 is actually stable; AGP 10 dated
cliff), and appends S341-S361 to the Source Appendix.
```

The two commits are independent and can be reviewed separately.

## Next roadmap item if work resumes later

The next open `Now` row after Restricted Settings (closed iter-62), by
roadmap order, remains:

```text
ROADMAP.md — Snapshot Bundle Export/Import
```

That row asks for one-button export/import of `prefs/`, `profiles/`, `tags/`,
`history.db`, audit log, and bundled-data manifest with a schema-version
header, before the applicationId rename risk window.

Pass-40-specific follow-ups are listed in the ROADMAP "Iter-63 Out-of-Scope
Follow-up" subsection.

## Caveats

- Push is expected to remain blocked unless GitHub auth is fixed. The remote is
  `https://github.com/SysAdminDoc/AppManagerNG.git`; `gh auth status` has shown
  the default account as `MavenImaging`, which is not authorized for this repo.
- Shared-drive Git may print non-fatal repack warnings. Confirm the commit and
  fsck state after any commit.
- Per project convention (`teamstation-commit-style` memory pattern applies
  here too — confirmed by inspection of recent `git log --format=fuller`):
  do **NOT** add `Co-Authored-By: Claude` trailer.
