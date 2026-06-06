<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Reproducible Release Builds

AppManagerNG release publishing is guarded by a two-build reproducibility check.
The release workflow performs two clean signed `:app:assembleRelease` builds from
the same tag, compares every resulting flavor / ABI APK SHA-256 hash, and refuses
to publish if any APK bytes differ.

The Linux/CI equivalent is:

```bash
bash scripts/verify_reproducible_release.sh
```

On Windows, use the PowerShell wrapper so Gradle reads the Windows Android SDK
path from `local.properties`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify_reproducible_release.ps1
```

The script writes comparison artifacts to `build/reproducible-release/`:

- `first/*.apk`
- `second/*.apk`
- `publish/AppManagerNG-reproducible-*.apk`
- `publish/AppManagerNG-reproducible.cdx.json`
- `sha256.txt`
- `release-assets.txt`

Determinism controls currently in place:

- `BuildConfig.BUILD_TIME_MILLIS` comes from the git commit timestamp, matching
  upstream App Manager v4.0.5's reproducible-build model.
- Gradle archive tasks disable file timestamp preservation and use reproducible
  file ordering.
- The server-side `am.jar` and `main.jar` D8 input lists are sorted before jar
  creation, so filesystem enumeration order cannot change APK bytes.
- Every publish APK is passed through `scripts/verify-native-page-alignment.py`,
  which fails the release if any native `.so` has an ELF `PT_LOAD.p_align` below
  16 KB or if an uncompressed `.so` ZIP data offset is not 16 KB-aligned.
- Release assets include a `.sha256` sidecar generated from the verified APK.
- Release assets include `AppManagerNG-<version>.cdx.json`, a CycloneDX 1.6
  aggregate SBOM generated from the checked Gradle lockfiles by
  `scripts/generate-cyclonedx-sbom.py`.
- The tag workflow publishes GitHub artifact attestations for release APK
  provenance and for the CycloneDX SBOM predicate attached to those APK
  subjects.

The SBOM generator can be run without building APKs:

```powershell
python scripts/generate-cyclonedx-sbom.py --version 0.5.0 --output build/reproducible-release/publish/AppManagerNG-reproducible.cdx.json
python scripts/generate-cyclonedx-sbom.py --check build/reproducible-release/publish/AppManagerNG-reproducible.cdx.json
```

When Python `jsonschema` is available, validate the generated document against
the official CycloneDX 1.6 JSON schema:

```powershell
@'
import json
import pathlib
import urllib.request
import jsonschema
schema_url = "https://raw.githubusercontent.com/CycloneDX/specification/master/schema/bom-1.6.schema.json"
request = urllib.request.Request(schema_url, headers={"User-Agent": "AppManagerNG-release-validation"})
with urllib.request.urlopen(request, timeout=30) as response:
    schema = json.load(response)
bom = json.loads(pathlib.Path("build/reproducible-release/publish/AppManagerNG-reproducible.cdx.json").read_text(encoding="utf-8"))
jsonschema.Draft7Validator(schema).validate(bom)
print("CycloneDX JSON schema validation OK")
'@ | python -
```

For a published tag release, verify the downloadable artifacts with:

```powershell
$version = "0.5.0"
Get-FileHash -Algorithm SHA256 "AppManagerNG-$version-full-release.apk"
Get-Content "AppManagerNG-$version-full-release.apk.sha256"
python scripts/generate-cyclonedx-sbom.py --check "AppManagerNG-$version.cdx.json"
gh attestation verify "AppManagerNG-$version-full-release.apk" --repo SysAdminDoc/AppManagerNG
gh attestation verify "AppManagerNG-$version-full-release.apk" --repo SysAdminDoc/AppManagerNG --predicate-type https://cyclonedx.org/bom
```

The local `--check` path validates the generated CycloneDX shape, aggregate app
component, Maven package URLs, duplicate references, and aggregate dependency
coverage. The JSON-schema command verifies conformance to the official
CycloneDX 1.6 schema. `gh attestation verify` confirms that GitHub recorded the
APK digest as a subject from the repository workflow for both provenance and
SBOM predicate attestations.
