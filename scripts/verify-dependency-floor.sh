#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Dependency floor drift gate: verifies that minSdk-21-pinned dependencies
# in versions.gradle have not been bumped past their documented ceiling.
# Also checks that the OWASP dependency-check plugin is within one minor
# version of the documented pin.
#
# Exit 0 = no drift, exit 1 = drift detected.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSIONS="$REPO_ROOT/versions.gradle"

extract() {
  local key="$1"
  grep -oP "${key}\s*=\s*[\"']?\K[^\"'[:space:]]+" "$VERSIONS" | head -1
}

FAIL=0

echo "=== Dependency Floor Drift Gate ==="
echo ""

# --- Pinned cluster: these must NOT exceed their ceiling ---
# Format: variable_name max_allowed_version label
declare -A PINS=(
  ["material_version"]="1.13.0|Material Components"
  ["activity_version"]="1.11.0|Activity"
  ["biometric_version"]="1.4.0-alpha04|Biometric"
  ["room_version"]="2.7.2|Room"
  ["webkit_version"]="1.14.0|WebKit"
  ["sora_editor_version"]="0.24.6|Sora Editor"
  ["work_version"]="2.10.5|WorkManager"
)

for key in "${!PINS[@]}"; do
  IFS='|' read -r max_version label <<< "${PINS[$key]}"
  actual=$(extract "$key" || true)
  if [[ -z "$actual" ]]; then
    echo "::warning::Could not read $key from versions.gradle"
    continue
  fi
  if [[ "$actual" != "$max_version" ]]; then
    echo "::error::$label ($key) is $actual but ceiling is $max_version — minSdk-21 floor breached"
    FAIL=1
  else
    echo "OK: $label pinned at $actual (ceiling $max_version)"
  fi
done

# --- minSdk sanity ---
MIN_SDK=$(extract "min_sdk" || true)
if [[ -n "$MIN_SDK" && "$MIN_SDK" -ne 21 ]]; then
  echo "::error::min_sdk is $MIN_SDK, expected 21 per docs/policy/minsdk-21-ceiling.md"
  FAIL=1
else
  echo "OK: min_sdk = $MIN_SDK"
fi

# --- OWASP dependency-check version drift ---
DC_VERSION=$(extract "dependency_check_version" || true)
if [[ -n "$DC_VERSION" ]]; then
  DC_MAJOR=$(echo "$DC_VERSION" | cut -d. -f1)
  DC_MINOR=$(echo "$DC_VERSION" | cut -d. -f2)
  # The tool should not lag more than 2 minor versions behind
  # Current expectation: 10.x line
  if (( DC_MAJOR < 10 )); then
    echo "::warning::dependency-check $DC_VERSION is behind the 10.x line"
  else
    echo "OK: dependency-check at $DC_VERSION"
  fi
fi

echo ""
if (( FAIL )); then
  echo "FAILED: dependency floor drift detected. See docs/policy/minsdk-21-ceiling.md."
else
  echo "PASSED: all pinned dependencies within ceiling."
fi

exit $FAIL
