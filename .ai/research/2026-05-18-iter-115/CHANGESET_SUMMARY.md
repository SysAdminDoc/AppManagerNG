<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 115 — KernelSU App Profile Awareness

## Roadmap item

Shipped T9 **KernelSU App Profile Awareness**.

## Implementation

- Extended `KernelSuDiagnostics` to read the active KernelSU `su` process
  profile from the process credentials NG actually receives:
  - UID
  - GID
  - supplementary groups
  - SELinux context
  - raw `id` output
  - `CapEff`
- Added a small expected-capability policy for AppManagerNG root operations:
  `CAP_CHOWN`, DAC read/write/search capabilities, `CAP_FOWNER`,
  `CAP_SETUID`, `CAP_SETGID`, and `CAP_SYS_ADMIN`.
- Classifies the active profile as default-root, restricted, incomplete, or
  unavailable, and reports missing expected capabilities for restricted
  profiles.
- Settings -> Privileges now includes the App Profile summary in the KernelSU
  status row and the full profile details in the copyable KernelSU dialog.
- Added focused JVM coverage for default-root profile parsing and restricted
  profile warning detection.

## Design note

The probe intentionally observes the effective `su` process instead of parsing
KernelSU private config files. That keeps the diagnostic aligned with the
permissions AppManagerNG will actually use for privileged work and avoids
depending on manager storage internals.

## Verification

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.runner.KernelSuDiagnosticsTest --console=plain`

## Sources used

- KernelSU App Profile guide: `https://kernelsu.org/guide/app-profile.html`
- Roadmap source S130: Hail v1.10.0 App Profile behavior
