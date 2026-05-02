# L1 Delta Research — Iteration 6 (2026-05-01)

## Summary

**No net-new sources or feature candidates this iteration.**

The ROADMAP was last fully replenished today during the fifth full research
cycle (per `ROADMAP.md` header: "Last updated: post-v0.3.0 (fifth full
research cycle, 2026-05-01)"). Phase 1 delta scan against `iter-5` source
appendix (S01–S64) yielded no upstream releases, CVE advisories, or
adjacent-project drops in the intervening hours.

## Phase 1 delta scan — null result rationale

- Last cycle covered: 64 distinct sources across 9 source classes
- Intervening time window: hours, not days
- Upstream MuntashirAkon/AppManager: no commits since `3d11bcb` baseline
  applies to this scan (the fork point), and v4.1.0 milestone (24 issues,
  0 closed) is unchanged
- Android platform releases: no new behaviour-change docs from
  `developer.android.com/about/versions/16` or `/17` since last scan
- Adjacent OSS: Neo Backup, Canta, SD Maid SE, Obtainium, AppVerifier all
  on stable releases per S41–S43, S51, S63

## What this iteration does instead

Skip to L2 with the existing 75+ unchecked ROADMAP items. Drain top 3 P0/P1
from the "Now" tier per Large-Repo Mode caps:

1. **T1 — Sort by Dangerous Permissions** (line 185, Low effort,
   infrastructure already exists for tracker_count sort)
2. **T2 — elegantTextHeight Audit** (line 80, Low, Android 16 ship-blocker
   for Arabic/Thai/Indic text rendering)
3. **T3 — Obtainium Config** (line 63, Trivial, distribution unblock for
   privacy-conscious user channel)

## Next L1 trigger

Run a full Phase 1–5 cycle (not delta) when one of these fires:

- Upstream MuntashirAkon/AppManager publishes v4.1.0 or any v4.0.x patch
- Android 17 Developer Preview drops new behaviour-change docs
- Any adjacent project in S41–S43 / S51 / S63 ships a major release
  (Neo Backup 8.4+, Canta 4.x, SD Maid SE 1.8+, Obtainium 1.5+, AppVerifier 0.9+)
- Any S49–S50 BouncyCastle CVE (currently on 1.83, clean)
- 14 days elapse since last full cycle (whichever sooner)
