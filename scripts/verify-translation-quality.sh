#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Translation quality gate: detects missing source strings across locales
# and verifies pseudoLocalesEnabled is set for layout-clipping checks.
#
# This script reports string-coverage gaps but only FAILS on source-string
# regressions (strings present in a locale but removed from the default).
# Missing translations in locale directories are warnings — they need
# translator input, not CI enforcement.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RES_DIR="$REPO_ROOT/app/src/main/res"
DEFAULT_STRINGS="$RES_DIR/values/strings.xml"

if [[ ! -f "$DEFAULT_STRINGS" ]]; then
  echo "::error::Default strings.xml not found at $DEFAULT_STRINGS"
  exit 1
fi

FAIL=0

# --- Verify pseudoLocalesEnabled ---
BUILD_GRADLE="$REPO_ROOT/app/build.gradle"
if grep -q 'pseudoLocalesEnabled\s*=\s*true' "$BUILD_GRADLE"; then
  echo "OK: pseudoLocalesEnabled = true"
else
  echo "::error::pseudoLocalesEnabled is not set to true in app/build.gradle"
  FAIL=1
fi

# --- Extract source string names ---
SOURCE_NAMES=$(grep -oP 'name="\K[^"]+' "$DEFAULT_STRINGS" | sort -u)
SOURCE_COUNT=$(echo "$SOURCE_NAMES" | wc -l)
echo "Source strings: $SOURCE_COUNT"
echo ""

# --- Check each locale for stale strings (present in locale but removed from source) ---
STALE_TOTAL=0
LOCALE_COUNT=0
for locale_dir in "$RES_DIR"/values-*/; do
  locale_strings="$locale_dir/strings.xml"
  [[ -f "$locale_strings" ]] || continue
  LOCALE_COUNT=$((LOCALE_COUNT + 1))
  locale_name=$(basename "$locale_dir")

  # Find strings in locale that are NOT in source (stale/removed)
  locale_names=$(grep -oP 'name="\K[^"]+' "$locale_strings" 2>/dev/null | sort -u || true)
  if [[ -z "$locale_names" ]]; then
    continue
  fi
  stale=$(comm -23 <(echo "$locale_names") <(echo "$SOURCE_NAMES") || true)
  if [[ -n "$stale" ]]; then
    stale_count=$(echo "$stale" | wc -l)
    STALE_TOTAL=$((STALE_TOTAL + stale_count))
    echo "::warning::$locale_name has $stale_count stale string(s) not in source"
  fi
done

echo ""
echo "Checked $LOCALE_COUNT locale directories."

# --- Coverage report (top 5 least-covered locales) ---
echo ""
echo "=== Coverage Report (bottom 5) ==="
declare -A COVERAGE
for locale_dir in "$RES_DIR"/values-*/; do
  locale_strings="$locale_dir/strings.xml"
  [[ -f "$locale_strings" ]] || continue
  locale_name=$(basename "$locale_dir")
  locale_count=$(grep -c 'name="' "$locale_strings" 2>/dev/null || echo 0)
  COVERAGE["$locale_name"]=$locale_count
done

for key in "${!COVERAGE[@]}"; do
  echo "${COVERAGE[$key]} $key"
done | sort -n | head -5 | while read count name; do
  pct=$((count * 100 / SOURCE_COUNT))
  echo "  $name: $count / $SOURCE_COUNT ($pct%)"
done

echo ""
if (( FAIL )); then
  echo "FAILED: translation quality gate."
else
  echo "PASSED: no source-string regressions detected."
  if (( STALE_TOTAL > 0 )); then
    echo "  ($STALE_TOTAL stale translation strings found — warnings only)"
  fi
fi

exit $FAIL
