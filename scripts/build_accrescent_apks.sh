#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

VARIANT="${1:-flossRelease}"
VARIANT_CAP="$(tr '[:lower:]' '[:upper:]' <<<"${VARIANT:0:1}")${VARIANT:1}"
FLAVOR="${VARIANT%Release}"
FLAVOR_LC="$(tr '[:upper:]' '[:lower:]' <<<"${FLAVOR}")"

BUNDLETOOL="${BUNDLETOOL:-bundletool}"
if ! command -v "${BUNDLETOOL}" >/dev/null 2>&1; then
  echo "bundletool must be on PATH, or set BUNDLETOOL=/path/to/bundletool." >&2
  exit 1
fi

BUNDLETOOL_VERSION="$("${BUNDLETOOL}" version 2>/dev/null | awk '{print $NF}' | head -n1)"
if [[ -z "${BUNDLETOOL_VERSION}" ]]; then
  echo "Could not read bundletool version. Accrescent requires 1.11.4 or newer." >&2
  exit 1
fi
if [[ "$(printf '%s\n%s\n' "1.11.4" "${BUNDLETOOL_VERSION}" | sort -V | head -n1)" != "1.11.4" ]]; then
  echo "bundletool ${BUNDLETOOL_VERSION} is too old. Accrescent requires 1.11.4 or newer." >&2
  exit 1
fi

KEYSTORE="${KEYSTORE:-}"
KEYSTORE_PASS="${KEYSTORE_PASS:-}"
KEY_ALIAS="${KEY_ALIAS:-}"
KEY_ALIAS_PASS="${KEY_ALIAS_PASS:-}"

if [[ -z "${KEYSTORE}" && -f app/keystore.properties ]]; then
  KEYSTORE_PROP="$(sed -n 's/^storeFile=//p' app/keystore.properties | tail -n1)"
  KEYSTORE_PASS="$(sed -n 's/^storePassword=//p' app/keystore.properties | tail -n1)"
  KEY_ALIAS="$(sed -n 's/^keyAlias=//p' app/keystore.properties | tail -n1)"
  KEY_ALIAS_PASS="$(sed -n 's/^keyPassword=//p' app/keystore.properties | tail -n1)"
  if [[ -n "${KEYSTORE_PROP}" ]]; then
    if [[ "${KEYSTORE_PROP}" = /* || "${KEYSTORE_PROP}" =~ ^[A-Za-z]: ]]; then
      KEYSTORE="${KEYSTORE_PROP}"
    else
      KEYSTORE="app/${KEYSTORE_PROP}"
    fi
  fi
fi

if [[ -z "${KEYSTORE}" || -z "${KEYSTORE_PASS}" || -z "${KEY_ALIAS}" || -z "${KEY_ALIAS_PASS}" ]]; then
  cat >&2 <<'EOF'
Accrescent APK sets must be signed with the release key.
Set KEYSTORE, KEYSTORE_PASS, KEY_ALIAS, and KEY_ALIAS_PASS, or provide app/keystore.properties.
EOF
  exit 1
fi

if [[ ! -f "${KEYSTORE}" ]]; then
  echo "Keystore not found: ${KEYSTORE}" >&2
  exit 1
fi

VERSION="$(grep -m1 'versionName' app/build.gradle | awk -F '"' '{print $2}')"
OUTPUT_DIR="app/build/outputs/accrescent"
mkdir -p "${OUTPUT_DIR}"

"${ROOT_DIR}/gradlew" ":app:bundle${VARIANT_CAP}" --no-build-cache --no-configuration-cache --no-daemon

AAB_DIR="app/build/outputs/bundle/${VARIANT}"
mapfile -t AABS < <(find "${AAB_DIR}" -maxdepth 1 -name '*.aab' -type f | sort)
if [[ "${#AABS[@]}" -ne 1 ]]; then
  echo "Expected exactly one AAB in ${AAB_DIR}; found ${#AABS[@]}." >&2
  exit 1
fi
AAB_PATH="${AABS[0]}"
APKS_PATH="${OUTPUT_DIR}/AppManagerNG-${VERSION}-${FLAVOR_LC}-accrescent.apks"

"${BUNDLETOOL}" build-apks \
  --overwrite \
  --mode=default \
  --bundle="${AAB_PATH}" \
  --output="${APKS_PATH}" \
  --ks="${KEYSTORE}" \
  --ks-pass=pass:"${KEYSTORE_PASS}" \
  --ks-key-alias="${KEY_ALIAS}" \
  --key-pass=pass:"${KEY_ALIAS_PASS}"

SIZE_BYTES="$(wc -c < "${APKS_PATH}" | tr -d ' ')"
MAX_BYTES=$((128 * 1024 * 1024))
if (( SIZE_BYTES > MAX_BYTES )); then
  echo "Accrescent APK set is ${SIZE_BYTES} bytes, above the 128 MiB limit." >&2
  exit 1
fi

sha256sum "${APKS_PATH}" > "${APKS_PATH}.sha256"
echo "Accrescent APK set: ${APKS_PATH}"
echo "SHA-256 sidecar: ${APKS_PATH}.sha256"
