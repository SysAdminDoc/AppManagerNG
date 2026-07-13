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

# --- Distribution listing packets must describe the CURRENT release ---
DIST_DIR="$REPO_ROOT/docs/distribution"
for packet in fdroid-listing izzyondroid-listing accrescent-listing; do
  f="$DIST_DIR/$packet.md"
  if [[ ! -f "$f" ]]; then
    echo "SKIP: $packet.md not present"
    continue
  fi
  if ! grep -qF "$VERSION_NAME" "$f"; then
    echo "ERROR: $packet.md does not mention current versionName $VERSION_NAME" >&2
    FAIL=1
  fi
  STALE_TAGS=$(grep -oP 'releases/tag/v\K[0-9]+\.[0-9]+\.[0-9]+' "$f" | grep -vxF "$VERSION_NAME" || true)
  if [[ -n "$STALE_TAGS" ]]; then
    echo "ERROR: $packet.md references stale release tag(s): $(echo "$STALE_TAGS" | tr '\n' ' ')" >&2
    FAIL=1
  fi
  STALE_CODES=$(grep -oP '(?:[Vv]ersionCode|CurrentVersionCode):\s*\K[0-9]+' "$f" | grep -vxF "$VERSION_CODE" || true)
  if [[ -n "$STALE_CODES" ]]; then
    echo "ERROR: $packet.md references stale versionCode(s): $(echo "$STALE_CODES" | tr '\n' ' ')" >&2
    FAIL=1
  fi
  # This project builds and releases locally; it has no GitHub Actions workflows.
  if grep -qiP 'CI release workflow|\.github/workflows|GitHub Actions' "$f"; then
    echo "ERROR: $packet.md claims a CI/GitHub-Actions workflow, but this project has none" >&2
    FAIL=1
  fi
  if [[ -z "$STALE_TAGS" && -z "$STALE_CODES" ]] && grep -qF "$VERSION_NAME" "$f"; then
    echo "OK: $packet.md describes v$VERSION_NAME (code $VERSION_CODE)"
  fi
done

# --- Canonical release-facing docs must not link to absent files (e.g. the removed
# PROJECT_CONTEXT.md). ROADMAP.md / RESEARCH.md are excluded: they are working trackers that
# may legitimately name a missing file as a task rather than link to it. ---
for doc in "$REPO_ROOT/README.md" "$REPO_ROOT/CONTRIBUTING.md" \
           "$DIST_DIR/fdroid-listing.md" "$DIST_DIR/izzyondroid-listing.md" "$DIST_DIR/accrescent-listing.md"; do
  [[ -f "$doc" ]] || continue
  if grep -qF "PROJECT_CONTEXT.md" "$doc" && [[ ! -f "$REPO_ROOT/PROJECT_CONTEXT.md" ]]; then
    echo "ERROR: $(basename "$doc") references PROJECT_CONTEXT.md which does not exist" >&2
    FAIL=1
  fi
done

if (( FAIL )); then
  echo ""
  echo "FAILED: version surfaces are inconsistent - fix before release."
else
  echo ""
  echo "PASSED: all version and SDK surfaces are consistent."
fi

exit $FAIL
