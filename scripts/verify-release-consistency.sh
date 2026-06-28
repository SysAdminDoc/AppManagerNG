#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Preflight: all version-bearing surfaces must agree before release.
# Exit 0 = consistent, exit 1 = drift detected.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_GRADLE="$REPO_ROOT/app/build.gradle"
VERSIONS_GRADLE="$REPO_ROOT/versions.gradle"
WRAPPER_PROPERTIES="$REPO_ROOT/gradle/wrapper/gradle-wrapper.properties"

VERSION_NAME=$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$BUILD_GRADLE")
VERSION_CODE=$(grep -oP 'versionCode\s*=\s*\K[0-9]+' "$BUILD_GRADLE")
COMPILE_SDK=$(grep -oP '^\s*compile_sdk\s*=\s*\K[0-9]+' "$VERSIONS_GRADLE" | head -1 || true)
MIN_SDK=$(grep -oP '^\s*min_sdk\s*=\s*\K[0-9]+' "$VERSIONS_GRADLE" | head -1 || true)
TARGET_SDK=$(grep -oP '^\s*target_sdk\s*=\s*\K[0-9]+' "$VERSIONS_GRADLE" | head -1 || true)
AGP_VERSION=$(grep -oP "^\s*agp_version\s*=\s*['\"]\K[^'\"]+" "$VERSIONS_GRADLE" | head -1 || true)
NDK_VERSION=$(grep -oP "^\s*ndk_version\s*=\s*['\"]\K[^'\"]+" "$VERSIONS_GRADLE" | head -1 || true)
GRADLE_VERSION=$(grep -oP 'gradle-\K[0-9]+(\.[0-9]+)+(?=-bin\.zip)' "$WRAPPER_PROPERTIES" | head -1 || true)

if [[ -z "$VERSION_NAME" || -z "$VERSION_CODE" || -z "$COMPILE_SDK" ||
      -z "$MIN_SDK" || -z "$TARGET_SDK" || -z "$AGP_VERSION" ||
      -z "$NDK_VERSION" || -z "$GRADLE_VERSION" ]]; then
  echo "ERROR: Could not extract version, SDK, Gradle, AGP, and NDK pins" >&2
  exit 1
fi

echo "Source of truth: versionName=$VERSION_NAME versionCode=$VERSION_CODE compileSdk=$COMPILE_SDK minSdk=$MIN_SDK targetSdk=$TARGET_SDK Gradle=$GRADLE_VERSION AGP=$AGP_VERSION NDK=$NDK_VERSION"
FAIL=0

check_contains() {
  local file="$1"
  local label="$2"
  local needle="$3"
  local description="$4"

  if grep -qF "$needle" "$file"; then
    echo "OK: $label has $description"
  else
    echo "ERROR: $label missing $description ($needle)" >&2
    FAIL=1
  fi
}

check_regex() {
  local file="$1"
  local label="$2"
  local pattern="$3"
  local description="$4"

  if grep -qP "$pattern" "$file"; then
    echo "OK: $label has $description"
  else
    echo "ERROR: $label missing $description" >&2
    FAIL=1
  fi
}

# --- README badge ---
README="$REPO_ROOT/README.md"
if [[ -f "$README" ]]; then
  BADGE_VERSION=$(grep -oP 'version-\K[0-9]+\.[0-9]+\.[0-9]+' "$README" | head -1 || true)
  BADGE_MIN_SDK=$(grep -oP 'minSdk-\K[0-9]+' "$README" | head -1 || true)
  BADGE_TARGET_SDK=$(grep -oP 'targetSdk-\K[0-9]+' "$README" | head -1 || true)
  if [[ "$BADGE_VERSION" != "$VERSION_NAME" ]]; then
    echo "ERROR: README badge version ($BADGE_VERSION) != versionName ($VERSION_NAME)" >&2
    FAIL=1
  else
    echo "OK: README badge matches"
  fi
  if [[ "$BADGE_MIN_SDK" != "$MIN_SDK" ]]; then
    echo "ERROR: README badge minSdk ($BADGE_MIN_SDK) != min_sdk ($MIN_SDK)" >&2
    FAIL=1
  else
    echo "OK: README minSdk badge matches"
  fi
  if [[ "$BADGE_TARGET_SDK" != "$TARGET_SDK" ]]; then
    echo "ERROR: README badge targetSdk ($BADGE_TARGET_SDK) != target_sdk ($TARGET_SDK)" >&2
    FAIL=1
  else
    echo "OK: README targetSdk badge matches"
  fi
fi

# --- app/build.gradle uses canonical SDK pins from versions.gradle ---
check_regex "$BUILD_GRADLE" "app/build.gradle" '^\s*compileSdk\s*=\s*compile_sdk\b' "compile_sdk pin"
check_regex "$BUILD_GRADLE" "app/build.gradle" '^\s*minSdk\s*=\s*min_sdk\b' "min_sdk pin"
check_regex "$BUILD_GRADLE" "app/build.gradle" '^\s*targetSdk\s*=\s*target_sdk\b' "target_sdk pin"

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

# --- Local working notes must not drift when present ---
CLAUDE_NOTES="$REPO_ROOT/CLAUDE.md"
if [[ -f "$CLAUDE_NOTES" ]]; then
  check_contains "$CLAUDE_NOTES" "CLAUDE.md" "versionName $VERSION_NAME" "versionName $VERSION_NAME"
  check_contains "$CLAUDE_NOTES" "CLAUDE.md" "versionCode $VERSION_CODE" "versionCode $VERSION_CODE"
  check_contains "$CLAUDE_NOTES" "CLAUDE.md" "Gradle $GRADLE_VERSION" "Gradle $GRADLE_VERSION"
  check_contains "$CLAUDE_NOTES" "CLAUDE.md" "AGP $AGP_VERSION" "AGP $AGP_VERSION"
  check_contains "$CLAUDE_NOTES" "CLAUDE.md" "NDK $NDK_VERSION" "NDK $NDK_VERSION"
  check_contains "$CLAUDE_NOTES" "CLAUDE.md" "Min/Target/Compile SDK**: $MIN_SDK / $TARGET_SDK / $COMPILE_SDK" "SDK tuple $MIN_SDK/$TARGET_SDK/$COMPILE_SDK"
else
  echo "SKIP: CLAUDE.md local working notes not present"
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
  echo "FAILED: version surfaces are inconsistent - fix before release."
else
  echo ""
  echo "PASSED: all version and SDK surfaces are consistent."
fi

exit $FAIL
