# Build TorVeil on Windows.
#
# Plain `go build` is enough: Wails v2 needs no CGO on Windows. If the Wails
# CLI is installed, `wails build` additionally packages an icon and an embedded
# manifest; this script produces the same executable without that dependency.

$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$Out     = if ($env:OUT)     { $env:OUT }     else { "dist" }
$Version = if ($env:VERSION) { $env:VERSION } else { "dev" }

New-Item -ItemType Directory -Force -Path $Out | Out-Null

Write-Host "==> checks"
$unformatted = & gofmt -l ./cmd ./internal
if ($unformatted) {
    Write-Error "these files need gofmt:`n$unformatted"
}
# The production tag is needed here too, or vet checks the Wails stub instead
# of the real application implementation.
go vet -tags production ./...
if ($LASTEXITCODE -ne 0) { Write-Error "go vet failed" }
go test ./...
if ($LASTEXITCODE -ne 0) { Write-Error "tests failed" }

Write-Host "==> building torveil.exe (GUI)"
$env:CGO_ENABLED = "0"
# -tags production is not optional: without it Wails links a stub that shows
# an error dialog at start-up instead of opening a window.
go build -trimpath -tags production -ldflags "-s -w -H windowsgui -X main.version=$Version" -o "$Out\torveil.exe" ./cmd/torveil
if ($LASTEXITCODE -ne 0) { Write-Error "build failed" }

Write-Host "==> building torveild.exe (headless)"
go build -trimpath -ldflags "-s -w -X main.version=$Version" -o "$Out\torveild.exe" ./cmd/torveild
if ($LASTEXITCODE -ne 0) { Write-Error "build failed" }

Copy-Item "build\torveil.exe.manifest" "$Out\torveil.exe.manifest" -Force

Write-Host @"

built into $Out\

Before running, put two things next to torveil.exe:

  wintun.dll   only needed for full-tunnel mode. Download the matching
               architecture from https://www.wintun.net/ and take the DLL
               from its bin\amd64 folder. Proxy mode does not need it.

  tor.exe      TorVeil drives a Tor build you install rather than bundling
               one, so the binary you run stays one you can verify. Install
               the Tor Expert Bundle or Tor Browser; TorVeil finds either
               automatically, or point "Extra search directory" in Settings
               at the folder. For Snowflake, snowflake-client.exe must be in
               the same place - the Expert Bundle ships it under
               pluggable_transports\.

Full-tunnel mode additionally needs TorVeil to run as administrator.
"@
