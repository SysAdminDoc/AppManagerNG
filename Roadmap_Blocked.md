<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# Blocked Roadmap Items

Items here wait on something the code can't settle, like an owner decision or a dependency release that doesn't exist yet. Move one back to ROADMAP.md once its blocker is gone.

## Owner decisions

- [ ] P1. Remove the hard-coded debug signing passwords
  Why: `app/build.gradle` has literal `storePassword` and `keyPassword` values in the debug signing branch, while `CONTRIBUTING.md` says no secrets in source.
  Blocker: the maintainer notes call the shared debug keystore intentional (debug builds install over each other on every machine), so changing it is the owner's call.
  Re-tested 2026-11-13: still on lines 51 and 52, and the notes haven't changed.
  Complexity: S

- [ ] P3. DDG Tracker Radar as a second tracker source
  Why: TrackerControl adds DuckDuckGo's mobile tracker list on top of Exodus for wider coverage.
  Blocker: the data's license. The LICENSE files in duckduckgo/tracker-radar and duckduckgo/tracker-blocklists say CC BY-NC-SA 4.0, and whether non-commercial data can ship inside a GPL app is the owner's call. Once that's settled there's still a design choice, since the list is domain-based and the scanner matches class names.
  Re-tested 2026-11-13: both LICENSE files still say CC BY-NC-SA 4.0 (tracker-blocklists was updated today, the license wasn't).
  Complexity: M

## Outside services and dependencies

- [ ] P2. Hosted translation project and NG string catch-up
  Why: translators need a Weblate or Crowdin project to work in.
  Blocker: creating and running one needs the owner's account. Language files (issue #31) let people translate and share without it in the meantime.
  Re-tested 2026-11-13: Weblate's API still returns 404 for appmanagerng (upstream's app-manager returns 200), and there's no Crowdin project.
  Complexity: M

- [ ] P3. Show APK Signature Scheme v3.2 (PQC)
  Why: Android 17 adds hybrid ML-DSA signing, and the signing chip should say "v3.2 (PQC)" for those APKs.
  Blocker: no verifier exposes it yet. The change in `PackageUtils.getSignerInfo` is one line behind a version check once one does.
  Re-tested 2026-11-13: apksig-android's last commit is still 2023-05-01, Google's newest apksig (9.6.0-alpha03) has no v3.2 or ML-DSA classes, and AOSP's apksig hasn't changed since March 2025.
  Complexity: S
