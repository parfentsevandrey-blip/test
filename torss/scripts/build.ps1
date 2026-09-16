<#
.SYNOPSIS
  Собирает torss.exe и складывает готовую портативную сборку в dist\TorSS\
  (+ dist\TorSS-portable.zip). Требуется Go 1.24+ (https://go.dev/dl/) и
  предварительно выполненный scripts\fetch-deps.ps1 (папка bin\).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\build.ps1
#>
[CmdletBinding()]
param(
  [switch]$Console,     # собрать с консольным окном (для отладки: torss.exe -console)
  [switch]$SkipZip,
  [switch]$Lite         # не встраивать bin\ в exe (маленький exe + папка bin рядом)
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Get-Command go -ErrorAction SilentlyContinue)) {
  throw "Go не найден. Установите с https://go.dev/dl/ и перезапустите терминал."
}
foreach ($f in "bin\sing-box.exe", "bin\wintun.dll", "bin\tor\tor.exe", "bin\tor\pluggable_transports\lyrebird.exe") {
  if (-not (Test-Path $f)) { throw "Нет $f — сначала запустите scripts\fetch-deps.ps1" }
}

$dist = Join-Path $root "dist\TorSS"
if (Test-Path $dist) { Remove-Item -Recurse -Force $dist }
New-Item -ItemType Directory -Force -Path $dist | Out-Null

$ldflags = "-s -w"
if (-not $Console) { $ldflags += " -H=windowsgui" }
$env:GOOS = "windows"; $env:GOARCH = "amd64"; $env:CGO_ENABLED = "0"

if ($Lite) {
  & go run ./tools/pack -clean
} else {
  Write-Host "pack bin\ into the executable ..."
  & go run ./tools/pack -version ("built " + (Get-Date -Format "yyyy-MM-dd"))
  if ($LASTEXITCODE -ne 0) { throw "pack failed" }
}
Write-Host "go build ..."
& go build -trimpath -ldflags $ldflags -o (Join-Path $dist "TorSS.exe") ./cmd/torss
if ($LASTEXITCODE -ne 0) { throw "go build failed" }

if ($Lite) { Copy-Item -Recurse -Force (Join-Path $root "bin") (Join-Path $dist "bin") }
Copy-Item -Force (Join-Path $root "config.example.json") $dist
Copy-Item -Force (Join-Path $root "README.md") $dist
Get-ChildItem -Recurse $dist | Where-Object { -not $_.PSIsContainer } |
  ForEach-Object { "{0,10:N0}  {1}" -f $_.Length, $_.FullName.Substring($dist.Length + 1) }

if (-not $SkipZip) {
  $zip = Join-Path $root "dist\TorSS-portable.zip"
  if (Test-Path $zip) { Remove-Item -Force $zip }
  Compress-Archive -Path $dist -DestinationPath $zip
  Write-Host "`n-> $zip"
}
Write-Host "`nГотово: $dist\TorSS.exe"
