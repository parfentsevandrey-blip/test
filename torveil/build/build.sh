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

echo "==> checks"
gofmt -l ./cmd ./internal | tee /dev/stderr | (! grep -q .) || {
  echo "    files above need gofmt" >&2
  exit 1
}
go vet ./...
GOOS=windows GOARCH="$ARCH" go vet ./...
go test ./...

echo "==> building torveil.exe (GUI) for windows/$ARCH"
GOOS=windows GOARCH="$ARCH" CGO_ENABLED=0 go build \
  -trimpath \
  -ldflags "-s -w -H windowsgui -X main.version=$VERSION" \
  -o "$OUT/torveil.exe" ./cmd/torveil

echo "==> building torveild.exe (headless) for windows/$ARCH"
GOOS=windows GOARCH="$ARCH" CGO_ENABLED=0 go build \
  -trimpath \
  -ldflags "-s -w -X main.version=$VERSION" \
  -o "$OUT/torveild.exe" ./cmd/torveild

cp build/torveil.exe.manifest "$OUT/torveil.exe.manifest"

cat <<EOF

built into $OUT/

Before running on Windows, put two things next to torveil.exe:

  wintun.dll   only needed for full-tunnel mode. Download the matching
               architecture from https://www.wintun.net/ and take the DLL from
               its bin/$ARCH folder. Proxy mode does not need it.

  tor.exe      TorVeil drives a Tor build you install rather than bundling one,
               so the binary you run stays one you can verify. Install the Tor
               Expert Bundle or Tor Browser; TorVeil finds either automatically,
               or point "Extra search directory" in Settings at the folder.
               For Snowflake, snowflake-client.exe must be in the same place —
               the Expert Bundle ships it under pluggable_transports/.

Full-tunnel mode additionally needs TorVeil to run as administrator.
EOF
