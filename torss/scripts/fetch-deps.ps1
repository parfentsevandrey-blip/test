<#
.SYNOPSIS
  Скачивает всё, что нужно TorSS, в папку bin\:
    bin\sing-box.exe               (SagerNet/sing-box, GitHub Releases)
    bin\wintun.dll                 (wintun.net)
    bin\tor\tor.exe                (Tor Expert Bundle, dist.torproject.org)
    bin\tor\pluggable_transports\lyrebird.exe, conjure-client.exe, pt_config.json
    bin\tor\data\geoip, geoip6

.PARAMETER Proxy
  HTTP(S)-прокси для загрузки, например http://127.0.0.1:2080 — если GitHub /
  torproject.org у вас уже заблокированы, поднимите любой рабочий прокси
  (или уже работающий TorSS) и укажите его здесь.

.PARAMETER TorMirror
  Зеркало Tor. По умолчанию https://dist.torproject.org. Альтернативы:
  https://tor.eff.org/dist , https://tor.calyxinstitute.org/dist

.PARAMETER SingBoxVersion
  Точная версия sing-box (например 1.12.4). По умолчанию берётся последний релиз
  через api.github.com.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\fetch-deps.ps1
  powershell -ExecutionPolicy Bypass -File scripts\fetch-deps.ps1 -Proxy http://127.0.0.1:2080
#>
[CmdletBinding()]
param(
  [string]$Proxy = "",
  [string]$TorMirror = "https://dist.torproject.org",
  [string]$TorVersion = "",
  [string]$SingBoxVersion = "",
  [string]$WintunVersion = "0.14.1",
  [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 -bor [Net.SecurityProtocolType]::Tls13

$root = Split-Path -Parent $PSScriptRoot
if (-not $OutDir) { $OutDir = Join-Path $root "bin" }
$tmp = Join-Path $env:TEMP ("torss-deps-" + [guid]::NewGuid().ToString("N").Substring(0, 8))
New-Item -ItemType Directory -Force -Path $OutDir, $tmp | Out-Null

function Get-Web {
  param([string]$Url, [string]$OutFile)
  $args = @{ Uri = $Url; UseBasicParsing = $true; MaximumRedirection = 10; Headers = @{ "User-Agent" = "torss-fetch-deps" } }
  if ($Proxy) { $args.Proxy = $Proxy }
  if ($OutFile) { $args.OutFile = $OutFile }
  Write-Host "GET $Url"
  return Invoke-WebRequest @args
}

function Get-Json {
  param([string]$Url)
  $r = Get-Web -Url $Url
  return ($r.Content | ConvertFrom-Json)
}

function Verify-Sha256 {
  param([string]$File, [string]$Expected)
  if (-not $Expected) { return }
  $actual = (Get-FileHash -Algorithm SHA256 $File).Hash.ToLower()
  if ($actual -ne $Expected.ToLower()) {
    throw "SHA256 mismatch for $File`n expected $Expected`n actual   $actual"
  }
  Write-Host "  sha256 OK"
}

# ---------------------------------------------------------------- sing-box --
Write-Host "`n== sing-box =="
if (-not $SingBoxVersion) {
  $rel = Get-Json "https://api.github.com/repos/SagerNet/sing-box/releases/latest"
  $SingBoxVersion = $rel.tag_name.TrimStart("v")
}
$sbName = "sing-box-$SingBoxVersion-windows-amd64.zip"
$sbUrl = "https://github.com/SagerNet/sing-box/releases/download/v$SingBoxVersion/$sbName"
$sbZip = Join-Path $tmp $sbName
Get-Web -Url $sbUrl -OutFile $sbZip | Out-Null
try {
  $sums = (Get-Web -Url "$sbUrl.sha256").Content
  $expected = ($sums -split "\s+")[0]
  Verify-Sha256 -File $sbZip -Expected $expected
} catch { Write-Warning "sing-box: no .sha256 file to verify against ($_)" }
Expand-Archive -Force -Path $sbZip -DestinationPath (Join-Path $tmp "sb")
$sbExe = Get-ChildItem -Recurse -Filter "sing-box.exe" (Join-Path $tmp "sb") | Select-Object -First 1
Copy-Item -Force $sbExe.FullName (Join-Path $OutDir "sing-box.exe")
Write-Host "  -> $OutDir\sing-box.exe ($SingBoxVersion)"

# ------------------------------------------------------------------ wintun --
Write-Host "`n== wintun =="
$wtZip = Join-Path $tmp "wintun.zip"
Get-Web -Url "https://www.wintun.net/builds/wintun-$WintunVersion.zip" -OutFile $wtZip | Out-Null
Expand-Archive -Force -Path $wtZip -DestinationPath (Join-Path $tmp "wt")
Copy-Item -Force (Join-Path $tmp "wt\wintun\bin\amd64\wintun.dll") (Join-Path $OutDir "wintun.dll")
Write-Host "  -> $OutDir\wintun.dll ($WintunVersion)"

# --------------------------------------------------------- Tor Expert Bundle --
Write-Host "`n== Tor Expert Bundle =="
if (-not $TorVersion) {
  $index = (Get-Web -Url "$TorMirror/torbrowser/").Content
  # каталоги вида 15.0.23/ ; альфы (16.0a11) пропускаем
  $versions = [regex]::Matches($index, 'href="(\d+\.\d+(\.\d+)?)/"') | ForEach-Object { $_.Groups[1].Value } | Sort-Object { [version]$_ }
  if (-not $versions) { throw "Не удалось получить список версий с $TorMirror/torbrowser/" }
  $TorVersion = $versions[-1]
}
$tebName = "tor-expert-bundle-windows-x86_64-$TorVersion.tar.gz"
$tebUrl = "$TorMirror/torbrowser/$TorVersion/$tebName"
$tebFile = Join-Path $tmp $tebName
Get-Web -Url $tebUrl -OutFile $tebFile | Out-Null
try {
  # подпись .asc можно проверить вручную: gpg --verify $tebName.asc $tebName (ключ Tor Browser Developers)
  Get-Web -Url "$tebUrl.asc" -OutFile "$tebFile.asc" | Out-Null
  Copy-Item -Force "$tebFile.asc" (Join-Path $OutDir "tor-expert-bundle.tar.gz.asc")
} catch { Write-Warning "Tor: не удалось скачать .asc ($_)" }
$tebDir = Join-Path $tmp "teb"
New-Item -ItemType Directory -Force -Path $tebDir | Out-Null
& tar.exe -xzf $tebFile -C $tebDir
if ($LASTEXITCODE -ne 0) { throw "tar.exe failed" }

$torOut = Join-Path $OutDir "tor"
if (Test-Path $torOut) { Remove-Item -Recurse -Force $torOut }
New-Item -ItemType Directory -Force -Path $torOut | Out-Null
Copy-Item -Recurse -Force (Join-Path $tebDir "tor\*") $torOut
Copy-Item -Recurse -Force (Join-Path $tebDir "data") (Join-Path $torOut "data")
Write-Host "  -> $torOut (Tor Expert Bundle $TorVersion)"
Get-ChildItem -Recurse $torOut | Where-Object { -not $_.PSIsContainer } | ForEach-Object { Write-Host "     " $_.FullName.Substring($OutDir.Length + 1) }

Remove-Item -Recurse -Force $tmp
Write-Host "`nГотово. Теперь: powershell -ExecutionPolicy Bypass -File scripts\build.ps1"
