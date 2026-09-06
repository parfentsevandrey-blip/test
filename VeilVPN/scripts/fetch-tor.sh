#!/usr/bin/env bash
# Downloads the Tor Expert Bundle for macOS (Apple silicon + Intel) from archive.torproject.org
# and merges the binaries into universal ones with lipo.
#
#   scripts/fetch-tor.sh [output-dir]        (default: build/tor)
#   TOR_VERSION=15.0.21 TOR_ARCHES="aarch64" scripts/fetch-tor.sh
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$HERE")"
VERSION="${TOR_VERSION:-$(tr -d '[:space:]' < "$ROOT/TOR_VERSION")}"
OUT="${1:-$ROOT/build/tor}"
ARCHES="${TOR_ARCHES:-aarch64 x86_64}"
BASE="https://archive.torproject.org/tor-package-archive/torbrowser/$VERSION"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$OUT"

for arch in $ARCHES; do
  file="tor-expert-bundle-macos-$arch-$VERSION.tar.gz"
  echo "==> Downloading $file"
  curl -fL --retry 5 --retry-delay 3 -o "$WORK/$file" "$BASE/$file"
  mkdir -p "$WORK/$arch"
  tar -xzf "$WORK/$file" -C "$WORK/$arch"
done

first="${ARCHES%% *}"

merge() {
  local rel="$1"
  local name
  name="$(basename "$rel")"
  local inputs=()
  for arch in $ARCHES; do
    if [ -f "$WORK/$arch/$rel" ]; then inputs+=("$WORK/$arch/$rel"); fi
  done
  if [ "${#inputs[@]}" -eq 0 ]; then
    echo "   (skipping $rel: not in bundle)"
    return 0
  fi
  if [ "${#inputs[@]}" -gt 1 ] && command -v lipo >/dev/null 2>&1; then
    lipo -create "${inputs[@]}" -output "$OUT/$name"
  else
    cp -f "${inputs[0]}" "$OUT/$name"
  fi
  chmod 755 "$OUT/$name"
  echo "   $name"
}

echo "==> Merging binaries into $OUT"
merge tor/tor
for dylib in "$WORK/$first"/tor/*.dylib; do
  [ -f "$dylib" ] && merge "tor/$(basename "$dylib")"
done
merge tor/pluggable_transports/lyrebird
merge tor/pluggable_transports/conjure-client

echo "==> Copying data files"
cp -f "$WORK/$first/tor/pluggable_transports/pt_config.json" "$OUT/pt_config.json"
cp -f "$WORK/$first/data/geoip" "$OUT/geoip"
cp -f "$WORK/$first/data/geoip6" "$OUT/geoip6"
chmod 644 "$OUT/pt_config.json" "$OUT/geoip" "$OUT/geoip6"
rm -rf "$OUT/licenses"
if [ -d "$WORK/$first/docs" ]; then
  cp -R "$WORK/$first/docs" "$OUT/licenses"
  chmod -R u+rwX,go+rX "$OUT/licenses"
fi
echo "$VERSION" > "$OUT/VERSION"

if command -v lipo >/dev/null 2>&1; then
  echo "==> Architectures:"
  for bin in tor lyrebird conjure-client; do
    [ -f "$OUT/$bin" ] && lipo -info "$OUT/$bin"
  done
fi
echo "==> Done: Tor Expert Bundle $VERSION in $OUT"
