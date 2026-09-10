#!/usr/bin/env bash
# Fetch, verify and package the runtime TorVeil embeds into its executable.
#
# TorVeil ships Tor rather than asking the user to install it, but shipping
# someone else's signed binaries only means something if the signature is
# actually checked. This script verifies the Tor Project's OpenPGP signature
# on every build and refuses to package anything that does not verify, so a
# tampered or truncated download cannot end up inside the executable.
#
# Output: internal/bundle/assets/runtime.tar.zst, consumed by //go:embed under the
# "bundled" build tag. The archive is deliberately not committed: it is 15 MB
# of third-party binaries that anyone can reproduce by running this.
set -euo pipefail

cd "$(dirname "$0")/.."

# Pinned versions. Bump deliberately, not automatically: a new Tor is a new
# set of binaries to verify and test against.
TOR_VERSION="${TOR_VERSION:-15.0.22}"
WINTUN_VERSION="${WINTUN_VERSION:-0.14.1}"
ARCH="${ARCH:-x86_64}"

# The Tor Browser Developers signing key. Everything below hangs off this
# fingerprint; it is the root of trust for the bundled Tor.
TOR_SIGNING_KEY="EF6E286DDA85EA2A4BA7DE684E2C6E8793298290"

# Wintun publishes no signature, so its release is pinned by hash instead.
WINTUN_SHA256="07c256185d6ee3652e09fa55c0b673e2624b565e02c4b9091c79ca7d2f24ef51"

OUT_DIR="internal/bundle/assets"
OUT="$OUT_DIR/runtime.tar.zst"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

BUNDLE="tor-expert-bundle-windows-${ARCH}-${TOR_VERSION}.tar.gz"
BASE="https://dist.torproject.org/torbrowser/${TOR_VERSION}"

echo "==> downloading $BUNDLE"
curl -fsSL --retry 3 -o "$WORK/$BUNDLE" "$BASE/$BUNDLE"
curl -fsSL --retry 3 -o "$WORK/$BUNDLE.asc" "$BASE/$BUNDLE.asc"

echo "==> verifying the Tor Project's signature"
export GNUPGHOME="$WORK/gnupg"
mkdir -p "$GNUPGHOME"
chmod 700 "$GNUPGHOME"
curl -fsSL --retry 3 -o "$WORK/tor-signing-key.asc" \
  "https://keys.openpgp.org/vks/v1/by-fingerprint/$TOR_SIGNING_KEY"
gpg --quiet --import "$WORK/tor-signing-key.asc"

# VALIDSIG names the primary key the signature chains up to. Checking that,
# rather than just "the signature is good", is what ties the download to the
# Tor Project rather than to whatever key happens to be in the keyring.
if ! gpg --status-fd 1 --verify "$WORK/$BUNDLE.asc" "$WORK/$BUNDLE" 2>/dev/null \
     | grep -q "^\[GNUPG:\] VALIDSIG .* $TOR_SIGNING_KEY\$"; then
  echo "    signature does NOT chain to $TOR_SIGNING_KEY — refusing to package" >&2
  exit 1
fi
echo "    good signature from the Tor Browser Developers signing key"

echo "==> downloading wintun ${WINTUN_VERSION}"
curl -fsSL --retry 3 -o "$WORK/wintun.zip" "https://www.wintun.net/builds/wintun-${WINTUN_VERSION}.zip"
echo "$WINTUN_SHA256  $WORK/wintun.zip" | sha256sum -c - >/dev/null || {
  echo "    wintun.zip does not match the pinned hash — refusing to package" >&2
  exit 1
}
echo "    hash matches the pinned release"

echo "==> assembling the runtime"
mkdir -p "$WORK/x" "$WORK/w"
tar xzf "$WORK/$BUNDLE" -C "$WORK/x"
unzip -q "$WORK/wintun.zip" -d "$WORK/w"

case "$ARCH" in
  x86_64) WINTUN_ARCH=amd64 ;;
  i686)   WINTUN_ARCH=x86 ;;
  *) echo "no wintun mapping for arch $ARCH" >&2; exit 1 ;;
esac

R="$WORK/runtime"
mkdir -p "$R/tor/pluggable_transports" "$R/data" "$R/wintun" "$R/docs"

# Only what TorVeil actually runs. conjure-client (13 MB) implements a
# transport TorVeil does not offer, and tor-gencert (6 MB) is a tool for relay
# operators; neither belongs in a client.
cp "$WORK/x/tor/tor.exe"                                 "$R/tor/"
cp "$WORK/x/tor/pluggable_transports/lyrebird.exe"       "$R/tor/pluggable_transports/"
cp "$WORK/x/tor/pluggable_transports/pt_config.json"     "$R/tor/pluggable_transports/"
cp "$WORK/x/data/geoip" "$WORK/x/data/geoip6"            "$R/data/"
cp "$WORK/w/wintun/bin/$WINTUN_ARCH/wintun.dll"          "$R/wintun/"

# Licence texts travel with the binaries they cover. Wintun's licence requires
# its notices be kept; Tor's and its dependencies' require attribution.
cp "$WORK/w/wintun/LICENSE.txt" "$R/wintun/"
cp "$WORK"/x/docs/*.txt "$R/docs/" 2>/dev/null || true

cat > "$R/VERSIONS.txt" <<EOF
Bundled with TorVeil. Downloaded from the projects below and verified at build
time by build/fetch-assets.sh.

Tor Expert Bundle  $TOR_VERSION  (windows-$ARCH)
  https://dist.torproject.org/torbrowser/$TOR_VERSION/$BUNDLE
  OpenPGP signature verified against $TOR_SIGNING_KEY
  Provides tor.exe and lyrebird.exe (obfs4, meek_lite, webtunnel, snowflake).

Wintun  $WINTUN_VERSION  ($WINTUN_ARCH)
  https://www.wintun.net/builds/wintun-$WINTUN_VERSION.zip
  sha256 $WINTUN_SHA256

Licence texts are in docs/ and wintun/LICENSE.txt. Neither project is modified.
EOF

mkdir -p "$OUT_DIR"
# Packed by a Go program rather than tar: the archive is zstd, and no zstd
# command-line tool can be assumed present. It writes deterministically, so
# the same inputs give the same bytes.
go run ./build/packruntime "$R" "$OUT"

echo
echo "wrote $OUT ($(du -h "$OUT" | cut -f1))"
echo "sha256 $(sha256sum "$OUT" | cut -d' ' -f1)"
echo
echo "Build with -tags bundled to embed it; build/build.sh does this for you."
