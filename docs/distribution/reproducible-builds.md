<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Reproducible Builds

AppManagerNG's maintainer release process verifies that two clean builds from
the same source tree produce byte-identical APKs before publishing artifacts.

## Build timestamp source

Each APK records `BUILD_TIME_MILLIS`. Release builds resolve that value in a
fixed order: a numeric `SOURCE_DATE_EPOCH` value first, then the Git `HEAD`
commit timestamp. `SOURCE_DATE_EPOCH` uses Unix seconds and is converted to
milliseconds for `BuildConfig`. Both sources therefore produce the same value
when the source revision is the same, even when the builds run on different
machines.

If neither source is available, a release build stops with an explanation
instead of embedding the wall clock. A maintainer doing an intentional local
release can pass `-PallowNonDeterministicBuildTime=true`; the build prints a
warning so that opt-out cannot be mistaken for a reproducible release. Debug
builds retain their wall-clock fallback for local development.

The shell and PowerShell verification scripts report the timestamp source
before starting their two clean release builds. A source archive should set
`SOURCE_DATE_EPOCH` before invoking either script.

## Server JAR verification

The release scripts also verify the privileged server payloads. After the first
release build they copy `am.jar` and `main.jar`, then create a detached Git
worktree at a different absolute path and rebuild only those server JARs with a
different timezone, locale, and builder identity. The two SHA-256 pairs are
printed and saved in `reproducible-release/server-jars.txt`. The server build
sorts class inputs by filename and closes each directory stream before invoking
D8, so input enumeration order does not depend on the filesystem.

## The one command

Reproducibility is one gate among several, and a release must clear all of
them. `scripts/release_gate.py` runs every gate in order and refuses to emit a
receipt unless each one passes:

```bash
python scripts/release_gate.py --tag v0.6.6 \
    --expected-signing-cert <sha256 of the release certificate>
```

| Stage | Refuses the release when |
|---|---|
| `source` | the working tree is dirty, or `--tag` does not resolve to HEAD |
| `consistency` | any version-bearing surface disagrees |
| `floor` | a pinned dependency has drifted past its ceiling |
| `translation` | a source string regressed, or the report's own counts disagree |
| `tests` | a host unit test fails, or the task reports success with no results |
| `lint` | a lint issue is not in the baseline, or the baseline has stale entries |
| `reproducible` | two clean builds differ, or the SBOM, page-alignment, or blocking CVE check fails |
| `artifact` | the built APK's package, version, SDK level, or signer is not the one the sources declare |

The receipt is written last and only on success, to
`build/release-gate/release-gate-receipt.json`. It binds the released commit
and tag to the SHA-256 of every published artifact and report, the signing
certificate fingerprint, the identity the sources declared, and the versions of
the tools that produced them — so a receipt can never describe a build that did
not pass, or an artifact other than the one that was checked.

Stage selection (`--only` / `--skip`) exists for maintainer iteration. The
receipt records exactly which stages ran, so a partial run is visibly partial.

### The lint baseline

The gate runs lint with the committed baseline moved aside and compares the
results itself. Letting lint apply its own baseline hides entries that no
longer match, so a baseline rots indefinitely; running unfiltered answers both
questions from one analysis — what is new, and what is stale. Issues are
matched by rule, module-relative file, and message, never by line number, so
unrelated edits do not manufacture findings.

When the baseline has drifted, `--refresh-lint-baseline` installs the baseline
that lint regenerated during the same run and stops so the diff can be
reviewed before it is committed. The gate never edits the baseline into shape
itself.

## How reproducibility is checked

1. **Two clean builds**. The maintainer runs
   `scripts/verify_reproducible_release.ps1` or
   `scripts/verify_reproducible_release.sh`, which runs
   `./gradlew clean :app:assembleRelease` twice from the local checkout.
2. **SHA-256 comparison** - every output APK's hash is compared across the two
   builds. If any APK differs, the release is rejected.
3. **Sidecar publication** - each verified APK is published alongside a
   `.sha256` file containing its hash and a combined `sha256.txt` covering all
   release assets.
4. **16 KB page-alignment check** - `scripts/verify-native-page-alignment.py`
   confirms native `.so` entries are aligned to 16 KB pages (Android 15+
   requirement).
5. **CycloneDX SBOM** - `scripts/generate-cyclonedx-sbom.py` produces a
   software bill of materials attached to the release.

## Local Verification

Maintainers can run the same two-build-and-compare locally:

```powershell
# Windows
.\scripts\verify_reproducible_release.ps1
```

```bash
# Linux / macOS shell
./scripts/verify_reproducible_release.sh
```

Both scripts:
- Build twice into `build/reproducible-release/{first,second}/`
- Compare per-APK SHA-256 hashes
- Copy verified APKs to `build/reproducible-release/publish/` with
  `AppManagerNG-reproducible-<variant>.apk` naming
- Run native page-alignment and SBOM generation/validation
- Write `release-assets.txt` listing all publishable artifacts

## Published Release Receipt

`docs/distribution/release-receipt.json` records the selected published APK's
tag commit, version name/code, artifact name/size/SHA-256, and signing
certificate SHA-256. After publishing, verify the downloaded artifact and
update that receipt once; `scripts/verify-release-consistency.sh` then rejects
drift in the Accrescent, F-Droid, and IzzyOnDroid packets. The parser's host
tests deliberately inject stale Markdown, YAML, and hash values:

```bash
python -m unittest scripts.tests.test_verify_release_metadata
```

## Why Reproducibility Matters

- **F-Droid / IzzyOnDroid** - reproducible builds are a prerequisite for
  verified-source badges in F-Droid repositories.
- **User trust** - anyone with the same JDK, Android SDK, and source tree can
  verify that the published APK matches the source.
- **Supply chain** - byte-identical builds prove the release machine and local
  build process introduced no unexpected modifications.

## Known Constraints

- Reproducibility is verified within a single local run (same JDK, SDK, OS).
  The verifier covers the server JARs from a second absolute path and runtime
  environment. Other toolchain differences, including JDK and Android SDK
  versions, still need matching toolchain inputs for a byte-identical APK.
- The debug keystore (`dev_keystore.jks`) is checked into the repo so debug
  builds are also reproducible across developers. Release signing uses the
  maintainer's local release keystore referenced by `app/keystore.properties`.
- R8/ProGuard determinism depends on the AGP version. The project pins AGP
  and Gradle versions in `versions.gradle` to minimize drift.
