<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Autonomous Loop State

Last updated: 2026-06-06
Assigned project: `C:\Users\--\repos\AppManagerNG`
Current branch: `main`

## Latest Cycle

- Result: completed Cycle 18 implementation for release SBOM publishing and
  GitHub artifact attestations.
- Updated: `ROADMAP.md` marks the release SBOM/provenance row shipped,
  `COMPLETED.md` records the closed release-trust guardrail, `CHANGELOG.md`
  records the Unreleased release-integrity change, and
  `docs/distribution/reproducible-builds.md` documents SBOM/checksum/
  attestation verification.
- Code: `scripts/generate-cyclonedx-sbom.py` emits a CycloneDX 1.6 aggregate
  SBOM from the checked Gradle lockfiles and validates the generated app
  component, Maven purls, duplicate references, and aggregate dependency list.
  Both reproducible-release wrappers add `AppManagerNG-reproducible.cdx.json`
  to their publish asset list. The tag workflow uploads a versioned
  `AppManagerNG-<version>.cdx.json` release asset and uses `actions/attest@v4`
  for APK provenance plus CycloneDX SBOM predicate attestations.
- Verification: passed
  `python scripts\generate-cyclonedx-sbom.py --version 0.5.0 --output build\reproducible-release\publish\AppManagerNG-reproducible.cdx.json`,
  `python scripts\generate-cyclonedx-sbom.py --check build\reproducible-release\publish\AppManagerNG-reproducible.cdx.json`,
  official CycloneDX 1.6 JSON schema validation via Python `jsonschema`,
  `bash -n scripts/verify_reproducible_release.sh`, PowerShell script parse
  check, and `rtk git diff --check`. Full `gh attestation verify` proof remains
  release-tag dependent after GitHub records attestations.
- Environment note: the ignored local `local.properties` still points at
  `C:\Users\--\AppData\Local\Android\Sdk` so Gradle can use the installed SDK on
  this host. Do not commit `local.properties`.

## Next Cycle

- Continue this same assigned project.
- Next host-verifiable roadmap target: `P1 - Harden the foreground UI tracker
  overlay against device-freeze reports`.
- Start by inspecting `TrackerWindow`, `NoRootAccessibilityService`, overlay
  `WindowManager.LayoutParams` construction/update paths, tracker settings copy,
  and any existing accessibility/overlay tests.
- Implementation constraint: make conservative bounds/throttle/failsafe changes
  around the existing accessibility overlay without changing privilege routing
  or tracker feature semantics.
- Verification target: focused JVM tests for the extracted overlay policy,
  Java compile for touched app code, docs/state update, and `rtk git diff
  --check`.
- Parked follow-ups: device-only Running Apps restore walkthrough, manual
  Android assist invocation, real tag-run attestation verification,
  distribution submissions, and Android 17 target-SDK gate.
