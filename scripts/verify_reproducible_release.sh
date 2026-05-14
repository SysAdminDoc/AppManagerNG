#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

GRADLE_CMD="${GRADLE_CMD:-./gradlew}"
OUT_DIR="${REPRO_OUT_DIR:-build/reproducible-release}"
APK_PATH="app/build/outputs/apk/release/app-release.apk"
FIRST_APK="$OUT_DIR/app-release-first.apk"
SECOND_APK="$OUT_DIR/app-release-second.apk"
PUBLISH_APK="$OUT_DIR/AppManagerNG-reproducible-release.apk"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

build_once() {
    local label="$1"
    local destination="$2"

    echo "::group::Clean build ${label}"
    "$GRADLE_CMD" --no-daemon --stacktrace clean :app:assembleRelease
    echo "::endgroup::"

    if [[ ! -f "$APK_PATH" ]]; then
        echo "::error::Expected release APK was not produced at $APK_PATH" >&2
        exit 1
    fi

    cp "$APK_PATH" "$destination"
    sha256sum "$destination" | tee "$OUT_DIR/${label}.sha256"
}

build_once "first" "$FIRST_APK"
build_once "second" "$SECOND_APK"

FIRST_HASH="$(sha256sum "$FIRST_APK" | awk '{print $1}')"
SECOND_HASH="$(sha256sum "$SECOND_APK" | awk '{print $1}')"

if [[ "$FIRST_HASH" != "$SECOND_HASH" ]]; then
    echo "::error::Release APK is not reproducible across two clean builds." >&2
    echo "::error::first=$FIRST_HASH second=$SECOND_HASH" >&2
    set +o pipefail
    cmp -l "$FIRST_APK" "$SECOND_APK" | head -20 > "$OUT_DIR/differing-bytes.txt"
    set -o pipefail
    exit 1
fi

cp "$FIRST_APK" "$PUBLISH_APK"
printf '%s  %s\n' "$FIRST_HASH" "$(basename "$PUBLISH_APK")" > "$OUT_DIR/sha256.txt"
echo "Reproducible release APK verified: $FIRST_HASH"
