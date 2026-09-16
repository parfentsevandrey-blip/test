This directory is filled by `go run ./tools/pack` with gzip-compressed copies of
bin/ (sing-box.exe, wintun.dll, tor/...) plus manifest.json, which `go build`
then embeds into torss.exe (single self-contained executable).

With only this README present the build is "lite": torss.exe expects a bin/
directory next to it (see scripts/fetch-deps.ps1).
