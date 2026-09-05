#!/bin/sh
# Fetches the Tor Project's own tor build for Apple Silicon.
#
# The Android app embeds tor as a library, through tor-android's JNI wrapper.
# There is no equivalent on macOS, and building tor here would mean building
# OpenSSL and libevent with it. The Tor Project publishes exactly what is
# wanted instead: the expert bundle, which is the tor binary and its pluggable
# transports, meant for embedding in another application.
#
# tor and libevent are taken, and nothing else. tor is linked dynamically
# against the libevent that ships beside it (@executable_path/libevent-*.dylib),
# so the two travel together: a tor copied on its own dies in dyld before it
# prints a line, and that was the first release's entire failure. The
# transports in the bundle are not taken — this app runs lyrebird, Snowflake
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
rm -rf "$dest"
mkdir -p "$dest"
cp "$tmp/tor/tor" "$dest/tor"
chmod +x "$dest/tor"
for lib in "$tmp"/tor/*.dylib; do
    [ -f "$lib" ] && cp "$lib" "$dest/"
done

# The geoip files let tor label circuits by country, which the route view
# shows. Nothing depends on them being present.
for f in geoip geoip6; do
    [ -f "$tmp/data/$f" ] && cp "$tmp/data/$f" "$dest/$f" || true
done

# On a Mac, prove that what was copied actually links and runs. The bundle's
# binaries carry no signature, and Apple Silicon will not execute an unsigned
# binary at all, so they are signed ad-hoc here; the app's own packaging signs
# them again with whatever identity it has.
if command -v otool >/dev/null 2>&1 && command -v codesign >/dev/null 2>&1; then
    for bin in "$dest"/tor "$dest"/*.dylib; do
        [ -f "$bin" ] && codesign --force --sign - "$bin"
    done
    otool -L "$dest/tor" | awk '/@executable_path/ {print $1}' | while IFS= read -r dep; do
        name=${dep#@executable_path/}
        if [ ! -f "$dest/$name" ]; then
            echo "tor needs $name beside it and the bundle did not provide it" >&2
            exit 1
        fi
    done
    if ! "$dest/tor" --version >/dev/null 2>"$tmp/tor.err"; then
        echo "the fetched tor does not run here:" >&2
        cat "$tmp/tor.err" >&2
        exit 1
    fi
    echo "tor $("$dest/tor" --version | head -1) in $dest, with:"
else
    echo "tor and its libraries in $dest (not a Mac: linking not checked), with:"
fi
ls -1 "$dest"
