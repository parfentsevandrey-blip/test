#!/bin/sh
# Fetches the Tor Project's own tor build for Apple Silicon.
#
# The Android app embeds tor as a library, through tor-android's JNI wrapper.
# There is no equivalent on macOS, and building tor here would mean building
# OpenSSL and libevent with it. The Tor Project publishes exactly what is
# wanted instead: the expert bundle, which is the tor binary and its pluggable
# transports, meant for embedding in another application.
#
# Only tor itself is taken. The transports in the bundle are the standard
# executables, and this app does not use them — it runs lyrebird, Snowflake
# and Conjure in-process from the Go core, which is what lets it race
# Snowflake's two rendezvous inside one bridge line.
set -eu

version=${TOR_VERSION:-14.5.4}
here=$(cd "$(dirname "$0")/.." && pwd)
dest="$here/Veil/Resources/tor"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

archive="tor-expert-bundle-macos-aarch64-$version.tar.gz"
url="https://archive.torproject.org/tor-package-archive/torbrowser/$version/$archive"

echo "fetching $archive"
curl -fL --retry 3 -o "$tmp/$archive" "$url"

echo "checking the signature is at least present"
if curl -fsL --retry 2 -o "$tmp/$archive.asc" "$url.asc"; then
    if command -v gpg >/dev/null 2>&1; then
        gpg --verify "$tmp/$archive.asc" "$tmp/$archive" 2>/dev/null \
            && echo "  signature verified" \
            || echo "  WARNING: signature not verified (the Tor Browser signing key may not be in your keyring)"
    else
        echo "  gpg not installed; signature downloaded but not checked"
    fi
else
    echo "  WARNING: no detached signature was served"
fi

tar xzf "$tmp/$archive" -C "$tmp"
mkdir -p "$dest"
cp "$tmp/tor/tor" "$dest/tor"
chmod +x "$dest/tor"

# The geoip files let tor label circuits by country, which the route view
# shows. Nothing depends on them being present.
for f in geoip geoip6; do
    [ -f "$tmp/data/$f" ] && cp "$tmp/data/$f" "$dest/$f" || true
done

echo "tor $("$dest/tor" --version 2>/dev/null | head -1) in $dest"
echo
echo "Note: the binary is signed by the Tor Project, not by you. Xcode will"
echo "re-sign it on copy; the target already sets that up."
