<#
  home-server.ps1 — «Домашний сервер»: панель, которая превращает этот
  компьютер с Windows 10/11 в веб-сервер для сайта из GitHub.

  Запускайте через home-server.cmd (двойной щелчок). Что происходит:
    • без параметров — скрипт просит права администратора, запускает в фоне
      «движок» панели и открывает её окно (Microsoft Edge в режиме приложения);
    • с ключом -Backend — это и есть движок: слушает http://127.0.0.1:<порт>
      (доступен только с этого компьютера и только с одноразовым ключом) и
      выполняет команды из окна: установка, домен, проверки, удаление.

  Всё устанавливается в C:\HomeServer:
    caddy.exe      веб-сервер Caddy — служба Windows «HomeServer»
    Caddyfile      его настройки (создаются панелью)
    site\          файлы сайта
    update.ps1     автообновление сайта — задача планировщика «HomeServer Update»
    data\, logs\   сертификаты HTTPS и журналы
#>
param(
    [switch]$Backend,
    [int]$Port = 0,
    [string]$Token = ''
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
try {
    [Net.ServicePointManager]::SecurityProtocol =
        [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
} catch { }

$Script:Version     = '1.0'
$Script:Root        = 'C:\HomeServer'
$Script:SourceDir   = $PSScriptRoot
$Script:ServiceName = 'HomeServer'
$Script:TaskName    = 'HomeServer Update'
$Script:RuleGroup   = 'HomeServer'
$Script:DefaultRepo = 'parfentsevandrey-blip/test'
$Script:PanelLog    = Join-Path ([IO.Path]::GetTempPath()) 'home-server-panel.log'

# =============================================================== общие помощники

function Write-PanelLog([string]$Message) {
    try {
        $line = '{0}  {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message
        Add-Content -LiteralPath $Script:PanelLog -Value $line -Encoding UTF8
    } catch { }
}

# Запуск внешней программы. Caddy и другие утилиты пишут в stderr, а Windows
# PowerShell при $ErrorActionPreference = 'Stop' считает это ошибкой.
function Invoke-Native([string]$Exe, [string[]]$Arguments) {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $out = @(& $Exe @Arguments 2>&1 | ForEach-Object { "$_" })
    } finally {
        $ErrorActionPreference = $prev
    }
    return [pscustomobject]@{ Code = $LASTEXITCODE; Output = $out }
}

function Format-Time($Date) {
    if (-not $Date -or ([datetime]$Date).Year -lt 2000) { return '' }
    return ([datetime]$Date).ToString('dd.MM.yyyy HH:mm')
}

function Get-RootPath([string]$Name) { return Join-Path $Script:Root $Name }

# ================================================================== настройки

function Get-Config {
    $cfg = [ordered]@{
        Domain = ''; DuckDnsToken = ''; DdnsUrl = ''
        Repo = $Script:DefaultRepo; Branch = ''
        IpType = ''; Upnp = $false; UpnpIp = ''; PowerSet = $false
    }
    $file = Get-RootPath 'config.json'
    if (Test-Path -LiteralPath $file) {
        $saved = Get-Content -LiteralPath $file -Raw -Encoding UTF8 | ConvertFrom-Json
        foreach ($p in $saved.PSObject.Properties) { $cfg[$p.Name] = $p.Value }
    }
    return $cfg
}

function Save-Config($Cfg) {
    New-Item -ItemType Directory -Force -Path $Script:Root | Out-Null
    ConvertTo-Json -InputObject $Cfg | Set-Content -LiteralPath (Get-RootPath 'config.json') -Encoding UTF8
}

# "Кутузовский12.РФ, https://www.Site.ru/" -> xn--12-dlctggdc8a5ahlb.xn--p1ai, www.site.ru
function ConvertTo-DomainList([string]$Text) {
    $idn = New-Object System.Globalization.IdnMapping
    $out = New-Object System.Collections.Generic.List[string]
    foreach ($d in ($Text -split '[\s,;]+')) {
        if (-not $d) { continue }
        $d = ($d -replace '^[a-zA-Z]+://', '').Split('/')[0].TrimEnd('.')
        try { $d = $idn.GetAscii($d.ToLowerInvariant()) } catch { throw "«$d» не похоже на доменное имя" }
        if ($d -notmatch '^([a-z0-9]([a-z0-9-]*[a-z0-9])?\.)+[a-z0-9-]{2,}$') {
            throw "«$d» не похоже на доменное имя (пример: mysite.ru или kutuzovsky12.duckdns.org)"
        }
        if (-not $out.Contains($d)) { $out.Add($d) }
    }
    return $out.ToArray()  # вызывать как @(ConvertTo-DomainList ...)
}

function New-CaddyfileText([string[]]$Domains) {
    $r = $Script:Root -replace '\\', '/'
    $text = @"
# Создано панелью «Домашний сервер» — при каждой настройке файл перезаписывается.
# Справка по формату: https://caddyserver.com/docs/caddyfile
{
	storage file_system $r/data
	log {
		output file $r/logs/caddy.log {
			roll_size 5MiB
			roll_keep 3
		}
	}
}

(site) {
	root * $r/site
	encode zstd gzip
	file_server {
		hide .git .github deploy
	}
	header {
		X-Content-Type-Options nosniff
		Referrer-Policy strict-origin-when-cross-origin
	}
}

"@
    if ($Domains.Count -gt 0) {
        $text += "`n# Сайт в интернете. HTTPS-сертификат Caddy получит и будет продлевать сам.`n"
        $text += "$($Domains -join ', ') {`n`timport site`n}`n"
    }
    $text += "`n# Тот же сайт по http://<IP компьютера> — для проверки из домашней сети.`n"
    $text += ":80 {`n`timport site`n}`n"
    return $text
}

# ========================================================== сеть и состояние

# Адрес компьютера в домашней сети: берём физический адаптер с маршрутом
# в интернет (VPN-адаптеры пропускаем).
function Get-LanInfo {
    $info = [ordered]@{ ip = ''; gateway = ''; adapter = ''; mac = ''; wifi = $false; ssid = '' }
    $physical = @(Get-NetAdapter -Physical -ErrorAction SilentlyContinue | Where-Object { $_.Status -eq 'Up' })
    $routes = @(Get-NetRoute -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue |
        Where-Object { $physical.ifIndex -contains $_.ifIndex } |
        Sort-Object { $_.RouteMetric + $_.InterfaceMetric })
    if ($routes.Count -eq 0) { return $info }
    $route = $routes[0]
    $adapter = $physical | Where-Object { $_.ifIndex -eq $route.ifIndex } | Select-Object -First 1
    $addr = Get-NetIPAddress -InterfaceIndex $route.ifIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notlike '169.254.*' } | Select-Object -First 1
    $info.ip = [string]$addr.IPAddress
    $info.gateway = [string]$route.NextHop
    $info.adapter = [string]$adapter.Name
    $info.mac = [string]$adapter.MacAddress
    $info.wifi = [bool]($adapter.PhysicalMediaType -match '802\.11')
    if ($info.wifi) {
        $line = (Invoke-Native 'netsh.exe' @('wlan', 'show', 'interfaces')).Output |
            Select-String -Pattern '^\s*SSID\s*:\s*(.+)$' | Select-Object -First 1
        if ($line) { $info.ssid = $line.Matches[0].Groups[1].Value.Trim() }
    }
    return $info
}

function Get-VpnAdapters {
    return @(Get-NetAdapter -ErrorAction SilentlyContinue | Where-Object {
            $_.Status -eq 'Up' -and ("$($_.InterfaceDescription) $($_.Name)" -match
                'WireGuard|Wintun|TAP-Windows|OpenVPN|AmneziaWG|Outline|Hiddify|sing-box|\bVPN\b|\bTUN\b')
        } | ForEach-Object { [string]$_.Name })
}

function Get-PublicIp([switch]$Refresh) {
    if (-not $Refresh -and $Script:PublicIp -and ((Get-Date) - $Script:PublicIpTime).TotalSeconds -lt 120) {
        return $Script:PublicIp
    }
    $ip = ''
    foreach ($url in 'https://api.ipify.org', 'https://ipv4.icanhazip.com') {
        try {
            $v = ([string](Invoke-RestMethod -UseBasicParsing -Uri $url -TimeoutSec 8)).Trim()
            if ($v -match '^\d{1,3}(\.\d{1,3}){3}$') { $ip = $v; break }
        } catch { }
    }
    $Script:PublicIp = $ip
    $Script:PublicIpTime = Get-Date
    return $ip
}

function Get-ServiceState {
    $svc = Get-Service -Name $Script:ServiceName -ErrorAction SilentlyContinue
    if (-not $svc) { return 'NotInstalled' }
    return [string]$svc.Status
}

function Test-LocalSite {
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1/' -Method Head -TimeoutSec 4
        return $r.StatusCode -eq 200
    } catch { return $false }
}

function Get-TaskInfo {
    $t = Get-ScheduledTask -TaskName $Script:TaskName -ErrorAction SilentlyContinue
    if (-not $t) { return [ordered]@{ exists = $false } }
    $i = $t | Get-ScheduledTaskInfo
    return [ordered]@{
        exists = $true
        lastRun = Format-Time $i.LastRunTime
        lastResult = [int64]$i.LastTaskResult
        nextRun = Format-Time $i.NextRunTime
    }
}

function Test-FirewallRules {
    return @(Get-NetFirewallRule -Group $Script:RuleGroup -ErrorAction SilentlyContinue).Count -gt 0
}

function Get-PortOwners {
    $owners = @()
    foreach ($c in @(Get-NetTCPConnection -State Listen -LocalPort 80, 443 -ErrorAction SilentlyContinue)) {
        $name = 'System (служба Windows http.sys)'
        if ($c.OwningProcess -ne 4) {
            $p = Get-Process -Id $c.OwningProcess -ErrorAction SilentlyContinue
            $name = [string]$p.ProcessName
        }
        if ($name -ne 'caddy') { $owners += "$name (порт $($c.LocalPort))" }
    }
    return @($owners | Select-Object -Unique)
}

# ------------------------------------------------------------------ UPnP
# Многие домашние роутеры умеют открывать порты по запросу программы (UPnP).
function Get-UpnpCollection {
    try { $nat = New-Object -ComObject HNetCfg.NATUPnP } catch { return $null }
    for ($i = 0; $i -lt 3; $i++) {
        $maps = $nat.StaticPortMappingCollection
        if ($null -ne $maps) { return , $maps }
        Start-Sleep -Seconds 1
    }
    return $null
}

function Get-UpnpState([string]$LanIp) {
    $state = [ordered]@{ available = $false; wanIp = ''; mapped = @(); conflicts = @() }
    $maps = Get-UpnpCollection
    if ($null -eq $maps) { return $state }
    $state.available = $true
    foreach ($m in $maps) {
        if (-not $state.wanIp -and $m.ExternalIPAddress) { $state.wanIp = [string]$m.ExternalIPAddress }
        if ($m.Protocol -eq 'TCP' -and (80, 443) -contains [int]$m.ExternalPort) {
            if ($m.InternalClient -eq $LanIp) { $state.mapped += [int]$m.ExternalPort }
            else { $state.conflicts += [ordered]@{ port = [int]$m.ExternalPort; client = [string]$m.InternalClient } }
        }
    }
    return $state
}

function Set-UpnpMappings([string]$LanIp) {
    $maps = Get-UpnpCollection
    if ($null -eq $maps) {
        throw 'Роутер не ответил по UPnP — эта функция в нём выключена или не поддерживается. Откройте порты вручную по инструкции ниже.'
    }
    # Сначала проверяем, что порты не заняты чужими правилами, и только потом меняем.
    foreach ($m in $maps) {
        if ($m.Protocol -eq 'TCP' -and (80, 443) -contains [int]$m.ExternalPort -and
            $m.InternalClient -ne $LanIp -and $m.Description -notlike 'HomeServer*') {
            throw "Порт $($m.ExternalPort) на роутере уже направлен на другое устройство ($($m.InternalClient)). Освободите его в настройках роутера."
        }
    }
    foreach ($port in 80, 443) {
        try { $maps.Remove($port, 'TCP') } catch { }
        $null = $maps.Add($port, 'TCP', $port, $LanIp, $true, "HomeServer $port")
    }
}

function Remove-UpnpMappings {
    $maps = Get-UpnpCollection
    if ($null -eq $maps) { return }
    foreach ($port in 80, 443) {
        foreach ($m in $maps) {
            if ($m.Protocol -eq 'TCP' -and [int]$m.ExternalPort -eq $port -and $m.Description -like 'HomeServer*') {
                try { $maps.Remove($port, 'TCP') } catch { }
            }
        }
    }
}

# ------------------------------------------------------------ DNS и HTTPS

function Clear-DnsCache { try { Clear-DnsClientCache -ErrorAction SilentlyContinue } catch { } }

function Get-DnsCheck([string[]]$Domains, [string]$PublicIp) {
    Clear-DnsCache
    $result = @()
    foreach ($d in $Domains) {
        $ips = @()
        try {
            $ips = @([System.Net.Dns]::GetHostAddresses($d) |
                Where-Object { $_.AddressFamily -eq 'InterNetwork' } | ForEach-Object { $_.IPAddressToString })
        } catch { }
        $result += [ordered]@{ name = $d; ips = $ips; ok = [bool]($PublicIp -and ($ips -contains $PublicIp)) }
    }
    return , $result
}

# Последнее сообщение Caddy о выпуске сертификата для домена (из журнала).
function Get-LastCertEvent([string]$Domain) {
    $file = Get-RootPath 'logs\caddy.log'
    if (-not (Test-Path -LiteralPath $file)) { return $null }
    $last = $null
    foreach ($line in @(Get-Content -LiteralPath $file -Tail 600 -Encoding UTF8)) {
        if ($line -notlike "*$Domain*") { continue }
        try { $j = $line | ConvertFrom-Json } catch { continue }
        if ($j.identifier -ne $Domain) { continue }
        if ($j.msg -like '*obtained successfully*') { $last = @{ ok = $true } }
        elseif ($j.level -eq 'error') { $last = @{ ok = $false; error = [string]$j.error } }
    }
    return $last
}

function Get-CertStatus([string[]]$Domains) {
    $dir = Get-RootPath 'data\certificates'
    $result = @()
    foreach ($d in $Domains) {
        $item = [ordered]@{ name = $d; state = 'pending'; until = ''; error = '' }
        $file = $null
        if (Test-Path -LiteralPath $dir) {
            $file = Get-ChildItem -LiteralPath $dir -Recurse -Filter "$d.crt" -ErrorAction SilentlyContinue | Select-Object -First 1
        }
        if ($file) {
            $item.state = 'ok'
            # Caddy хранит цепочку сертификатов в PEM; срок берём из первого (сертификат сайта).
            $m = [regex]::Match((Get-Content -LiteralPath $file.FullName -Raw),
                '-----BEGIN CERTIFICATE-----([A-Za-z0-9+/=\s]+?)-----END CERTIFICATE-----')
            if ($m.Success) {
                try {
                    $der = [Convert]::FromBase64String(($m.Groups[1].Value -replace '\s', ''))
                    $cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new($der)
                    $item.until = $cert.NotAfter.ToString('dd.MM.yyyy')
                } catch { }
            }
        } else {
            $ev = Get-LastCertEvent $d
            if ($ev -and -not $ev.ok) { $item.state = 'error'; $item.error = $ev.error }
        }
        $result += $item
    }
    return , $result
}

# Проверка доступности сайта из интернета через check-host.net:
# несколько серверов в разных странах открывают наш адрес.
function Invoke-ExternalCheck([string]$Url) {
    $headers = @{ Accept = 'application/json' }
    $start = Invoke-RestMethod -UseBasicParsing -Headers $headers -TimeoutSec 20 -Uri (
        'https://check-host.net/check-http?max_nodes=6&host=' + [uri]::EscapeDataString($Url))
    if (-not $start.request_id) { throw 'Сервис проверки check-host.net не ответил — попробуйте позже' }
    $deadline = (Get-Date).AddSeconds(30)
    do {
        Start-Sleep -Seconds 2
        $res = Invoke-RestMethod -UseBasicParsing -Headers $headers -TimeoutSec 20 -Uri (
            'https://check-host.net/check-result/' + $start.request_id)
        $pending = @($res.PSObject.Properties | Where-Object { $null -eq $_.Value }).Count
    } while ($pending -gt 0 -and (Get-Date) -lt $deadline)

    $nodes = @()
    foreach ($p in $start.nodes.PSObject.Properties) {
        $meta = @($p.Value)
        $node = [ordered]@{ cc = [string]$meta[0]; country = [string]$meta[1]; city = [string]$meta[2]
            ok = $false; text = 'нет ответа'; code = ''; seconds = '' }
        $v = $res.($p.Name)
        if ($null -ne $v -and $null -ne @($v)[0]) {
            # Ответ узла: [[1, 0.36, "OK", "200", "1.2.3.4"]] или [[0, 3.0, "Connection timed out", null, null]]
            $r = @(@($v)[0])
            $node.ok = ($r[0] -eq 1)
            if ($r.Count -gt 2 -and $r[2]) { $node.text = [string]$r[2] }
            if ($r.Count -gt 3 -and $r[3]) { $node.code = [string]$r[3] }
            if ($null -ne $r[1]) { $node.seconds = ([double]$r[1]).ToString('0.0', [Globalization.CultureInfo]::InvariantCulture) }
        }
        $nodes += $node
    }
    $okCount = @($nodes | Where-Object { $_.ok }).Count
    $report = [ordered]@{
        time = (Get-Date).ToString('dd.MM.yyyy HH:mm'); url = $Url
        ok = ($nodes.Count -gt 0 -and $okCount -eq $nodes.Count); okCount = $okCount; total = $nodes.Count
        nodes = $nodes
    }
    ConvertTo-Json -InputObject $report -Depth 5 | Set-Content -LiteralPath (Get-RootPath 'external-check.json') -Encoding UTF8
    return $report
}

function Get-LastExternalCheck {
    $file = Get-RootPath 'external-check.json'
    if (-not (Test-Path -LiteralPath $file)) { return $null }
    try { return Get-Content -LiteralPath $file -Raw -Encoding UTF8 | ConvertFrom-Json } catch { return $null }
}

# ============================================================== установка

function Protect-Folder {
    # Менять файлы в папке могут только администраторы и система: отсюда
    # запускаются служба и задача планировщика.
    $r = Invoke-Native 'icacls.exe' @($Script:Root, '/inheritance:r', '/grant:r',
        '*S-1-5-32-544:(OI)(CI)F', '*S-1-5-18:(OI)(CI)F', '*S-1-5-32-545:(OI)(CI)RX')
    if ($r.Code -ne 0) { throw 'не удалось настроить права доступа к папке' }
    Get-ChildItem -LiteralPath $Script:Root -Filter '*.ps1' | Unblock-File
}

function Initialize-Folders {
    foreach ($d in '', 'site', 'data', 'logs') {
        New-Item -ItemType Directory -Force -Path (Join-Path $Script:Root $d) | Out-Null
    }
    Copy-Item -LiteralPath (Join-Path $Script:SourceDir 'update.ps1') -Destination (Get-RootPath 'update.ps1') -Force
    Protect-Folder
    return $Script:Root
}

function Stop-HomeServerService {
    $svc = Get-Service -Name $Script:ServiceName -ErrorAction SilentlyContinue
    if ($svc -and $svc.Status -ne 'Stopped') {
        Stop-Service -Name $Script:ServiceName -Force
        $svc.WaitForStatus('Stopped', [TimeSpan]::FromSeconds(20))
    }
}

function Install-CaddyBinary {
    $exe = Get-RootPath 'caddy.exe'
    $arch = 'amd64'
    if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { $arch = 'arm64' }
    $tmp = Get-RootPath 'tmp-caddy'
    if (Test-Path -LiteralPath $tmp) { Remove-Item -LiteralPath $tmp -Recurse -Force }
    New-Item -ItemType Directory -Path $tmp | Out-Null
    try {
        try {
            $rel = Invoke-RestMethod -UseBasicParsing -TimeoutSec 30 -Uri 'https://api.github.com/repos/caddyserver/caddy/releases/latest'
        } catch {
            $rel = $null
        }
        if ($rel) {
            $version = [string]$rel.tag_name
            if ((Test-Path -LiteralPath $exe) -and ((Invoke-Native $exe @('version')).Output -join ' ') -match ([regex]::Escape($version) + '\b')) {
                return "Caddy $version (уже установлен)"
            }
            $asset = $rel.assets | Where-Object { $_.name -like "caddy_*_windows_$arch.zip" } | Select-Object -First 1
            $sums = $rel.assets | Where-Object { $_.name -like 'caddy_*_checksums.txt' } | Select-Object -First 1
            if (-not $asset -or -not $sums) { throw "в релизе Caddy $version нет сборки для Windows ($arch)" }
            $zip = Join-Path $tmp $asset.name
            Invoke-WebRequest -UseBasicParsing -Uri $asset.browser_download_url -OutFile $zip -TimeoutSec 600
            Invoke-WebRequest -UseBasicParsing -Uri $sums.browser_download_url -OutFile (Join-Path $tmp 'sums.txt') -TimeoutSec 60
            $line = Get-Content -LiteralPath (Join-Path $tmp 'sums.txt') |
                Where-Object { $_ -match ('\s' + [regex]::Escape($asset.name) + '$') } | Select-Object -First 1
            if (-not $line) { throw 'нет контрольной суммы для архива Caddy' }
            $expected = ($line -split '\s+')[0].ToLowerInvariant()
            $actual = (Get-FileHash -LiteralPath $zip -Algorithm SHA512).Hash.ToLowerInvariant()
            if ($expected -ne $actual) { throw 'архив Caddy повреждён (не совпала контрольная сумма) — попробуйте ещё раз' }
            Expand-Archive -LiteralPath $zip -DestinationPath $tmp -Force
            $new = Join-Path $tmp 'caddy.exe'
        } else {
            if (Test-Path -LiteralPath $exe) { return 'GitHub недоступен — оставляю установленную версию Caddy' }
            # Запасной путь — официальный сайт Caddy.
            $new = Join-Path $tmp 'caddy.exe'
            Invoke-WebRequest -UseBasicParsing -OutFile $new -TimeoutSec 600 -Uri "https://caddyserver.com/api/download?os=windows&arch=$arch"
            $version = 'с caddyserver.com'
        }
        Stop-HomeServerService
        Copy-Item -LiteralPath $new -Destination $exe -Force
        return "Caddy $version"
    } finally {
        Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Get-DefaultBranch([string]$Repo) {
    $resp = Invoke-WebRequest -UseBasicParsing -TimeoutSec 60 -Uri ('https://github.com/{0}.git/info/refs?service=git-upload-pack' -f $Repo)
    if ($resp.Content -is [byte[]]) { $text = [Text.Encoding]::ASCII.GetString($resp.Content) } else { $text = [string]$resp.Content }
    $m = [regex]::Match($text, 'symref=HEAD:refs/heads/([^\s\x00]+)')
    if (-not $m.Success) { throw "не удалось узнать основную ветку репозитория $Repo" }
    return $m.Groups[1].Value
}

function Invoke-UpdateScript([switch]$Force, [switch]$DdnsOnly) {
    $upd = Get-RootPath 'update.ps1'
    if (-not (Test-Path -LiteralPath $upd)) { throw 'Сервер ещё не установлен' }
    $global:LASTEXITCODE = 0
    $out = @(& $upd -Force:$Force -DdnsOnly:$DdnsOnly 2>&1 | ForEach-Object { "$_" } | Where-Object { $_ })
    return [ordered]@{ ok = ($LASTEXITCODE -eq 0); message = ($out -join "`n") }
}

function Install-Site {
    $cfg = Get-Config
    if (-not $cfg.Branch) {
        $cfg.Branch = Get-DefaultBranch $cfg.Repo
        Save-Config $cfg
    }
    $r = Invoke-UpdateScript -Force
    if (-not $r.ok) { throw $r.message }
    return $r.message
}

function Test-CaddyConfig([string]$File) {
    $r = Invoke-Native (Get-RootPath 'caddy.exe') @('validate', '--config', $File, '--adapter', 'caddyfile')
    if ($r.Code -ne 0) { throw ('Caddy не принял настройки: ' + (($r.Output | Select-Object -Last 2) -join ' ')) }
}

function Write-Caddyfile {
    $domains = @(ConvertTo-DomainList (Get-Config).Domain)
    $file = Get-RootPath 'Caddyfile'
    [IO.File]::WriteAllText("$file.new", (New-CaddyfileText $domains), (New-Object Text.UTF8Encoding($false)))
    try { Test-CaddyConfig "$file.new" } catch { Remove-Item -LiteralPath "$file.new" -Force; throw }
    Move-Item -LiteralPath "$file.new" -Destination $file -Force
    if ($domains.Count -gt 0) { return 'Адрес в интернете: ' + ($domains -join ', ') }
    return 'Сайт будет доступен в домашней сети'
}

function Restart-HomeServerService {
    $owners = @(Get-PortOwners)
    if ($owners.Count -gt 0) {
        throw ('Порт занят другой программой: ' + ($owners -join ', ') +
            '. Закройте или отключите её и нажмите «Установить» ещё раз.')
    }
    $svc = Get-Service -Name $Script:ServiceName
    try {
        if ($svc.Status -eq 'Running') { Restart-Service -Name $Script:ServiceName -Force }
        else { Start-Service -Name $Script:ServiceName }
        (Get-Service -Name $Script:ServiceName).WaitForStatus('Running', [TimeSpan]::FromSeconds(20))
    } catch {
        throw 'Служба веб-сервера не запустилась — подробности в разделе «Журнал».'
    }
}

function Register-CaddyService {
    $bin = '"{0}" run --config "{1}"' -f (Get-RootPath 'caddy.exe'), (Get-RootPath 'Caddyfile')
    $svc = Get-Service -Name $Script:ServiceName -ErrorAction SilentlyContinue
    if (-not $svc) {
        New-Service -Name $Script:ServiceName -BinaryPathName $bin -StartupType Automatic `
            -DisplayName 'Домашний сервер (сайт)' `
            -Description 'Веб-сервер Caddy: раздаёт сайт из C:\HomeServer\site. Управление — панель «Домашний сервер».' | Out-Null
    } else {
        Set-ItemProperty -LiteralPath "HKLM:\SYSTEM\CurrentControlSet\Services\$($Script:ServiceName)" -Name ImagePath -Value $bin
        Set-Service -Name $Script:ServiceName -StartupType Automatic
    }
    # Если служба упадёт — Windows перезапустит её через 5, 10 и 30 секунд.
    Invoke-Native 'sc.exe' @('failure', $Script:ServiceName, 'reset=', '86400',
        'actions=', 'restart/5000/restart/10000/restart/30000') | Out-Null
    Restart-HomeServerService
    return 'Служба «Домашний сервер (сайт)» запускается вместе с Windows'
}

function Register-UpdateTask {
    $ps = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    $action = New-ScheduledTaskAction -Execute $ps -Argument (
        '-NoProfile -NonInteractive -ExecutionPolicy Bypass -WindowStyle Hidden -File "{0}"' -f (Get-RootPath 'update.ps1'))
    $triggers = @(
        (New-ScheduledTaskTrigger -AtStartup),
        (New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Minutes 5))
    )
    $principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
    $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable `
        -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Minutes 15)
    Register-ScheduledTask -TaskName $Script:TaskName -Action $action -Trigger $triggers -Principal $principal `
        -Settings $settings -Force -Description 'Каждые 5 минут обновляет сайт из GitHub (панель «Домашний сервер»).' | Out-Null
    return 'Сайт будет обновляться из GitHub каждые 5 минут'
}

function Set-FirewallRules {
    Remove-FirewallRules
    $exe = Get-RootPath 'caddy.exe'
    New-NetFirewallRule -Group $Script:RuleGroup -DisplayName 'Домашний сервер: сайт (HTTP, HTTPS)' -Direction Inbound `
        -Action Allow -Protocol TCP -LocalPort 80, 443 -Program $exe -Profile Any | Out-Null
    New-NetFirewallRule -Group $Script:RuleGroup -DisplayName 'Домашний сервер: сайт (HTTP/3)' -Direction Inbound `
        -Action Allow -Protocol UDP -LocalPort 443 -Program $exe -Profile Any | Out-Null
    return 'Входящие подключения к портам 80 и 443 разрешены'
}

function Remove-FirewallRules {
    Get-NetFirewallRule -Group $Script:RuleGroup -ErrorAction SilentlyContinue | Remove-NetFirewallRule
}

function Set-PowerSettings {
    foreach ($c in '/change standby-timeout-ac 0', '/change standby-timeout-dc 0', '/change hibernate-timeout-ac 0',
        '/setacvalueindex SCHEME_CURRENT SUB_BUTTONS LIDACTION 0',
        '/setdcvalueindex SCHEME_CURRENT SUB_BUTTONS LIDACTION 0',
        # Wi-Fi адаптер: «максимальная производительность» вместо энергосбережения (есть не везде).
        '/setacvalueindex SCHEME_CURRENT 19cbb8fa-5279-450e-9fac-8a3d5fedd0c1 12bbebe6-58d6-4636-95bb-3217ef867c1a 0',
        '/setactive SCHEME_CURRENT') {
        Invoke-Native 'powercfg.exe' ($c -split ' ') | Out-Null
    }
    $cfg = Get-Config
    $cfg.PowerSet = $true
    Save-Config $cfg
    return 'Сон отключён, закрытие крышки ничего не делает'
}

function Test-InstalledSite {
    for ($i = 0; $i -lt 10; $i++) {
        if (Test-LocalSite) { return 'Сайт открывается: http://127.0.0.1' }
        Start-Sleep -Seconds 1
    }
    throw 'Веб-сервер запущен, но сайт не открывается — подробности в разделе «Журнал».'
}

# Шаги выполняются по очереди; о каждом панель узнаёт сразу (потоковый ответ).
function Invoke-Steps([object[]]$Steps, [switch]$ContinueOnError) {
    Send-Progress @{ plan = @($Steps | ForEach-Object { @{ id = $_.id; title = $_.title } }) }
    $allOk = $true
    foreach ($s in $Steps) {
        Send-Progress @{ id = $s.id; state = 'run' }
        try {
            $note = [string](@(& $s.action) | Select-Object -Last 1)
            Send-Progress @{ id = $s.id; state = 'ok'; note = $note }
        } catch {
            $allOk = $false
            Write-PanelLog "$($s.id): $($_.Exception.Message)"
            Send-Progress @{ id = $s.id; state = 'error'; note = $_.Exception.Message }
            if (-not $ContinueOnError) { break }
        }
    }
    Send-Progress @{ done = $true; ok = $allOk }
}

function Install-HomeServer {
    Invoke-Steps @(
        @{ id = 'folders'; title = 'Готовлю папку C:\HomeServer'; action = { Initialize-Folders } },
        @{ id = 'caddy'; title = 'Скачиваю веб-сервер Caddy'; action = { Install-CaddyBinary } },
        @{ id = 'site'; title = 'Скачиваю сайт из GitHub'; action = { Install-Site } },
        @{ id = 'config'; title = 'Настраиваю веб-сервер'; action = { Write-Caddyfile } },
        @{ id = 'service'; title = 'Запускаю сервер и включаю автозапуск'; action = { Register-CaddyService } },
        @{ id = 'task'; title = 'Включаю автообновление сайта'; action = { Register-UpdateTask } },
        @{ id = 'firewall'; title = 'Открываю порты 80 и 443 в брандмауэре'; action = { Set-FirewallRules } },
        @{ id = 'power'; title = 'Запрещаю компьютеру засыпать'; action = { Set-PowerSettings } },
        @{ id = 'check'; title = 'Проверяю, что сайт открывается'; action = { Test-InstalledSite } }
    )
}

function Remove-HomeServerService {
    Stop-HomeServerService
    if (Get-Service -Name $Script:ServiceName -ErrorAction SilentlyContinue) {
        Invoke-Native 'sc.exe' @('delete', $Script:ServiceName) | Out-Null
    }
    return 'Служба удалена'
}

function Unregister-UpdateTask {
    if (Get-ScheduledTask -TaskName $Script:TaskName -ErrorAction SilentlyContinue) {
        Unregister-ScheduledTask -TaskName $Script:TaskName -Confirm:$false
    }
    return 'Задача удалена'
}

function Uninstall-HomeServer {
    $cfg = Get-Config
    Invoke-Steps -ContinueOnError @(
        @{ id = 'service'; title = 'Останавливаю и удаляю службу веб-сервера'; action = { Remove-HomeServerService } },
        @{ id = 'task'; title = 'Удаляю задачу автообновления'; action = { Unregister-UpdateTask } },
        @{ id = 'firewall'; title = 'Удаляю правила брандмауэра'; action = { Remove-FirewallRules; 'Правила удалены' } },
        @{ id = 'upnp'; title = 'Закрываю порты на роутере (UPnP)'; action = {
                if ($cfg.Upnp) { Remove-UpnpMappings; 'Правила UPnP удалены' } else { 'Не требуется' } } },
        @{ id = 'files'; title = 'Удаляю папку C:\HomeServer'; action = {
                if (Test-Path -LiteralPath $Script:Root) { Remove-Item -LiteralPath $Script:Root -Recurse -Force }
                'Папка удалена' } },
        @{ id = 'power'; title = 'Настройки сна'; action = {
                'Оставлены как есть — вернуть можно в «Параметры → Система → Питание»' } }
    )
}

# ======================================================== команды из панели

function Get-Status {
    $cfg = Get-Config
    $domains = @(ConvertTo-DomainList $cfg.Domain)
    $service = Get-ServiceState
    $shaFile = Get-RootPath 'site-commit.txt'
    $sha = ''
    $updated = ''
    if (Test-Path -LiteralPath $shaFile) {
        $sha = (Get-Content -LiteralPath $shaFile -Raw).Trim()
        $updated = Format-Time (Get-Item -LiteralPath $shaFile).LastWriteTime
    }
    $ddns = ''
    if ($cfg.DuckDnsToken) { $ddns = 'duckdns' } elseif ($cfg.DdnsUrl) { $ddns = 'custom' }
    $duckName = ''
    foreach ($d in $domains) { if ($d -like '*.duckdns.org') { $duckName = $d -replace '\.duckdns\.org$', ''; break } }
    $localOk = $false
    if ($service -eq 'Running') { $localOk = Test-LocalSite }
    # Кириллические домены показываем в панели по-русски (кутузовский12.рф), а не в punycode.
    $idn = New-Object System.Globalization.IdnMapping
    $labels = [ordered]@{}
    foreach ($d in $domains) { try { $labels[$d] = $idn.GetUnicode($d) } catch { $labels[$d] = $d } }

    return [ordered]@{
        version = $Script:Version
        installed = ($service -ne 'NotInstalled' -and (Test-Path -LiteralPath (Get-RootPath 'caddy.exe')))
        service = $service
        localOk = $localOk
        root = $Script:Root
        site = [ordered]@{ sha = $sha; updated = $updated; repo = $cfg.Repo; branch = $cfg.Branch }
        lan = Get-LanInfo
        vpn = @(Get-VpnAdapters)
        domains = $domains
        domainLabels = $labels
        ddns = $ddns
        duckName = $duckName
        ipType = [string]$cfg.IpType
        upnp = [bool]$cfg.Upnp
        power = [bool]$cfg.PowerSet
        firewall = Test-FirewallRules
        task = Get-TaskInfo
        external = Get-LastExternalCheck
        publicIp = [string]$Script:PublicIp
    }
}

function Get-NetworkStatus {
    $cfg = Get-Config
    $domains = @(ConvertTo-DomainList $cfg.Domain)
    $publicIp = Get-PublicIp -Refresh
    $lan = Get-LanInfo
    $upnp = Get-UpnpState $lan.ip
    $ipType = [string]$cfg.IpType
    $source = 'user'
    if ($upnp.wanIp) {
        $source = 'upnp'
        if ($upnp.wanIp -eq $publicIp) { $ipType = 'white' } else { $ipType = 'grey' }
    }
    if (-not $ipType) { $source = '' }
    return [ordered]@{
        publicIp = $publicIp
        upnp = $upnp
        ipType = $ipType
        ipTypeSource = $source
        dns = Get-DnsCheck $domains $publicIp
        certs = Get-CertStatus $domains
    }
}

function Set-Domain($Body) {
    $cfg = Get-Config
    switch ([string]$Body.mode) {
        'duckdns' {
            $name = ([string]$Body.name).Trim().ToLowerInvariant() -replace '\.duckdns\.org$', ''
            if ($name -notmatch '^[a-z0-9]([a-z0-9-]*[a-z0-9])?$') {
                throw 'Имя на DuckDNS может содержать только латинские буквы, цифры и дефис'
            }
            $token = ([string]$Body.token).Trim()
            if (-not $token -and $cfg.DuckDnsToken) { $token = [string]$cfg.DuckDnsToken }
            if ($token -notmatch '^[A-Za-z0-9-]{20,}$') {
                throw 'Токен DuckDNS выглядит неверно — скопируйте его со страницы duckdns.org целиком'
            }
            $cfg.Domain = "$name.duckdns.org"
            $cfg.DuckDnsToken = $token
            $cfg.DdnsUrl = "https://www.duckdns.org/update?domains=$name&token=$token&ip="
        }
        'custom' {
            $list = @(ConvertTo-DomainList ([string]$Body.domain))
            if ($list.Count -eq 0) { throw 'Введите домен, например mysite.ru' }
            $url = ([string]$Body.ddnsUrl).Trim()
            if ($url -and $url -notmatch '^https://') { throw 'Ссылка обновления DDNS должна начинаться с https://' }
            if (-not $url -and $Body.keepDdns -and -not $cfg.DuckDnsToken) { $url = [string]$cfg.DdnsUrl }
            $cfg.Domain = $list -join ' '
            $cfg.DuckDnsToken = ''
            $cfg.DdnsUrl = $url
        }
        'none' {
            $cfg.Domain = ''; $cfg.DuckDnsToken = ''; $cfg.DdnsUrl = ''
        }
        default { throw 'Неизвестный режим' }
    }
    Save-Config $cfg
    Remove-Item -LiteralPath (Get-RootPath 'ddns-state.json') -ErrorAction SilentlyContinue

    $ddns = ''
    if (Test-Path -LiteralPath (Get-RootPath 'caddy.exe')) {
        Write-Caddyfile | Out-Null
        if ((Get-ServiceState) -ne 'NotInstalled') { Restart-HomeServerService }
        if ($cfg.DdnsUrl) { $ddns = (Invoke-UpdateScript -DdnsOnly).message }
    }
    return [ordered]@{ ok = $true; domains = @(ConvertTo-DomainList $cfg.Domain); ddns = $ddns }
}

function Invoke-ServiceAction([string]$Action) {
    if ((Get-ServiceState) -eq 'NotInstalled') { throw 'Сервер ещё не установлен' }
    switch ($Action) {
        'start' { Restart-HomeServerService }
        'restart' { Restart-HomeServerService }
        'stop' { Stop-HomeServerService }
        default { throw 'Неизвестная команда' }
    }
    return [ordered]@{ ok = $true; service = Get-ServiceState }
}

function Invoke-RouterAuto {
    $lan = Get-LanInfo
    if (-not $lan.ip) { throw 'Не удалось определить адрес компьютера в домашней сети' }
    Set-UpnpMappings $lan.ip
    $cfg = Get-Config
    $cfg.Upnp = $true
    $cfg.UpnpIp = $lan.ip
    Save-Config $cfg
    return [ordered]@{ ok = $true; upnp = Get-UpnpState $lan.ip }
}

function Set-IpType([string]$Value) {
    if (@('white', 'grey', '') -notcontains $Value) { throw 'Неизвестное значение' }
    $cfg = Get-Config
    $cfg.IpType = $Value
    Save-Config $cfg
    return [ordered]@{ ok = $true }
}

function Start-ExternalCheck {
    $domains = @(ConvertTo-DomainList (Get-Config).Domain)
    if ($domains.Count -gt 0) { $url = "https://$($domains[0])" }
    else {
        $ip = Get-PublicIp
        if (-not $ip) { throw 'Не удалось узнать внешний IP — проверьте подключение к интернету' }
        $url = "http://$ip"
    }
    return Invoke-ExternalCheck $url
}

function Format-CaddyLogLine([string]$Line) {
    try { $j = $Line | ConvertFrom-Json } catch { return $Line }
    if ($null -eq $j.ts) { return $Line }
    $time = [DateTimeOffset]::FromUnixTimeMilliseconds([int64]([double]$j.ts * 1000)).ToLocalTime().ToString('dd.MM HH:mm:ss')
    $text = '{0}  {1}  {2}' -f $time, ([string]$j.level).ToUpperInvariant(), $j.msg
    if ($j.identifier) { $text += " · $($j.identifier)" }
    if ($j.error) { $text += " — $($j.error)" }
    return $text
}

function Get-LogLines([string]$Name) {
    if ($Name -eq 'update') { $file = Get-RootPath 'logs\update.log' } else { $file = Get-RootPath 'logs\caddy.log' }
    $lines = @()
    if (Test-Path -LiteralPath $file) {
        $lines = @(Get-Content -LiteralPath $file -Tail 300 -Encoding UTF8)
        if ($Name -ne 'update') { $lines = @($lines | ForEach-Object { Format-CaddyLogLine $_ }) }
    }
    return [ordered]@{ path = $file; lines = $lines }
}

function Open-Path([string]$What) {
    $path = $Script:Root
    if ($What -eq 'logs') { $path = Get-RootPath 'logs' }
    if (-not (Test-Path -LiteralPath $path)) { throw 'Папка ещё не создана — сначала установите сервер' }
    Start-Process -FilePath 'explorer.exe' -ArgumentList $path
    return [ordered]@{ ok = $true }
}

function Invoke-Api([string]$Method, [string]$Path, $Body, $Request) {
    switch ("$Method $Path") {
        'POST /api/ping' { return [ordered]@{ ok = $true; version = $Script:Version } }
        'GET /api/status' { return Get-Status }
        'GET /api/network' { return Get-NetworkStatus }
        'POST /api/domain' { return Set-Domain $Body }
        'POST /api/service' { return Invoke-ServiceAction ([string]$Body.action) }
        'POST /api/update' { return Invoke-UpdateScript }
        'POST /api/router/auto' { return Invoke-RouterAuto }
        'POST /api/ip-type' { return Set-IpType ([string]$Body.value) }
        'POST /api/external-check' { return Start-ExternalCheck }
        'GET /api/logs' { return Get-LogLines ([string]$Request.QueryString['name']) }
        'POST /api/open' { return Open-Path ([string]$Body.what) }
        'POST /api/bye' {
            # Окно закрыли (или перезагрузили — тогда следующий запрос отменит выход).
            $Script:QuitAt = (Get-Date).AddSeconds(5)
            return [ordered]@{ ok = $true }
        }
    }
    throw (New-Object System.IO.FileNotFoundException 'Неизвестная команда')
}

# ================================================================ веб-сервер

function Send-Response($Res, [int]$Code, [string]$Type, [string]$Text) {
    $bytes = [Text.Encoding]::UTF8.GetBytes($Text)
    $Res.StatusCode = $Code
    $Res.ContentType = $Type
    $Res.AddHeader('Cache-Control', 'no-store')
    $Res.ContentLength64 = $bytes.Length
    $Res.OutputStream.Write($bytes, 0, $bytes.Length)
    $Res.OutputStream.Close()
}

function Send-Json($Res, [int]$Code, $Data) {
    Send-Response $Res $Code 'application/json; charset=utf-8' (ConvertTo-Json -InputObject $Data -Depth 8 -Compress)
}

function Send-Progress($Data) {
    if (-not $Script:Stream) { return }
    $bytes = [Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject $Data -Depth 5 -Compress) + "`n")
    $Script:Stream.Write($bytes, 0, $bytes.Length)
    $Script:Stream.Flush()
}

function Invoke-Streamed($Res, [scriptblock]$Action) {
    $Res.StatusCode = 200
    $Res.ContentType = 'application/x-ndjson; charset=utf-8'
    $Res.AddHeader('Cache-Control', 'no-store')
    $Res.SendChunked = $true
    $Script:Stream = $Res.OutputStream
    try {
        & $Action
    } catch {
        Write-PanelLog "stream: $($_.Exception.Message)"
        Send-Progress @{ done = $true; ok = $false; error = $_.Exception.Message }
    } finally {
        $Script:Stream = $null
        try { $Res.OutputStream.Close() } catch { }
    }
}

function Invoke-PanelRequest($Ctx) {
    $req = $Ctx.Request
    $res = $Ctx.Response
    try {
        # Только этот компьютер и только адрес панели (защита от чужих страниц в браузере).
        $remote = $req.RemoteEndPoint
        if (-not $remote -or -not $remote.Address -or -not [Net.IPAddress]::IsLoopback($remote.Address) -or
            $req.UserHostName -ne $Script:HostHeader) {
            Send-Response $res 403 'text/plain' 'Forbidden'
            return
        }
        $path = $req.Url.AbsolutePath
        if ($req.HttpMethod -eq 'GET' -and $path -eq '/') {
            if ($req.QueryString['t'] -ne $Script:Token) { Send-Response $res 403 'text/plain' 'Forbidden'; return }
            $html = [IO.File]::ReadAllText((Join-Path $Script:SourceDir 'panel.html'), [Text.Encoding]::UTF8)
            Send-Response $res 200 'text/html; charset=utf-8' $html
            return
        }
        if (-not $path.StartsWith('/api/')) { Send-Response $res 404 'text/plain' 'Not found'; return }

        $text = ''
        if ($req.HasEntityBody) {
            $reader = New-Object IO.StreamReader($req.InputStream, [Text.Encoding]::UTF8)
            $text = $reader.ReadToEnd()
            $reader.Close()
        }
        $token = $req.Headers['X-Token']
        if ($path -eq '/api/bye' -and -not $token) { $token = $text.Trim() }  # navigator.sendBeacon
        if ($token -ne $Script:Token) { Send-Response $res 403 'text/plain' 'Forbidden'; return }

        $Script:LastSeen = Get-Date
        if ($path -ne '/api/bye') { $Script:QuitAt = $null }
        $body = $null
        if ($text -and $path -ne '/api/bye') { $body = $text | ConvertFrom-Json }

        if ($path -eq '/api/install') { Invoke-Streamed $res { Install-HomeServer }; return }
        if ($path -eq '/api/uninstall') { Invoke-Streamed $res { Uninstall-HomeServer }; return }
        Send-Json $res 200 (Invoke-Api $req.HttpMethod $path $body $req)
    } catch {
        Write-PanelLog ('{0} {1}: {2}' -f $req.HttpMethod, $req.Url.AbsolutePath, $_.Exception.Message)
        $code = 500
        if ($_.Exception -is [System.IO.FileNotFoundException]) { $code = 404 }
        try { Send-Json $res $code @{ error = $_.Exception.Message } } catch { }
    }
}

function Start-PanelServer([int]$Port, [string]$Token) {
    $Script:Token = $Token
    $Script:HostHeader = "127.0.0.1:$Port"
    $Script:LastSeen = Get-Date
    $Script:QuitAt = $null
    $listener = New-Object System.Net.HttpListener
    $listener.Prefixes.Add("http://127.0.0.1:$Port/")
    $listener.Start()
    try {
        while ($true) {
            $pending = $listener.BeginGetContext($null, $null)
            while (-not $pending.AsyncWaitHandle.WaitOne(1000)) {
                if ($Script:QuitAt -and (Get-Date) -ge $Script:QuitAt) { return }
                # Окно панели присылает сигнал раз в 20 секунд; нет сигнала 15 минут — выходим.
                if (((Get-Date) - $Script:LastSeen).TotalMinutes -ge 15) { return }
            }
            Invoke-PanelRequest ($listener.EndGetContext($pending))
        }
    } finally {
        $listener.Close()
    }
}

# ================================================================== запуск

function Test-Admin {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    return (New-Object Security.Principal.WindowsPrincipal($id)).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Show-Message([string]$Text, [string]$Icon = 'Information') {
    Add-Type -AssemblyName System.Windows.Forms
    [void][System.Windows.Forms.MessageBox]::Show($Text, 'Домашний сервер', 'OK', $Icon)
}

function Get-FreePort {
    $l = New-Object System.Net.Sockets.TcpListener([Net.IPAddress]::Loopback, 0)
    $l.Start()
    $p = $l.LocalEndpoint.Port
    $l.Stop()
    return $p
}

# Окно панели — Microsoft Edge в режиме приложения (без адресной строки).
# Его запускает обычный, не повышенный процесс: браузеру права администратора не нужны.
function Open-AppWindow([string]$Url) {
    $edge = @("${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
        "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe") |
        Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -First 1
    if ($edge) { Start-Process -FilePath $edge -ArgumentList "--app=$Url", '--window-size=1280,880', '--no-first-run' }
    else { Start-Process $Url }
}

function Start-Launcher {
    Write-Host ''
    Write-Host '  Домашний сервер — запускаю панель управления…'
    Write-Host '  Windows спросит разрешение на изменения — нажмите «Да».'
    $port = Get-FreePort
    $token = [guid]::NewGuid().ToString('N')
    $ps = Join-Path $PSHOME 'powershell.exe'
    if (-not (Test-Path -LiteralPath $ps)) { $ps = (Get-Process -Id $PID).Path }
    $argLine = '-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "{0}" -Backend -Port {1} -Token {2}' -f $PSCommandPath, $port, $token
    try {
        Start-Process -FilePath $ps -ArgumentList $argLine -Verb RunAs -WindowStyle Hidden | Out-Null
    } catch {
        Show-Message ("Без прав администратора панель не сможет настроить сервер.`n`n" +
            'Запустите home-server.cmd ещё раз и нажмите «Да» в окне Windows.') 'Warning'
        return
    }
    $ready = $false
    for ($i = 0; $i -lt 60 -and -not $ready; $i++) {
        Start-Sleep -Milliseconds 500
        try {
            $r = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "http://127.0.0.1:$port/api/ping" `
                -Headers @{ 'X-Token' = $token } -TimeoutSec 2
            $ready = $r.StatusCode -eq 200
        } catch { }
    }
    if (-not $ready) {
        Show-Message "Панель не запустилась.`nПодробности в журнале:`n$($Script:PanelLog)" 'Error'
        return
    }
    Open-AppWindow "http://127.0.0.1:$port/?t=$token"
}

function Start-Backend {
    if (-not (Test-Admin) -or $Port -le 0 -or -not $Token) {
        Write-PanelLog 'Движок панели запущен без прав администратора или без ключа — выхожу'
        exit 1
    }
    Write-PanelLog "Панель $($Script:Version) запущена на порту $Port"
    try {
        Start-PanelServer -Port $Port -Token $Token
    } catch {
        Write-PanelLog "Ошибка движка: $($_.Exception.Message)"
        exit 1
    }
    Write-PanelLog 'Панель закрыта'
}

# При подключении через «.» (в тестах) только объявляем функции.
if ($MyInvocation.InvocationName -ne '.') {
    if ($Backend) { Start-Backend } else { Start-Launcher }
}
