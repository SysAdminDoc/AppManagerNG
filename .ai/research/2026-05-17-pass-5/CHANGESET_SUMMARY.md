<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET_SUMMARY — 2026-05-17 pass 5

## Modified

- `OnboardingFragment.java` — Wireless ADB setup now preflights the Developer
  Options USB-debugging flag and prompts users to enable both USB debugging and
  Wireless debugging before pairing. Users can still continue explicitly for
  device-specific setups that do not need the guard.
- `strings.xml` — Shizuku, Wireless ADB, setup-prompt, and ADB-pairing copy now
  name both required Developer Options toggles.
- `ROADMAP.md` — T5 "USB Debugging Prompt in Shizuku Setup" marked shipped and
  iter-28 closure added.
- `CHANGELOG.md`, `PROJECT_CONTEXT.md` — pass-5 progress recorded.

## Verification

- XML parse check passed for `app/src/main/res/values/strings.xml`.
- Gradle tests could not run locally: no `JAVA_HOME` / no `java` command available.
