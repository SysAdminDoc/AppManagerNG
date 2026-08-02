#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Keep this stable entry point for release tooling; the implementation lives in Python so
# resource parsing and the checked-in coverage ratchet behave identically on every host.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec python3 "$REPO_ROOT/scripts/translation_quality.py"
