#!/usr/bin/env bash
set -euo pipefail
umask 077

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCAL_PROPERTIES="$ROOT_DIR/local.properties"
KEYSTORE_DIR="$ROOT_DIR/keystores"
KEYSTORE_FILE="$KEYSTORE_DIR/idealplayer-release.jks"
KEY_ALIAS="${IDEALPLAYER_RELEASE_KEY_ALIAS:-idealplayer-release}"

mkdir -p "$KEYSTORE_DIR"
chmod 700 "$KEYSTORE_DIR"
touch "$LOCAL_PROPERTIES"
chmod 600 "$LOCAL_PROPERTIES"

upsert_property() {
  local key="$1"
  local value="$2"
  local tmp
  tmp="$(mktemp)"

  if grep -q "^${key}=" "$LOCAL_PROPERTIES"; then
    awk -F= -v key="$key" -v value="$value" '
      $1 == key { print key "=" value; next }
      { print }
    ' "$LOCAL_PROPERTIES" > "$tmp"
  else
    cp "$LOCAL_PROPERTIES" "$tmp"
    printf '%s=%s\n' "$key" "$value" >> "$tmp"
  fi

  mv "$tmp" "$LOCAL_PROPERTIES"
  chmod 600 "$LOCAL_PROPERTIES"
}

if [[ -f "$KEYSTORE_FILE" ]]; then
  echo "Release keystore already exists: $KEYSTORE_FILE"
else
  STORE_PASSWORD="$(openssl rand -base64 32 | tr -d '\n')"
  KEY_PASSWORD="$(openssl rand -base64 32 | tr -d '\n')"

  keytool -genkeypair \
    -v \
    -storetype JKS \
    -keystore "$KEYSTORE_FILE" \
    -storepass "$STORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -dname "CN=IdealPlayer, OU=IdealPlayer, O=IdealPlayer, L=Istanbul, ST=Istanbul, C=TR"

  upsert_property "IDEALPLAYER_RELEASE_STORE_PASSWORD" "$STORE_PASSWORD"
  upsert_property "IDEALPLAYER_RELEASE_KEY_PASSWORD" "$KEY_PASSWORD"
fi

chmod 600 "$KEYSTORE_FILE"

upsert_property "IDEALPLAYER_RELEASE_STORE_FILE" "keystores/idealplayer-release.jks"
upsert_property "IDEALPLAYER_RELEASE_KEY_ALIAS" "$KEY_ALIAS"
upsert_property "UPDATE_MANIFEST_URL" "https://github.com/meliherenn/IdealPlayer/releases/latest/download/latest.json"

echo "Release signing is configured in local.properties."
echo "Back up keystores/idealplayer-release.jks securely. Future updates must use this same keystore."
