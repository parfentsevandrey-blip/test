#!/usr/bin/env bash
# Cross-compile TorVeil for Windows from Linux or macOS.
#
# Wails v2 needs no CGO for a Windows target, so an ordinary `go build` with
# the windowsgui link flag produces the same executable the Wails CLI would.
# Use build.ps1 on Windows if you want the CLI's icon and manifest packaging.
set -euo pipefail

cd "$(dirname "$0")/.."

OUT="${OUT:-dist}"
ARCH="${ARCH:-amd64}"
VERSION="${VERSION:-dev}"

mkdir -p "$OUT"

# The embedded runtime makes the executable self-contained. Fetching it is a
# separate script because it verifies the Tor Project's signature and that
# should be legible on its own, not buried in a build.
TAGS="production"
DAEMON_TAGS=""
if [ -f internal/bundle/assets/runtime.tar.gz ]; then
  TAGS="production,bundled"
  DAEMON_TAGS="bundled"
  echo "==> runtime archive present, building a self-contained executable"
elif [ "${SKIP_ASSETS:-0}" = "1" ]; then
  echo "==> SKIP_ASSETS=1: building without a bundled Tor" >&2
else
  echo "==> fetching the Tor runtime"
  ./build/fetch-assets.sh
  TAGS="production,bundled"
  DAEMON_TAGS="bundled"
fi

echo "==> checks"
gofmt -l ./cmd ./internal | tee /dev/stderr | (! grep -q .) || {
  echo "    files above need gofmt" >&2
  exit 1
}
go vet ./...
# The Windows vet needs the production tag too, or it checks the Wails stub
# instead of the real application implementation.
GOOS=windows GOARCH="$ARCH" go vet -tags "$TAGS" ./...
go test ./...

echo "==> building torveil.exe (GUI) for windows/$ARCH"
# -tags production is not optional: without it Wails links a stub that shows
# an error dialog at start-up instead of opening a window.
GOOS=windows GOARCH="$ARCH" CGO_ENABLED=0 go build \
  -trimpath -tags "$TAGS" \
  -ldflags "-s -w -H windowsgui -X main.version=$VERSION" \
  -o "$OUT/torveil.exe" ./cmd/torveil

echo "==> building torveild.exe (headless) for windows/$ARCH"
# The headless build needs the runtime too, but not the Wails production tag.
GOOS=windows GOARCH="$ARCH" CGO_ENABLED=0 go build \
  -trimpath -tags "$DAEMON_TAGS" \
  -ldflags "-s -w -X main.version=$VERSION" \
  -o "$OUT/torveild.exe" ./cmd/torveild

cp build/torveil.exe.manifest "$OUT/torveil.exe.manifest"

if [ "$TAGS" = "production,bundled" ]; then
cat <<EOF

built into $OUT/ — self-contained, nothing else to install.

torveil.exe carries Tor, the pluggable transports (obfs4, snowflake,
meek_lite, webtunnel) and wintun.dll, and unpacks them into the user's
profile on first launch. Proxy mode works straight away; full-tunnel mode
additionally needs it to run as administrator.
EOF
else
cat <<EOF

built into $OUT/ — WITHOUT a bundled Tor.

This executable expects a Tor installed on the machine. Run
build/fetch-assets.sh and build again for a self-contained one.
EOF
fi
