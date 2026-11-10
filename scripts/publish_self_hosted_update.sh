#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_FILE="$ROOT_DIR/version.properties"

if [[ $# -lt 1 ]]; then
  cat <<'USAGE'
Usage:
  scripts/publish_self_hosted_update.sh <version-name> [mandatory] [release-notes-file]

Example:
  scripts/publish_self_hosted_update.sh 1.0.1 true notes.txt

The script increments VERSION_CODE, builds the APK, copies it to build/self-hosted-update/<version>,
and writes latest.json for upload beside the APK.

Default APK URL:
  https://github.com/meliherenn/IdealPlayer/releases/latest/download/<apk>

Override with:
  APK_BASE_URL=https://updates.your-domain.com/idealplayer scripts/publish_self_hosted_update.sh 1.0.1
USAGE
  exit 1
fi

VERSION_NAME="$1"
APK_BASE_URL="${APK_BASE_URL:-https://github.com/meliherenn/IdealPlayer/releases/latest/download}"
APK_BASE_URL="${APK_BASE_URL%/}"
MANDATORY="${2:-false}"
NOTES_FILE="${3:-}"
BUILD_TASK="${BUILD_TASK:-:app:assembleSelfHostedRelease}"
MIN_SUPPORTED_VERSION_CODE="${MIN_SUPPORTED_VERSION_CODE:-0}"

resolve_apksigner() {
  if [[ -n "${APKSIGNER:-}" && -x "$APKSIGNER" ]]; then
    printf '%s\n' "$APKSIGNER"
    return 0
  fi

  local from_path
  from_path="$(command -v apksigner || true)"
  if [[ -n "$from_path" ]]; then
    printf '%s\n' "$from_path"
    return 0
  fi

  local sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -z "$sdk_root" && -f "$ROOT_DIR/local.properties" ]]; then
    sdk_root="$(awk -F= '/^sdk.dir=/{ sub(/\r$/, "", $2); print $2; exit }' "$ROOT_DIR/local.properties")"
  fi

  local candidate
  local resolved=""
  for candidate in "$sdk_root"/build-tools/*/apksigner; do
    [[ -x "$candidate" ]] || continue
    resolved="$candidate"
  done

  [[ -n "$resolved" ]] || return 1
  printf '%s\n' "$resolved"
}

if [[ "$MANDATORY" != "true" && "$MANDATORY" != "false" ]]; then
  echo "mandatory must be true or false" >&2
  exit 1
fi

if [[ ! -f "$VERSION_FILE" ]]; then
  echo "Missing $VERSION_FILE" >&2
  exit 1
fi

APKSIGNER_BIN="$(resolve_apksigner)" || {
  echo "Unable to find apksigner. Set APKSIGNER or configure the Android SDK." >&2
  exit 1
}

CURRENT_CODE="$(awk -F= '/^VERSION_CODE=/{print $2}' "$VERSION_FILE" | tr -d '[:space:]')"
if [[ -z "$CURRENT_CODE" ]]; then
  echo "VERSION_CODE is missing from $VERSION_FILE" >&2
  exit 1
fi

VERSION_CODE="$((CURRENT_CODE + 1))"
TMP_VERSION_FILE="$(mktemp)"
VERSION_FILE_BACKUP="$(mktemp)"
cp "$VERSION_FILE" "$VERSION_FILE_BACKUP"

restore_version_on_error() {
  local status=$?
  if [[ $status -ne 0 ]]; then
    cp "$VERSION_FILE_BACKUP" "$VERSION_FILE"
  fi
  rm -f "$VERSION_FILE_BACKUP"
  exit "$status"
}
trap restore_version_on_error EXIT

awk -F= -v code="$VERSION_CODE" -v name="$VERSION_NAME" '
  BEGIN { OFS = "=" }
  /^VERSION_CODE=/ { print "VERSION_CODE", code; next }
  /^VERSION_NAME=/ { print "VERSION_NAME", name; next }
  { print }
' "$VERSION_FILE" > "$TMP_VERSION_FILE"
mv "$TMP_VERSION_FILE" "$VERSION_FILE"

cd "$ROOT_DIR"
./gradlew "$BUILD_TASK"

APK_SOURCE="$(find "$ROOT_DIR/app/build/outputs/apk" -path "*/selfHostedRelease/*" -name "*.apk" | sort | head -n 1)"

if [[ -z "$APK_SOURCE" ]]; then
  echo "No APK was produced by $BUILD_TASK" >&2
  exit 1
fi

if ! "$APKSIGNER_BIN" verify --verbose "$APK_SOURCE"; then
  echo "Refusing to publish an APK that does not pass apksigner verification." >&2
  exit 1
fi

OUT_DIR="$ROOT_DIR/build/self-hosted-update/$VERSION_NAME"
APK_NAME="idealplayer-$VERSION_NAME.apk"
mkdir -p "$OUT_DIR"
cp "$APK_SOURCE" "$OUT_DIR/$APK_NAME"

SHA256="$(sha256sum "$OUT_DIR/$APK_NAME" | awk '{print $1}')"
APK_URL="$APK_BASE_URL/$APK_NAME"
RELEASE_NOTES=""
if [[ -n "$NOTES_FILE" ]]; then
  RELEASE_NOTES="$(cat "$NOTES_FILE")"
fi

VERSION_CODE="$VERSION_CODE" \
VERSION_NAME="$VERSION_NAME" \
APK_URL="$APK_URL" \
SHA256="$SHA256" \
MANDATORY="$MANDATORY" \
MIN_SUPPORTED_VERSION_CODE="$MIN_SUPPORTED_VERSION_CODE" \
RELEASE_NOTES="$RELEASE_NOTES" \
python3 - <<'PY' > "$OUT_DIR/latest.json"
import json
import os

manifest = {
    "versionCode": int(os.environ["VERSION_CODE"]),
    "versionName": os.environ["VERSION_NAME"],
    "apkUrl": os.environ["APK_URL"],
    "sha256": os.environ["SHA256"],
    "mandatory": os.environ["MANDATORY"] == "true",
    "minSupportedVersionCode": int(os.environ["MIN_SUPPORTED_VERSION_CODE"]),
    "releaseNotes": os.environ["RELEASE_NOTES"],
}
print(json.dumps(manifest, ensure_ascii=False, indent=2))
PY

cat <<OUTPUT
Update bundle created:
  APK:       $OUT_DIR/$APK_NAME
  Manifest:  $OUT_DIR/latest.json
  SHA-256:   $SHA256

Upload both files to:
  $APK_BASE_URL/

Build app releases with:
  UPDATE_MANIFEST_URL=$APK_BASE_URL/latest.json ./gradlew :app:assembleSelfHostedRelease
OUTPUT

trap - EXIT
rm -f "$VERSION_FILE_BACKUP"
