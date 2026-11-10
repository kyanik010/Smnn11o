#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO="${GITHUB_REPO:-meliherenn/IdealPlayer}"

if [[ $# -lt 1 ]]; then
  cat <<'USAGE'
Usage:
  scripts/upload_github_update_release.sh <version-name> [release-notes-file]

Example:
  scripts/upload_github_update_release.sh 1.0.1 notes.txt

Before running:
  1. Commit and push the code for this version.
  2. Run scripts/publish_self_hosted_update.sh <version-name>.
  3. Then run this script to create/update the GitHub Release assets.
USAGE
  exit 1
fi

VERSION_NAME="$1"
NOTES_FILE="${2:-}"
TAG="v$VERSION_NAME"
BUNDLE_DIR="$ROOT_DIR/build/self-hosted-update/$VERSION_NAME"
APK_FILE="$BUNDLE_DIR/idealplayer-$VERSION_NAME.apk"
MANIFEST_FILE="$BUNDLE_DIR/latest.json"

if [[ ! -f "$APK_FILE" || ! -f "$MANIFEST_FILE" ]]; then
  echo "Missing update bundle for $VERSION_NAME. Run scripts/publish_self_hosted_update.sh $VERSION_NAME first." >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "GitHub CLI is not authenticated. Run gh auth login first." >&2
  exit 1
fi

NOTES_ARGS=(--notes "IdealPlayer $VERSION_NAME")
if [[ -n "$NOTES_FILE" ]]; then
  NOTES_ARGS=(--notes-file "$NOTES_FILE")
fi

if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
  gh release upload "$TAG" "$APK_FILE" "$MANIFEST_FILE" --repo "$REPO" --clobber
  gh release edit "$TAG" --repo "$REPO" --title "IdealPlayer $VERSION_NAME" --latest "${NOTES_ARGS[@]}"
else
  gh release create "$TAG" \
    "$APK_FILE" \
    "$MANIFEST_FILE" \
    --repo "$REPO" \
    --title "IdealPlayer $VERSION_NAME" \
    --latest \
    "${NOTES_ARGS[@]}"
fi

cat <<OUTPUT
GitHub Release is ready:
  https://github.com/$REPO/releases/tag/$TAG

Stable update manifest URL used by the app:
  https://github.com/$REPO/releases/latest/download/latest.json
OUTPUT
