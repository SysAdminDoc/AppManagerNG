<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue from here — after iter 123

## Current state

- Branch: `main`
- Latest completed row: Docs **F-Droid 2.0 ROM JSON Pre-Seeding Format**
- Validation completed:
  - JSON sample parsed with PowerShell `ConvertFrom-Json`
  - XML sample parsed with PowerShell `[xml]`

## What just shipped

The distribution docs now include F-Droid 2.0 ROM preseed guidance and sample
files. The guide deliberately treats the samples as templates because
AppManagerNG does not yet have a production F-Droid repository URL and repo
signing certificate outside the normal listing work.

## Next visible roadmap work

The next visible unshipped `Next` row after iter 123 is:

| Row | Tier | Status |
| --- | --- | --- |
| **Android 17 ACCESS_LOCAL_NETWORK + Static-Final Reflection Ban** | Eng-Debt | **Next** |

The adjacent **F-Droid 2.0 Index v2 Protobuf** row is marked **Later**, so do
not treat it as the immediate next task unless the roadmap is reprioritized.

## Notes for the next pass

- This is an audit batch, not one guaranteed source patch. Start by reading the
  row text and source appendix entries `[S169]` / `[S170]`.
- Verify current source before changing anything; prior Android 17 audits have
  already closed several sibling concerns.
- If a sub-audit is clean or blocked by compile SDK, document that explicitly in
  `ROADMAP.md` and an audit note rather than adding decorative code.
