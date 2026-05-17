#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

GRADLE_CMD="${GRADLE_CMD:-./gradlew}"
OUT_DIR="${REPRO_OUT_DIR:-build/reproducible-release}"
APK_ROOT="app/build/outputs/apk"
FIRST_DIR="$OUT_DIR/first"
SECOND_DIR="$OUT_DIR/second"
PUBLISH_DIR="$OUT_DIR/publish"
ASSET_LIST="$OUT_DIR/release-assets.txt"

rm -rf "$OUT_DIR"
mkdir -p "$FIRST_DIR" "$SECOND_DIR" "$PUBLISH_DIR"

list_apk_names() {
    local dir="$1"
    find "$dir" -maxdepth 1 -type f -name '*.apk' -exec basename {} \; | sort
}

publish_name_for() {
    local base="$1"
    local variant="${base#app-}"
    variant="${variant%.apk}"
    printf 'AppManagerNG-reproducible-%s.apk' "$variant"
}

build_once() {
    local label="$1"
    local destination_dir="$2"

    echo "::group::Clean build ${label}"
    "$GRADLE_CMD" --no-daemon --stacktrace clean :app:assembleRelease
    echo "::endgroup::"

    mapfile -t apks < <(find "$APK_ROOT" -path '*/release/*.apk' -type f | sort)
    if (( ${#apks[@]} == 0 )); then
        echo "::error::No release APKs were produced under $APK_ROOT" >&2
        exit 1
    fi

    declare -A seen=()
    local apk
    local name
    for apk in "${apks[@]}"; do
        name="$(basename "$apk")"
        if [[ -n "${seen[$name]:-}" ]]; then
            echo "::error::Duplicate release APK basename '$name' from ${seen[$name]} and $apk" >&2
            exit 1
        fi
        seen[$name]="$apk"
        cp "$apk" "$destination_dir/$name"
    done
    (
        cd "$destination_dir"
        list_apk_names "." | while IFS= read -r name; do
            sha256sum "$name"
        done
    ) | tee "$OUT_DIR/${label}.sha256"
}

build_once "first" "$FIRST_DIR"
build_once "second" "$SECOND_DIR"

FIRST_APKS="$(list_apk_names "$FIRST_DIR")"
SECOND_APKS="$(list_apk_names "$SECOND_DIR")"

if [[ "$FIRST_APKS" != "$SECOND_APKS" ]]; then
    echo "::error::Release APK set changed across two clean builds." >&2
    diff -u <(printf '%s\n' "$FIRST_APKS") <(printf '%s\n' "$SECOND_APKS") > "$OUT_DIR/apk-list.diff" || true
    exit 1
fi

: > "$ASSET_LIST"
: > "$OUT_DIR/sha256.txt"

while IFS= read -r name; do
    [[ -n "$name" ]] || continue
    first_apk="$FIRST_DIR/$name"
    second_apk="$SECOND_DIR/$name"
    first_hash="$(sha256sum "$first_apk" | awk '{print $1}')"
    second_hash="$(sha256sum "$second_apk" | awk '{print $1}')"
    if [[ "$first_hash" != "$second_hash" ]]; then
        echo "::error::Release APK $name is not reproducible across two clean builds." >&2
        echo "::error::first=$first_hash second=$second_hash" >&2
        set +o pipefail
        cmp -l "$first_apk" "$second_apk" | head -20 > "$OUT_DIR/${name}.differing-bytes.txt"
        set -o pipefail
        exit 1
    fi

    publish_apk="$PUBLISH_DIR/$(publish_name_for "$name")"
    cp "$first_apk" "$publish_apk"
    printf '%s  %s\n' "$first_hash" "$(basename "$publish_apk")" | tee "$publish_apk.sha256" >> "$OUT_DIR/sha256.txt"
    printf '%s\n%s\n' "$publish_apk" "$publish_apk.sha256" >> "$ASSET_LIST"
    echo "Reproducible release APK verified: $name $first_hash"
done <<< "$FIRST_APKS"
