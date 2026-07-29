#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Sign the remote debloat-definition manifest.

The app only accepts a manifest whose signed document verifies against a key pinned in
``DebloatDefinitionManifest.PINNED_KEYS``. This script builds that signed document from the
current dataset files and wraps it in the envelope the app expects:

    {
      "schema": 2,
      "signed": "<base64 of the signed document>",
      "signatures": [{"keyId": "...", "alg": "SHA256withECDSA", "sig": "<base64 DER>"}]
    }

Because the document is carried verbatim as base64, verification never depends on JSON
canonicalisation.

The signing key is maintainer-local and is never committed. Generate one with::

    openssl ecparam -name prime256v1 -genkey -noout -out .keys/debloat-definitions-<id>.pem

and pin its public half with ``--print-public-key``.

Usage::

    python scripts/sign-debloat-definitions.py \
        --key .keys/debloat-definitions-2026-07.pem \
        --key-id ng-debloat-2026-07 \
        --version 2026-07-29 \
        --generation 2

Every publish must use a generation number strictly greater than the previous one; the app
refuses to roll back to a lower generation.
"""

from __future__ import annotations

import argparse
import base64
import datetime as dt
import hashlib
import json
import pathlib
import subprocess
import sys
import tempfile

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
MANIFEST_PATH = REPO_ROOT / "docs" / "debloat-definitions" / "manifest.json"
RAW_URL_PREFIX = "https://raw.githubusercontent.com/SysAdminDoc/AppManagerNG/main/"
SCHEMA_VERSION = 2
SIGNATURE_ALGORITHM = "SHA256withECDSA"
DATASET_FILES = {
    "debloat": "app/src/main/assets/debloat.json",
    "suggestions": "app/src/main/assets/suggestions.json",
}


def openssl(*args: str, stdin: bytes | None = None) -> bytes:
    result = subprocess.run(["openssl", *args], input=stdin, capture_output=True)
    if result.returncode != 0:
        sys.exit(f"openssl {' '.join(args)} failed:\n{result.stderr.decode(errors='replace')}")
    return result.stdout


def describe(relative_path: str) -> dict:
    path = REPO_ROOT / relative_path
    data = path.read_bytes()
    return {
        "url": RAW_URL_PREFIX + relative_path,
        "sha256": hashlib.sha256(data).hexdigest(),
        "bytes": len(data),
    }


def build_document(version: str, generation: int, valid_days: int) -> bytes:
    expires = dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=valid_days)
    document = {
        "schema": SCHEMA_VERSION,
        "generation": generation,
        "version": version,
        "expires": expires.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "files": {name: describe(path) for name, path in DATASET_FILES.items()},
    }
    return json.dumps(document, indent=2, sort_keys=True).encode("utf-8")


def sign(document: bytes, key_path: pathlib.Path) -> bytes:
    with tempfile.NamedTemporaryFile(delete=False) as handle:
        handle.write(document)
        payload_path = handle.name
    try:
        return openssl("dgst", "-sha256", "-sign", str(key_path), payload_path)
    finally:
        pathlib.Path(payload_path).unlink(missing_ok=True)


def public_key_base64(key_path: pathlib.Path) -> str:
    return base64.b64encode(openssl("ec", "-in", str(key_path), "-pubout", "-outform", "DER")).decode()


def previous_generation() -> int:
    if not MANIFEST_PATH.is_file():
        return 0
    try:
        envelope = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
        document = json.loads(base64.b64decode(envelope["signed"]))
        return int(document["generation"])
    except (ValueError, KeyError, TypeError):
        return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--key", type=pathlib.Path, help="PEM-encoded EC P-256 private key")
    parser.add_argument("--key-id", help="Identifier pinned in DebloatDefinitionManifest")
    parser.add_argument("--version", help="Human-readable dataset version, e.g. 2026-07-29")
    parser.add_argument("--generation", type=int,
                        help="Monotonic generation number (defaults to previous + 1)")
    parser.add_argument("--valid-days", type=int, default=90, help="Manifest lifetime in days")
    parser.add_argument("--print-public-key", action="store_true",
                        help="Print the base64 X.509 public key to pin, then exit")
    args = parser.parse_args()

    if args.print_public_key:
        if not args.key:
            parser.error("--print-public-key requires --key")
        print(public_key_base64(args.key))
        return 0

    for required in ("key", "key_id", "version"):
        if not getattr(args, required):
            parser.error(f"--{required.replace('_', '-')} is required")

    generation = args.generation if args.generation is not None else previous_generation() + 1
    if generation <= previous_generation():
        sys.exit(f"Generation {generation} does not advance past {previous_generation()}.")

    document = build_document(args.version, generation, args.valid_days)
    envelope = {
        "schema": SCHEMA_VERSION,
        "signed": base64.b64encode(document).decode(),
        "signatures": [{
            "keyId": args.key_id,
            "alg": SIGNATURE_ALGORITHM,
            "sig": base64.b64encode(sign(document, args.key)).decode(),
        }],
    }
    MANIFEST_PATH.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST_PATH.write_text(json.dumps(envelope, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"Wrote {MANIFEST_PATH.relative_to(REPO_ROOT)} (generation {generation}, key {args.key_id}).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
