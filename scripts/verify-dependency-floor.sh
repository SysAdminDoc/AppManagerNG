#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Dependency floor drift gate: keep this shell entry point for release tooling,
# while the Python checker owns the machine-readable policy and AAR inspection.
#
# Exit 0 = no drift, exit 1 = drift detected.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if command -v python3 >/dev/null 2>&1 && python3 --version >/dev/null 2>&1; then
  PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1 && python --version >/dev/null 2>&1; then
  PYTHON_BIN=python
elif command -v py >/dev/null 2>&1 && py -3 --version >/dev/null 2>&1; then
  PYTHON_BIN="py -3"
else
  echo "ERROR: Python 3 is required for the dependency floor gate" >&2
  exit 1
fi

if [[ "$PYTHON_BIN" == "py -3" ]]; then
  exec py -3 "$SCRIPT_DIR/verify_dependency_floor.py" "$@"
fi
exec "$PYTHON_BIN" "$SCRIPT_DIR/verify_dependency_floor.py" "$@"
