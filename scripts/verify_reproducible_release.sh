#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

GRADLE_CMD="${GRADLE_CMD:-./gradlew}"
if [[ -n "${PYTHON_CMD:-}" ]] && command -v "$PYTHON_CMD" >/dev/null 2>&1 \
        && "$PYTHON_CMD" --version >/dev/null 2>&1; then
    PYTHON_BIN=("$PYTHON_CMD")
elif command -v python3 >/dev/null 2>&1 && python3 --version >/dev/null 2>&1; then
    PYTHON_BIN=(python3)
elif command -v python >/dev/null 2>&1 && python --version >/dev/null 2>&1; then
    PYTHON_BIN=(python)
elif command -v py >/dev/null 2>&1 && py -3 --version >/dev/null 2>&1; then
    PYTHON_BIN=(py -3)
else
    echo "ERROR: Python 3 is required for reproducible release verification." >&2
    exit 1
fi
# Deliberately outside build/: each build in this comparison runs `clean`, which empties the
# root project's build directory. Keeping the evidence in there means the second build destroys
# the first build's artifacts — and destroys the published artifacts and reports on the way out.
OUT_DIR="${REPRO_OUT_DIR:-reproducible-release}"
APK_ROOT="app/build/outputs/apk"
MAPPING_ROOT="app/build/outputs/mapping"
FIRST_DIR="$OUT_DIR/first"
SECOND_DIR="$OUT_DIR/second"
PUBLISH_DIR="$OUT_DIR/publish"
ASSET_LIST="$OUT_DIR/release-assets.txt"
SERVER_JAR_REPORT="$OUT_DIR/server-jars.txt"

rm -rf "$OUT_DIR"
mkdir -p "$FIRST_DIR" "$SECOND_DIR" "$PUBLISH_DIR"

set_build_time_source() {
    if [[ "${SOURCE_DATE_EPOCH:-}" =~ ^[0-9]+$ ]]; then
        echo "Build timestamp source: SOURCE_DATE_EPOCH=${SOURCE_DATE_EPOCH}"
        return
    fi

    local commit_seconds=""
    if command -v git >/dev/null 2>&1; then
        commit_seconds="$(git show --no-patch --format=%ct HEAD 2>/dev/null || true)"
    fi
    if [[ "$commit_seconds" =~ ^[0-9]+$ ]]; then
        export SOURCE_DATE_EPOCH="$commit_seconds"
        echo "Build timestamp source: Git HEAD commit time (${SOURCE_DATE_EPOCH})"
        return
    fi

    echo "Build timestamp source: none; release Gradle tasks will fail closed." >&2
}

set_build_time_source

copy_server_jars() {
    local source_root="$1"
    local destination_dir="$2"
    mkdir -p "$destination_dir"
    local name
    for name in am.jar main.jar; do
        if [[ ! -f "$source_root/app/src/main/assets/$name" ]]; then
            echo "ERROR: Missing generated server jar $source_root/app/src/main/assets/$name" >&2
            exit 1
        fi
        cp "$source_root/app/src/main/assets/$name" "$destination_dir/$name"
    done
}

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

# flossRelease -> floss-release, so a mapping pairs with the APK built from the same variant.
variant_to_apk_suffix() {
    printf '%s' "$1" | sed 's/\([a-z0-9]\)\([A-Z]\)/\1-\2/g' | tr '[:upper:]' '[:lower:]'
}

# The release build is minified, so a stack trace from a published APK is unreadable without the
# mapping R8 wrote beside it. Collect it with the APKs and fail closed when a variant has none:
# publishing an obfuscated build with no way to decode it makes every crash report unactionable.
copy_mappings() {
    local destination_dir="$1"
    local mapping_dir="$destination_dir/mapping"
    mkdir -p "$mapping_dir"
    local variant_dir
    local variant
    local found=0
    for variant_dir in "$MAPPING_ROOT"/*Release; do
        [[ -d "$variant_dir" ]] || continue
        variant="$(basename "$variant_dir")"
        if [[ ! -f "$variant_dir/mapping.txt" ]]; then
            echo "ERROR: Minified variant $variant produced no mapping.txt under $variant_dir" >&2
            exit 1
        fi
        cp "$variant_dir/mapping.txt" "$mapping_dir/$variant.txt"
        found=1
    done
    if (( found == 0 )); then
        echo "ERROR: No R8 mapping was produced under $MAPPING_ROOT" >&2
        exit 1
    fi
}

build_once() {
    local label="$1"
    local destination_dir="$2"

    echo "=== Clean build ${label} ==="
    # --no-build-cache: `clean` empties the project's build directory but not Gradle's build
    # cache, so without this the second build restores task outputs produced by the first one.
    # That makes the two builds dependent and the comparison meaningless — it would confirm the
    # cache is consistent, not that the build is reproducible.
    "$GRADLE_CMD" --no-daemon --no-build-cache --stacktrace clean :app:assembleRelease
    echo "=== Clean build ${label} complete ==="

    mapfile -t apks < <(find "$APK_ROOT" -path '*/release/*.apk' -type f | sort)
    if (( ${#apks[@]} == 0 )); then
        echo "ERROR: No release APKs were produced under $APK_ROOT" >&2
        exit 1
    fi

    declare -A seen=()
    local apk
    local name
    for apk in "${apks[@]}"; do
        name="$(basename "$apk")"
        if [[ -n "${seen[$name]:-}" ]]; then
            echo "ERROR: Duplicate release APK basename '$name' from ${seen[$name]} and $apk" >&2
            exit 1
        fi
        seen[$name]="$apk"
        cp "$apk" "$destination_dir/$name"
    done
    copy_mappings "$destination_dir"
    copy_server_jars "." "$destination_dir/server-jars"
    (
        cd "$destination_dir"
        list_apk_names "." | while IFS= read -r name; do
            sha256sum "$name"
        done
    ) | tee "$OUT_DIR/${label}.sha256"
}

verify_cross_environment_server_jars() {
    if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
        echo "ERROR: Cross-environment server-jar verification requires a clean Git checkout." >&2
        exit 1
    fi

    local worktree_parent
    local worktree_dir
    worktree_parent="$(mktemp -d "${TMPDIR:-/tmp}/appmanagerng-jar-repro.XXXXXX")"
    worktree_dir="$worktree_parent/source"
    local cross_dir="$OUT_DIR/server-jars/different-environment"
    mkdir -p "$cross_dir"

    (
        set -euo pipefail
        trap 'git worktree remove --force "$worktree_dir" >/dev/null 2>&1 || true; rm -rf "$worktree_parent"' EXIT
        git worktree add --detach "$worktree_dir" HEAD >/dev/null
        if [[ -f local.properties ]]; then
            cp local.properties "$worktree_dir/local.properties"
        fi

        echo "=== Cross-environment server-jar build ==="
        (
            cd "$worktree_dir"
            TZ="Pacific/Auckland" \
            LC_ALL="C" \
            LANG="C" \
            USER="jar-repro-builder" \
            LOGNAME="jar-repro-builder" \
            "$worktree_dir/gradlew" --no-daemon --no-build-cache --stacktrace clean \
                :server:compileReleaseJavaWithJavac :server:createReleaseServerJars \
                -Duser.timezone=Pacific/Auckland -Duser.language=en -Duser.country=NZ
        )
        copy_server_jars "$worktree_dir" "$cross_dir"
    )

    : > "$SERVER_JAR_REPORT"
    local name
    local first_hash
    local cross_hash
    for name in am.jar main.jar; do
        first_hash="$(sha256sum "$FIRST_DIR/server-jars/$name" | awk '{print $1}')"
        cross_hash="$(sha256sum "$cross_dir/$name" | awk '{print $1}')"
        printf '%s first=%s different-environment=%s\n' "$name" "$first_hash" "$cross_hash" | tee -a "$SERVER_JAR_REPORT"
        if [[ "$first_hash" != "$cross_hash" ]]; then
            echo "ERROR: Server jar $name is not reproducible across environments." >&2
            exit 1
        fi
    done
}

build_once "first" "$FIRST_DIR"
build_once "second" "$SECOND_DIR"
verify_cross_environment_server_jars

FIRST_APKS="$(list_apk_names "$FIRST_DIR")"
SECOND_APKS="$(list_apk_names "$SECOND_DIR")"

if [[ "$FIRST_APKS" != "$SECOND_APKS" ]]; then
    echo "ERROR: Release APK set changed across two clean builds." >&2
    diff -u <(printf '%s\n' "$FIRST_APKS") <(printf '%s\n' "$SECOND_APKS") > "$OUT_DIR/apk-list.diff" || true
    exit 1
fi

: > "$ASSET_LIST"
: > "$OUT_DIR/sha256.txt"
printf '%s\n' "$SERVER_JAR_REPORT" >> "$ASSET_LIST"

while IFS= read -r name; do
    [[ -n "$name" ]] || continue
    first_apk="$FIRST_DIR/$name"
    second_apk="$SECOND_DIR/$name"
    first_hash="$(sha256sum "$first_apk" | awk '{print $1}')"
    second_hash="$(sha256sum "$second_apk" | awk '{print $1}')"
    if [[ "$first_hash" != "$second_hash" ]]; then
        echo "ERROR: Release APK $name is not reproducible across two clean builds." >&2
        echo "ERROR: first=$first_hash second=$second_hash" >&2
        set +o pipefail
        cmp -l "$first_apk" "$second_apk" | head -20 > "$OUT_DIR/${name}.differing-bytes.txt"
        set -o pipefail
        exit 1
    fi

    publish_apk="$PUBLISH_DIR/$(publish_name_for "$name")"
    cp "$first_apk" "$publish_apk"
    "${PYTHON_BIN[@]}" scripts/verify-native-page-alignment.py "$publish_apk"
    printf '%s  %s\n' "$first_hash" "$(basename "$publish_apk")" | tee "$publish_apk.sha256" >> "$OUT_DIR/sha256.txt"
    printf '%s\n%s\n' "$publish_apk" "$publish_apk.sha256" >> "$ASSET_LIST"
    echo "Reproducible release APK verified: $name $first_hash"
done <<< "$FIRST_APKS"

# A mapping that differs between two clean builds means the DEX differs too, so the APK
# comparison above would be the only thing that looked stable.
for mapping in "$FIRST_DIR"/mapping/*.txt; do
    [[ -f "$mapping" ]] || continue
    variant="$(basename "$mapping" .txt)"
    second_mapping="$SECOND_DIR/mapping/$variant.txt"
    if [[ ! -f "$second_mapping" ]]; then
        echo "ERROR: Variant $variant produced a mapping in the first build but not the second." >&2
        exit 1
    fi
    first_hash="$(sha256sum "$mapping" | awk '{print $1}')"
    second_hash="$(sha256sum "$second_mapping" | awk '{print $1}')"
    if [[ "$first_hash" != "$second_hash" ]]; then
        echo "ERROR: R8 mapping for $variant is not reproducible across two clean builds." >&2
        echo "ERROR: first=$first_hash second=$second_hash" >&2
        exit 1
    fi
    publish_mapping="$PUBLISH_DIR/AppManagerNG-reproducible-$(variant_to_apk_suffix "$variant")-mapping.txt"
    cp "$mapping" "$publish_mapping"
    printf '%s  %s\n' "$first_hash" "$(basename "$publish_mapping")" \
        | tee "$publish_mapping.sha256" >> "$OUT_DIR/sha256.txt"
    printf '%s\n%s\n' "$publish_mapping" "$publish_mapping.sha256" >> "$ASSET_LIST"
    echo "Reproducible R8 mapping verified: $variant $first_hash"
done

sbom_path="$PUBLISH_DIR/AppManagerNG-reproducible.cdx.json"
"${PYTHON_BIN[@]}" scripts/generate-cyclonedx-sbom.py --output "$sbom_path"
"${PYTHON_BIN[@]}" scripts/generate-cyclonedx-sbom.py --check "$sbom_path"
printf '%s\n' "$sbom_path" >> "$ASSET_LIST"

"${PYTHON_BIN[@]}" scripts/run_dependency_cve_gate.py \
    --gradle-cmd "$GRADLE_CMD" \
    --out-dir "$PUBLISH_DIR"
printf '%s\n' \
    "$PUBLISH_DIR/dependency-check-report.html" \
    "$PUBLISH_DIR/dependency-check-report.sarif" \
    "$PUBLISH_DIR/dependency-cve-receipt.json" >> "$ASSET_LIST"
