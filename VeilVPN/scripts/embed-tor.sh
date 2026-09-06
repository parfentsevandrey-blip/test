#!/usr/bin/env bash
# Copies the fetched Tor bundle into Veil.app and ad-hoc signs the helper binaries.
#   scripts/embed-tor.sh build/tor build/Veil.app
set -euo pipefail

TOR_DIR="$1"
APP="$2"
MACOS="$APP/Contents/MacOS"
RESOURCES="$APP/Contents/Resources"
IDENTITY="${CODESIGN_IDENTITY:--}"

[ -x "$TOR_DIR/tor" ] || { echo "error: $TOR_DIR/tor not found (run scripts/fetch-tor.sh)"; exit 1; }
mkdir -p "$MACOS" "$RESOURCES"

echo "==> Embedding Tor into $APP"
for binary in tor lyrebird conjure-client; do
  if [ -f "$TOR_DIR/$binary" ]; then
    cp -f "$TOR_DIR/$binary" "$MACOS/$binary"
    chmod 755 "$MACOS/$binary"
  fi
done
for dylib in "$TOR_DIR"/*.dylib; do
  if [ -f "$dylib" ]; then
    cp -f "$dylib" "$MACOS/$(basename "$dylib")"
    chmod 755 "$MACOS/$(basename "$dylib")"
  fi
done
for resource in geoip geoip6 pt_config.json VERSION; do
  if [ -f "$TOR_DIR/$resource" ]; then
    cp -f "$TOR_DIR/$resource" "$RESOURCES/$resource"
  fi
done
if [ -d "$TOR_DIR/licenses" ]; then
  rm -rf "$RESOURCES/tor-licenses"
  cp -R "$TOR_DIR/licenses" "$RESOURCES/tor-licenses"
fi

echo "==> Signing helper binaries ($IDENTITY)"
for dylib in "$MACOS"/*.dylib; do
  [ -f "$dylib" ] && codesign --force --sign "$IDENTITY" --timestamp=none "$dylib"
done
for binary in tor lyrebird conjure-client; do
  [ -f "$MACOS/$binary" ] && codesign --force --sign "$IDENTITY" --timestamp=none "$MACOS/$binary"
done
echo "==> Tor embedded"
