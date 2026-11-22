<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# Blocked Roadmap Items

Items here wait on something the code can't settle, like an owner decision or a dependency release that doesn't exist yet. Move one back to ROADMAP.md once its blocker is gone.

## Owner decisions

- [ ] P1. Remove the hard-coded debug signing passwords
  Why: `app/build.gradle` has literal `storePassword` and `keyPassword` values in the debug signing branch, while `CONTRIBUTING.md` says no secrets in source.
  Blocker: the maintainer notes call the shared debug keystore intentional (debug builds install over each other on every machine), so changing it is the owner's call.
  Re-tested 2026-11-21: still there (now lines 53 and 54), and the notes haven't changed.
  Complexity: S

- [ ] P2. Release file names with the ABI and a short SHA-256 (issue #44)
  Why: #44 asks for release APKs named like `AppManagerNG-v0.8.0-full-universal-b32438a1.apk`, so a file carries its own checksum and the installer can check it.
  Blocker: the published names (`AppManagerNG-reproducible-floss-release.apk` and friends, unchanged since v0.6) are what Obtainium and other update trackers match on, so renaming them breaks updates for people tracking the old pattern. That's the owner's call, made at release time. The in-app parts of #44 (name tokens for saved APKs, the installer check of a checksum in the file name) don't wait on it.
  Re-tested 2026-11-21: v0.8.0's 15 assets still use the old names.
  Complexity: S

- [ ] P3. On-device AI models (issue #46)
  Why: #46 proposes a small embedding model for meaning-based search in the toast, notification and app-op logs, and a Gemma-class model for an assistant, translation and screenshot help.
  Blocker: shipping or downloading a model and an inference runtime (LiteRT, ONNX Runtime or ExecuTorch) adds tens to thousands of MB and a native dependency to an offline-first GPL app. Whether the app takes that on, and where models would come from, is the owner's call. The search part of the request is covered without a model: Related words search in Toast, Notification and Operation history (`a28cf249c`, a built-in word list plus a stemmer). What's left here is the model-backed assistant, translation and screenshot help.
  Re-tested 2026-11-21: nothing in the app or its dependencies loads a model today.
  Complexity: L

- [ ] P3. DDG Tracker Radar as a second tracker source
  Why: TrackerControl adds DuckDuckGo's mobile tracker list on top of Exodus for wider coverage.
  Blocker: the data's license. The LICENSE files in duckduckgo/tracker-radar and duckduckgo/tracker-blocklists say CC BY-NC-SA 4.0, and whether non-commercial data can ship inside a GPL app is the owner's call. Once that's settled there's still a design choice, since the list is domain-based and the scanner matches class names.
  Re-tested 2026-11-21: both LICENSE files still say CC BY-NC-SA 4.0 (both repos had commits since, the licenses didn't change).
  Complexity: M

## Outside services and dependencies

- [ ] P2. Hosted translation project and NG string catch-up
  Why: translators need a Weblate or Crowdin project to work in.
  Blocker: creating and running one needs the owner's account. Language files (issue #31) let people translate and share without it in the meantime.
  Re-tested 2026-11-21: Weblate's API still returns 404 for appmanagerng (upstream's app-manager returns 200), and there's no Crowdin project.
  Complexity: M

- [ ] P3. Show APK Signature Scheme v3.2 (PQC)
  Why: Android 17 adds hybrid ML-DSA signing, and the signing chip should say "v3.2 (PQC)" for those APKs.
  Blocker: no verifier exposes it yet. The change in `PackageUtils.getSignerInfo` is one line behind a version check once one does.
  Re-tested 2026-11-21: apksig-android's last commit is still 2023-05-01, and Google's newest apksig (9.6.0-alpha05, published 2026-11-20) still has no v3.2 or ML-DSA classes.
  Complexity: S

## Hands at a phone

- [ ] P1. Check the wireless reconnect after a reboot on a phone
  Why: the upstream wireless fixes are ported, and on the S22 on 2026-09-30 AppManagerNG paired, connected over Wireless debugging and came back on its own after Wi-Fi went off and on. The start at boot, which Android 15 only allows as a special use service, has host tests but hasn't run on a phone yet.
  Evidence: `self/BootReceiver.java`, `servermanager/WifiWaitService.java`; upstream `22d439d61` (connect on boot).
  Acceptance: with Wireless debugging as the mode, the network always allowed for Wireless debugging and the server stopped, `adb reboot` the S22 or S25 and unlock it; the server is running within two minutes of the unlock without the app being opened. Then join an access point Wireless debugging isn't allowed on yet: one system prompt and one AppManagerNG notification come up, not one per retry, and the server starts once the network is allowed. (Already seen on the S22 on 2026-11-15: the log shows the echo only as "(echo of the launch command, not logged)" and reaches "Success!", and the server came back over Wireless debugging from the app change job without the app being opened.)
  Blocker: both test phones unlock only with a pattern the owner draws, so after `adb reboot` the check waits for the owner at the phone. The access point half also needs a second Wi-Fi network that Wireless debugging hasn't been allowed on yet.
  Re-tested 2026-11-21: neither phone shows up in `adb devices` today (only emulators), and the pattern unlock hasn't changed.
  Complexity: S
