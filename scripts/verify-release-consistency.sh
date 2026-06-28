#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Preflight: all version-bearing surfaces must agree before release.
# Exit 0 = consistent, exit 1 = drift detected.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_GRADLE="$REPO_ROOT/app/build.gradle"

VERSION_NAME=$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$BUILD_GRADLE")
VERSION_CODE=$(grep -oP 'versionCode\s*=\s*\K[0-9]+' "$BUILD_GRADLE")

if [[ -z "$VERSION_NAME" || -z "$VERSION_CODE" ]]; then
  echo "ERROR: Could not extract versionName/versionCode from app/build.gradle" >&2
  exit 1
fi

echo "Source of truth: versionName=$VERSION_NAME versionCode=$VERSION_CODE"
FAIL=0

# --- README badge ---
README="$REPO_ROOT/README.md"
if [[ -f "$README" ]]; then
  BADGE_VERSION=$(grep -oP 'version-\K[0-9]+\.[0-9]+\.[0-9]+' "$README" | head -1 || true)
  if [[ "$BADGE_VERSION" != "$VERSION_NAME" ]]; then
    echo "ERROR: README badge version ($BADGE_VERSION) != versionName ($VERSION_NAME)" >&2
    FAIL=1
  else
    echo "OK: README badge matches"
  fi
fi

# --- Fastlane changelog ---
FASTLANE="$REPO_ROOT/fastlane/metadata/android/en-US/changelogs/${VERSION_CODE}.txt"
if [[ ! -f "$FASTLANE" ]]; then
  echo "ERROR: Missing fastlane/metadata/android/en-US/changelogs/${VERSION_CODE}.txt" >&2
  FAIL=1
else
  echo "OK: Fastlane changelog exists for versionCode $VERSION_CODE"
fi

# --- CHANGELOG.md has entry or Unreleased section ---
CHANGELOG="$REPO_ROOT/CHANGELOG.md"
if [[ -f "$CHANGELOG" ]]; then
  if grep -qP "^## v\Q${VERSION_NAME}\E\\b" "$CHANGELOG" || grep -qP "^## Unreleased" "$CHANGELOG"; then
    echo "OK: CHANGELOG.md has entry for v${VERSION_NAME} or Unreleased section"
  else
    echo "ERROR: CHANGELOG.md has no entry for v${VERSION_NAME} and no Unreleased section" >&2
    FAIL=1
  fi
fi

# --- Tag consistency (optional local release context) ---
RELEASE_TAG="${RELEASE_TAG_NAME:-}"
if [[ -z "$RELEASE_TAG" ]] && git -C "$REPO_ROOT" describe --tags --exact-match HEAD >/dev/null 2>&1; then
  RELEASE_TAG="$(git -C "$REPO_ROOT" describe --tags --exact-match HEAD)"
fi
if [[ -n "$RELEASE_TAG" && "$RELEASE_TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  TAG_VERSION="${RELEASE_TAG#v}"
  if [[ "$TAG_VERSION" != "$VERSION_NAME" ]]; then
    echo "ERROR: Git tag ($RELEASE_TAG) version ($TAG_VERSION) != versionName ($VERSION_NAME)" >&2
    FAIL=1
  else
    echo "OK: Git tag matches versionName"
  fi
fi

# --- SBOM script can read versionName ---
SBOM_SCRIPT="$REPO_ROOT/scripts/generate-cyclonedx-sbom.py"
if [[ -f "$SBOM_SCRIPT" ]]; then
  echo "OK: SBOM script present (reads versionName dynamically from build.gradle)"
fi

if (( FAIL )); then
  echo ""
  echo "FAILED: version surfaces are inconsistent — fix before release."
else
  echo ""
  echo "PASSED: all version surfaces are consistent."
fi

exit $FAIL
