#!/usr/bin/env bash
# Signs Veil.app (ad-hoc unless CODESIGN_IDENTITY is set) and wraps it into a compressed DMG.
#   scripts/package-dmg.sh build/Veil.app build/Veil-0.1.0.dmg
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$HERE")"
APP="$1"
DMG="$2"
VOLUME="${3:-Veil}"
IDENTITY="${CODESIGN_IDENTITY:--}"

[ -d "$APP" ] || { echo "error: $APP not found"; exit 1; }

echo "==> Signing $APP ($IDENTITY)"
codesign --force --sign "$IDENTITY" --timestamp=none "$APP"
codesign --verify --deep --strict --verbose=1 "$APP"

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
cp -R "$APP" "$STAGE/"
ln -s /Applications "$STAGE/Applications"
cp "$ROOT/docs/INSTALL.txt" "$STAGE/Как установить · How to install.txt"

echo "==> Creating $DMG"
mkdir -p "$(dirname "$DMG")"
rm -f "$DMG"
hdiutil create -volname "$VOLUME" -srcfolder "$STAGE" -ov -format UDZO -imagekey zlib-level=9 "$DMG"
ls -la "$DMG"
shasum -a 256 "$DMG"
