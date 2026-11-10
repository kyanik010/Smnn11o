#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="$ROOT_DIR/idealplayer-logo.png"
APP_RES="$ROOT_DIR/app/src/main/res"
ALPHA_SOURCE="$(mktemp --suffix=-idealplayer-logo.png)"
trap 'rm -f "$ALPHA_SOURCE"' EXIT

magick "$SRC" -alpha on -fuzz 8% -fill none -draw 'color 0,0 floodfill' "$ALPHA_SOURCE"

echo "Creating mipmap directories..."
mkdir -p "$APP_RES"/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}

cp "$ALPHA_SOURCE" "$APP_RES/drawable-nodpi/idealplayer_logo.png"
cp "$ALPHA_SOURCE" "$ROOT_DIR/web/remote-setup/assets/idealplayer-logo.png"
magick "$ALPHA_SOURCE" -resize 480x480 -background none -gravity center -extent 480x480 \
  "$APP_RES/drawable-nodpi/idealplayer_splash_logo.png"

# Calculate dimensions
# Standard/Round icons: standard size, let's keep it similar but with a slight padding if needed. We'll use 80% size for the logo, centered.
# mdpi: 48x48. Logo size: ~38x38
# hdpi: 72x72. Logo size: ~58x58
# xhdpi: 96x96. Logo size: ~76x76
# xxhdpi: 144x144. Logo size: ~116x116
# xxxhdpi: 192x192. Logo size: ~154x154

echo "Generating standard icons..."
magick "$ALPHA_SOURCE" -resize 38x38 -background transparent -gravity center -extent 48x48 "$APP_RES/mipmap-mdpi/ic_launcher.png"
magick "$ALPHA_SOURCE" -resize 58x58 -background transparent -gravity center -extent 72x72 "$APP_RES/mipmap-hdpi/ic_launcher.png"
magick "$ALPHA_SOURCE" -resize 76x76 -background transparent -gravity center -extent 96x96 "$APP_RES/mipmap-xhdpi/ic_launcher.png"
magick "$ALPHA_SOURCE" -resize 116x116 -background transparent -gravity center -extent 144x144 "$APP_RES/mipmap-xxhdpi/ic_launcher.png"
magick "$ALPHA_SOURCE" -resize 154x154 -background transparent -gravity center -extent 192x192 "$APP_RES/mipmap-xxxhdpi/ic_launcher.png"

echo "Generating round icons..."
magick "$ALPHA_SOURCE" -resize 38x38 -background transparent -gravity center -extent 48x48 "$APP_RES/mipmap-mdpi/ic_launcher_round.png"
magick "$ALPHA_SOURCE" -resize 58x58 -background transparent -gravity center -extent 72x72 "$APP_RES/mipmap-hdpi/ic_launcher_round.png"
magick "$ALPHA_SOURCE" -resize 76x76 -background transparent -gravity center -extent 96x96 "$APP_RES/mipmap-xhdpi/ic_launcher_round.png"
magick "$ALPHA_SOURCE" -resize 116x116 -background transparent -gravity center -extent 144x144 "$APP_RES/mipmap-xxhdpi/ic_launcher_round.png"
magick "$ALPHA_SOURCE" -resize 154x154 -background transparent -gravity center -extent 192x192 "$APP_RES/mipmap-xxxhdpi/ic_launcher_round.png"

# Adaptive foregrounds: 108dp canvas, 72dp safe zone. Ratio is 72/108 = 0.666
# mdpi: 108x108, logo: 72x72
# hdpi: 162x162, logo: 108x108
# xhdpi: 216x216, logo: 144x144
# xxhdpi: 324x324, logo: 216x216
# xxxhdpi: 432x432, logo: 288x288

echo "Generating adaptive foreground icons (safe zone padded)..."
magick "$ALPHA_SOURCE" -resize 72x72 -background transparent -gravity center -extent 108x108 "$APP_RES/mipmap-mdpi/ic_launcher_foreground.png"
magick "$ALPHA_SOURCE" -resize 108x108 -background transparent -gravity center -extent 162x162 "$APP_RES/mipmap-hdpi/ic_launcher_foreground.png"
magick "$ALPHA_SOURCE" -resize 144x144 -background transparent -gravity center -extent 216x216 "$APP_RES/mipmap-xhdpi/ic_launcher_foreground.png"
magick "$ALPHA_SOURCE" -resize 216x216 -background transparent -gravity center -extent 324x324 "$APP_RES/mipmap-xxhdpi/ic_launcher_foreground.png"
magick "$ALPHA_SOURCE" -resize 288x288 -background transparent -gravity center -extent 432x432 "$APP_RES/mipmap-xxxhdpi/ic_launcher_foreground.png"

echo "Generating Android TV banner..."
magick "$ALPHA_SOURCE" -resize 160x160 -background '#000000' -gravity center -extent 320x180 \
  "$APP_RES/mipmap-xhdpi/ic_banner.png"

echo "Cleaning up redundant xml/webp resources..."
find "$APP_RES" -name "ic_launcher_foreground.xml" -type f -delete
find "$APP_RES" -name "ic_launcher*.webp" -type f -delete

echo "Icons generated successfully."
