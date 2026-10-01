<#
  update.ps1 — часть «Домашнего сервера» для Windows.

  Каждые 5 минут (задача планировщика «HomeServer Update») скачивает из GitHub
  свежую версию сайта, если она изменилась, и — если настроен динамический DNS —
  сообщает ему текущий внешний IP-адрес. Кнопка «Обновить сейчас» в панели
  запускает этот же файл.
#>
param(
    [switch]$Force,   # скачать сайт заново, даже если версия не изменилась
    [switch]$DdnsOnly # только обновить динамический DNS
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
try {
    [Net.ServicePointManager]::SecurityProtocol =
        [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
} catch { }

$Root = $PSScriptRoot
$LogFile = Join-Path $Root 'logs\update.log'
$Config = Get-Content -LiteralPath (Join-Path $Root 'config.json') -Raw -Encoding UTF8 | ConvertFrom-Json

function Add-UpdateLog([string]$Message) {
    $line = '{0}  {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message
    Add-Content -LiteralPath $LogFile -Value $line -Encoding UTF8
    Write-Output $Message
}

function Limit-Log {
    if ((Test-Path -LiteralPath $LogFile) -and (Get-Item -LiteralPath $LogFile).Length -gt 256KB) {
        $tail = Get-Content -LiteralPath $LogFile -Encoding UTF8 -Tail 500
        Set-Content -LiteralPath $LogFile -Value $tail -Encoding UTF8
    }
}

function Get-PublicIPv4 {
    foreach ($url in 'https://api.ipify.org', 'https://ipv4.icanhazip.com') {
        try {
            $ip = ([string](Invoke-RestMethod -UseBasicParsing -Uri $url -TimeoutSec 15)).Trim()
            if ($ip -match '^\d{1,3}(\.\d{1,3}){3}$') { return $ip }
        } catch { }
    }
    return ''
}

# Если порты на роутере открывали автоматически (UPnP), проверяем, что правила
# на месте: после перезагрузки некоторые роутеры их забывают.
function Update-Upnp {
    if (-not $Config.Upnp -or -not $Config.UpnpIp -or $env:OS -ne 'Windows_NT') { return }
    try {
        $maps = (New-Object -ComObject HNetCfg.NATUPnP).StaticPortMappingCollection
        if ($null -eq $maps) { return }
        foreach ($port in 80, 443) {
            $found = $false
            foreach ($m in $maps) {
                if ($m.ExternalPort -eq $port -and $m.Protocol -eq 'TCP' -and $m.InternalClient -eq $Config.UpnpIp) { $found = $true }
            }
            if (-not $found) {
                $null = $maps.Add($port, 'TCP', $port, $Config.UpnpIp, $true, "HomeServer $port")
                Add-UpdateLog "UPnP: правило для порта $port восстановлено"
            }
        }
    } catch { }
}

# Динамический DNS: обращаемся к сервису, когда внешний IP сменился,
# и на всякий случай раз в час.
function Update-Ddns {
    if (-not $Config.DdnsUrl) { return }
    $stateFile = Join-Path $Root 'ddns-state.json'
    $state = $null
    if (Test-Path -LiteralPath $stateFile) {
        try { $state = Get-Content -LiteralPath $stateFile -Raw -Encoding UTF8 | ConvertFrom-Json } catch { }
    }
    $ip = Get-PublicIPv4
    if ($state -and $ip -and $state.Ip -eq $ip -and $state.Url -eq $Config.DdnsUrl -and
        ((Get-Date) - [datetime]$state.Time).TotalMinutes -lt 60) {
        return
    }

    $url = [string]$Config.DdnsUrl
    # DuckDNS сам определяет только IPv4 подключения — подставляем адрес явно,
    # чтобы не зависеть от того, пойдёт ли запрос по IPv6.
    if ($ip -and $url -match 'duckdns\.org/update' -and $url -match '[?&]ip=$') { $url += $ip }

    try {
        $resp = ([string](Invoke-RestMethod -UseBasicParsing -Uri $url -TimeoutSec 30)).Trim()
    } catch {
        Add-UpdateLog "DDNS: нет связи с сервисом ($($_.Exception.Message))"
        return
    }
    if ($resp -match '^(KO|bad|nohost|notfqdn|abuse|911|dnserr)') {
        Add-UpdateLog "DDNS: сервис ответил «$resp» — проверьте имя и токен"
        Remove-Item -LiteralPath $stateFile -ErrorAction SilentlyContinue
        return
    }
    if (-not $state -or $state.Ip -ne $ip) { Add-UpdateLog "DDNS: адрес обновлён ($ip)" }
    @{ Ip = $ip; Url = $Config.DdnsUrl; Time = (Get-Date).ToString('o') } |
        ConvertTo-Json | Set-Content -LiteralPath $stateFile -Encoding UTF8
}

# Последняя версия (коммит) ветки — без git и без лимитов GitHub API:
# тот же список веток, который запрашивает команда git clone.
function Get-LatestCommit {
    $uri = 'https://github.com/{0}.git/info/refs?service=git-upload-pack' -f $Config.Repo
    $resp = Invoke-WebRequest -UseBasicParsing -Uri $uri -TimeoutSec 60
    if ($resp.Content -is [byte[]]) { $text = [Text.Encoding]::ASCII.GetString($resp.Content) }
    else { $text = [string]$resp.Content }
    $m = [regex]::Match($text, '([0-9a-f]{40}) refs/heads/' + [regex]::Escape($Config.Branch) + '[\x00\n]')
    if (-not $m.Success) { throw "в репозитории $($Config.Repo) нет ветки $($Config.Branch)" }
    return $m.Groups[1].Value
}

function Sync-Folder([string]$From, [string]$To) {
    if ($env:OS -eq 'Windows_NT') {
        # /MIR — сделать папку сайта точной копией архива (лишние файлы удаляются).
        robocopy $From $To /MIR /R:5 /W:2 /NFL /NDL /NJH /NJS /NP | Out-Null
        if ($LASTEXITCODE -ge 8) { throw "robocopy завершился с кодом $LASTEXITCODE" }
    } else {
        if (Test-Path -LiteralPath $To) { Remove-Item -LiteralPath $To -Recurse -Force }
        Copy-Item -LiteralPath $From -Destination $To -Recurse
    }
}

function Update-Site {
    $sha = Get-LatestCommit
    $shaFile = Join-Path $Root 'site-commit.txt'
    $siteDir = Join-Path $Root 'site'
    $current = ''
    if (Test-Path -LiteralPath $shaFile) { $current = (Get-Content -LiteralPath $shaFile -Raw).Trim() }
    if (-not $Force -and $sha -eq $current -and (Test-Path -LiteralPath (Join-Path $siteDir 'index.html'))) {
        Write-Output "Обновлений нет — на сервере последняя версия ($($sha.Substring(0, 7)))."
        return
    }

    $tmp = Join-Path $Root 'tmp'
    if (Test-Path -LiteralPath $tmp) { Remove-Item -LiteralPath $tmp -Recurse -Force }
    New-Item -ItemType Directory -Path $tmp | Out-Null
    try {
        $zip = Join-Path $tmp 'site.zip'
        $uri = 'https://codeload.github.com/{0}/zip/{1}' -f $Config.Repo, $sha
        Invoke-WebRequest -UseBasicParsing -Uri $uri -OutFile $zip -TimeoutSec 600
        Expand-Archive -LiteralPath $zip -DestinationPath (Join-Path $tmp 'x') -Force
        $src = Get-ChildItem -LiteralPath (Join-Path $tmp 'x') -Directory | Select-Object -First 1
        if (-not $src -or -not (Test-Path -LiteralPath (Join-Path $src.FullName 'index.html'))) {
            throw 'в скачанном архиве нет index.html'
        }
        Sync-Folder $src.FullName $siteDir
        Set-Content -LiteralPath $shaFile -Value $sha -Encoding ASCII
        Add-UpdateLog "Сайт обновлён до версии $($sha.Substring(0, 7))"
    } finally {
        Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# Задача планировщика и кнопка в панели могут сработать одновременно.
$mutex = $null
$owned = $false
try {
    $mutex = New-Object System.Threading.Mutex($false, 'Global\HomeServerUpdate')
    # AbandonedMutexException означает, что прошлый запуск оборвался, — мьютекс всё равно наш.
    try { $owned = $mutex.WaitOne(0) } catch { $owned = $true }
} catch { }
if (-not $owned) {
    Write-Output 'Обновление уже выполняется — попробуйте через минуту.'
    exit 0
}
$code = 0
try {
    Limit-Log
    Update-Upnp
    Update-Ddns
    if (-not $DdnsOnly) { Update-Site }
} catch {
    Add-UpdateLog "Ошибка обновления: $($_.Exception.Message)"
    $code = 1
} finally {
    $mutex.ReleaseMutex()
    $mutex.Dispose()
}
exit $code
