<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# Blocked Roadmap Items

Items here wait on something the code can't settle, like an owner decision or a
dependency release that doesn't exist yet. Move back to ROADMAP.md once the
blocker is resolved.

## Design-Decision-Gated

### P3

- [ ] P3 — DDG Tracker Radar as supplementary tracker source
  Why: TrackerControl layers DuckDuckGo's mobile-specific tracker database on top of Exodus for broader coverage. DDG Tracker Radar is MIT-licensed and maintained by DuckDuckGo.
  Evidence: TrackerControl multi-source approach; DDG Tracker Radar mobile TDS at staticcdn.duckduckgo.com
  Touches: scanner/ (new domain matching pass), app/src/main/assets/ (DDG data), StaticDataset.java, ScannerViewModel.java
  Blocker: DDG Tracker Radar is domain-based (tracking which network domains apps contact), while NG's scanner is class-name-based (Aho-Corasick matching against Exodus code signatures). Integration requires a design decision: (a) static domain extraction from DEX string pool + manifest (imprecise, high false-positive risk), (b) runtime network monitoring via VPN/firewall (scope creep for a package manager), or (c) build-time cross-reference of DDG entities with existing libs.json entries to enrich metadata without new detection (low value). The libs.json `network` signature field already exists but is unused at runtime.
  Re-tested 2026-11-07: the hard blocker is now the data license, not the design choice. The LICENSE files in duckduckgo/tracker-radar and duckduckgo/tracker-blocklists both say CC BY-NC-SA 4.0 (not MIT), and whether non-commercial data can ship inside a GPL app is the owner's call.
  Complexity: M

## Dependency-Gated

### P3

- [ ] P3 — APK Signature Scheme v3.2 display
  Why: Android 17 ships hybrid PQC signing (ML-DSA + classical). The signing cert chip should show "v3.2 (PQC)" for hybrid-signed APKs when the detection API is available.
  Evidence: PackageUtils.java:882 TODO comment; Android 17 PQC upgrade docs
  Touches: utils/PackageUtils.java (getSignerInfo), details/info/ (signing cert display), apksig dependency
  Blocker: apksig-android (MuntashirAkon/apksig-android) does not yet expose `isVerifiedUsingV32Scheme()`. The one-line change is ready — gate behind a version check when upstream ships it.
  Re-tested 2026-11-07: the API still doesn't exist. apksig-android's last commit is from 2023-05-01, and Google's apksig 9.5.1 and 9.6.0-alpha03 only have the V1, V2, V3, V31 and V4 checks, with no v3.2 or ML-DSA verifier.
  Complexity: S

## External-Service-Gated

### P2

- [ ] P2 — Fork-owned translation pipeline and NG-string catch-up
  Why: translation intake needs a hosted Weblate or Crowdin project before
  repo-side configuration has a real destination.
  Evidence: README.md translation note; current hosted project is not live.
  Blocker: requires maintainer/account action to create and operate the
  hosted translation service.
  Re-tested 2026-11-07: there's still no hosted project. The Weblate API returns 404 for appmanagerng while upstream's app-manager returns 200, and creating a Weblate or Crowdin project needs the owner's account.
  Complexity: M

## Maintainer-Policy-Gated

### P1

- [ ] P1 — Remove hard-coded local debug signing credentials
  Why: `app/build.gradle` embeds literal `storePassword` and `keyPassword` values in a tracked debug signing branch even though `*.jks` is ignored and `CONTRIBUTING.md` prohibits secrets in source.
  Evidence: `app/build.gradle`; `.gitignore`; `CONTRIBUTING.md`; `CLAUDE.md` explicitly documents the shared debug keystore/password as intentional and says not to purge it.
  Touches: `app/build.gradle`, shared debug-signing policy, build/release consistency tests.
  Acceptance: after maintainer policy changes, no tracked file contains an unintended signing password while shared debug builds retain their intended install-over-install behavior; release signing behavior and certificate metadata remain unchanged.
  Blocker: explicit repository policy conflicts with the research recommendation; changing the shared debug-signing contract requires maintainer direction.
  Re-tested 2026-11-07: the passwords are still on `app/build.gradle` lines 51 and 52, and the maintainer notes still call the shared debug keystore intentional, so changing it waits on the owner.
  Complexity: S
