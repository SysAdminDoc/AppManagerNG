#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Keep this stable entry point for release tooling; the implementation lives in Python so
# resource parsing and the checked-in coverage ratchet behave identically on every host.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if command -v python3 >/dev/null 2>&1 && python3 --version >/dev/null 2>&1; then
  exec python3 "$REPO_ROOT/scripts/translation_quality.py"
elif command -v python >/dev/null 2>&1 && python --version >/dev/null 2>&1; then
  exec python "$REPO_ROOT/scripts/translation_quality.py"
elif command -v py >/dev/null 2>&1 && py -3 --version >/dev/null 2>&1; then
  exec py -3 "$REPO_ROOT/scripts/translation_quality.py"
fi

echo "ERROR: Python 3 is required for the translation quality gate" >&2
exit 1
