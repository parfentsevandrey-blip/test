#!/usr/bin/env bash
# Cross-build TorSS.exe from Linux/macOS (Go 1.24+). Expects bin/ to be filled
# (sing-box.exe, wintun.dll, tor/...) — see scripts/fetch-deps.ps1 for the
# sources; on Linux you can build sing-box yourself:
#   GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go install -trimpath -ldflags "-s -w" \
#     -tags with_gvisor,with_utls,with_quic,with_dhcp github.com/sagernet/sing-box/cmd/sing-box@latest
#   cp "$(go env GOPATH)/bin/windows_amd64/sing-box.exe" bin/
set -euo pipefail
cd "$(dirname "$0")/.."

LITE=${LITE:-0}
VERSION=${VERSION:-"built $(date +%F)"}
OUT=${OUT:-dist/TorSS}
mkdir -p "$OUT"

if [ "$LITE" = "1" ]; then
  go run ./tools/pack -clean
else
  go run ./tools/pack -version "$VERSION"
fi

GOOS=windows GOARCH=amd64 CGO_ENABLED=0 \
  go build -trimpath -ldflags "-s -w -H=windowsgui" -o "$OUT/TorSS.exe" ./cmd/torss

cp config.example.json README.md "$OUT/"
[ "$LITE" = "1" ] && cp -r bin "$OUT/bin"
ls -la "$OUT"
echo "-> $OUT/TorSS.exe"
