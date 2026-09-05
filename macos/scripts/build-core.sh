#!/bin/sh
# Builds the shared Go core as an xcframework for Apple Silicon.
#
# The module is the Android one, unchanged: the transports, the TCP/IP stack,
# the ad blocker and the seed all compile for darwin/arm64 as they are. Only
# the two files in VeilCore/ are macOS-specific, and they are copied in rather
# than kept in a second module so the two builds can never drift apart.
set -eu

here=$(cd "$(dirname "$0")/.." && pwd)
module="$here/../android/native/veiltun"
staging="$here/.build/veiltun"
out="$here/Frameworks"

if [ ! -d "$module" ]; then
    echo "the shared Go module is not where it should be: $module" >&2
    exit 1
fi
command -v gomobile >/dev/null 2>&1 || {
    echo "gomobile is not installed. Run:" >&2
    echo "  go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init" >&2
    exit 1
}

echo "staging the shared module"
rm -rf "$staging"
mkdir -p "$staging"
# Everything but the Android-only entry point and the test harnesses; on
# darwin, StartTunnel in macos.go takes its place.
(cd "$module" && tar cf - \
    --exclude='gen' --exclude='cmd' --exclude='ptlab' --exclude='vpngatelab' \
    .) | (cd "$staging" && tar xf -)
cp "$here/VeilCore/"*.go "$staging/"

# veiltun.go's Start() opens the fd with the Linux-only fdbased endpoint, so
# on darwin it is not built at all. Everything else in the file — the Config,
# the counters, the logger, Stop — is shared, so the guard goes on the one
# function rather than on the file.
python3 - "$staging/veiltun.go" <<'PY'
import re, sys
path = sys.argv[1]
src = open(path).read()
if 'fdbased' in src:
    src = src.replace(
        '\t"github.com/xjasonlyu/tun2socks/v2/core/device/fdbased"\n', '')
    start = src.index('// Start brings the tunnel up.')
    end = src.index('\n// Stop tears the tunnel down', start)
    src = src[:start] + src[end + 1:]
    src = re.sub(r'\n\t"github.com/xjasonlyu/tun2socks/v2/core"\n', '\n', src)
    src = re.sub(r'\n\t"github.com/xjasonlyu/tun2socks/v2/core/option"\n', '\n', src)
    open(path, 'w').write(src)
    print("  removed the Linux-only Start(); darwin uses StartTunnel()")
PY

echo "tidying"
(cd "$staging" && go mod tidy >/dev/null 2>&1 || true)
(cd "$staging" && go build ./... )

echo "binding for macos/arm64"
mkdir -p "$out"
(cd "$staging" && gomobile bind \
    -target=macos/arm64 \
    -ldflags="-s -w -checklinkname=0" \
    -o "$out/Veiltun.xcframework" \
    .)

echo "built $out/Veiltun.xcframework"
