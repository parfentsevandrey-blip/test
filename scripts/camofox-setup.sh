#!/bin/bash
# Скрипт настройки облачного окружения Claude Code: ставит camofox-browser
# (https://github.com/jo-inc/camofox-browser) в каждой новой сессии.
# Вставляется в настройки окружения → Setup script.
# Запуск сервера в сессии: camofox-start  →  http://127.0.0.1:9377
set -euo pipefail

CAMOFOX_REF=v1.17.0              # версия программы; main — самый свежий код
APP_DIR=/opt/camofox-browser
CACHE_DIR=/root/.cache/camoufox  # здесь camoufox-js ищет браузер

# 1. Системные библиотеки для Firefox/Camoufox, которых нет в базовом образе
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq libdbus-glib-1-2 libxss1 libegl1 >/dev/null

# 2. Код и npm-зависимости (только рабочие, как в Dockerfile проекта). Штатный
#    postinstall качает браузер через API GitHub, закрытое в песочнице, поэтому
#    он отключён, а браузер скачивается напрямую на шаге 3.
[ -d "$APP_DIR/.git" ] || git -c advice.detachedHead=false clone -q --depth 1 \
  --branch "$CAMOFOX_REF" https://github.com/jo-inc/camofox-browser "$APP_DIR"
cd "$APP_DIR"
CAMOFOX_SKIP_DOWNLOAD=1 npm ci --omit=dev --no-audit --no-fund --loglevel=error

# 3. Браузер Camoufox той версии, что закреплена в Dockerfile проекта
VER=$(sed -n 's/^ARG CAMOUFOX_VERSION=//p' Dockerfile)
REL=$(sed -n 's/^ARG CAMOUFOX_RELEASE=//p' Dockerfile)
: "${VER:?}" "${REL:?}"
ARCH=$(uname -m); [ "$ARCH" = aarch64 ] && ARCH=arm64
WANT="{\"version\":\"$VER\",\"release\":\"$REL\"}"
if [ "$(cat "$CACHE_DIR/version.json" 2>/dev/null)" != "$WANT" ]; then
  rm -rf "$CACHE_DIR"
  mkdir -p "$CACHE_DIR"
  curl -fsSL -o /tmp/camoufox.zip \
    "https://github.com/daijro/camoufox/releases/download/v$VER-$REL/camoufox-$VER-$REL-lin.$ARCH.zip"
  unzip -q -o /tmp/camoufox.zip -d "$CACHE_DIR" || [ $? -eq 1 ]  # 1 = только предупреждения
  rm -f /tmp/camoufox.zip
  chmod -R 755 "$CACHE_DIR"
  echo "$WANT" > "$CACHE_DIR/version.json"
fi
test -x "$CACHE_DIR/camoufox-bin"

# 4. Необязательное, ошибки не прерывают установку: uBlock Origin,
#    база GeoIP (нужна только при работе через PROXY_*), yt-dlp для субтитров YouTube
env -u PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD node --input-type=module -e \
  "import * as a from 'camoufox-js/dist/addons.js'; await a.maybeDownloadAddons(a.DefaultAddons)" >/dev/null \
  || echo "предупреждение: uBlock Origin не скачан, camofox попробует при запуске"
[ -s "$CACHE_DIR/GeoLite2-City.mmdb" ] || curl -fsSL -o "$CACHE_DIR/GeoLite2-City.mmdb" \
  https://github.com/P3TERX/GeoLite.mmdb/releases/latest/download/GeoLite2-City.mmdb \
  || { rm -f "$CACHE_DIR/GeoLite2-City.mmdb"; echo "предупреждение: база GeoIP не скачана"; }
command -v yt-dlp >/dev/null || sh plugins/youtube/post-install.sh \
  || { rm -f /usr/local/bin/yt-dlp; echo "предупреждение: yt-dlp не установлен"; }

# 5. Команда camofox-start: доверяет сертификатам прокси песочницы и запускает сервер
cat > /usr/local/bin/camofox-start <<'EOF'
#!/bin/bash
# Запускает сервер camofox-browser в фоне: http://127.0.0.1:9377
set -euo pipefail
# Прокси песочницы подменяет TLS, а у Firefox своё хранилище корневых сертификатов:
# добавляем сертификаты из системного хранилища через политику Firefox.
DIST=/root/.cache/camoufox/distribution
[ -f "$DIST/policies.json.orig" ] || cp "$DIST/policies.json" "$DIST/policies.json.orig"
rm -f "$DIST"/sandbox-ca-*.pem
shopt -s nullglob
awk -v d="$DIST" '/BEGIN CERT/ {f = d "/sandbox-ca-" (++n) ".pem"} f {print > f} /END CERT/ {close(f); f = ""}' \
  /usr/local/share/ca-certificates/*.crt </dev/null
node -e '
  const fs = require("fs"), d = process.argv[1];
  const p = JSON.parse(fs.readFileSync(d + "/policies.json.orig", "utf8"));
  const certs = fs.readdirSync(d).filter(f => f.startsWith("sandbox-ca-")).map(f => d + "/" + f);
  if (certs.length) p.policies.Certificates = { Install: certs };
  fs.writeFileSync(d + "/policies.json", JSON.stringify(p, null, 2));' "$DIST"
[ "${1:-}" = --trust-only ] && exit 0

URL="http://127.0.0.1:${CAMOFOX_PORT:-9377}"
curl -fsS "$URL/health" >/dev/null 2>&1 && { echo "camofox-browser уже запущен: $URL"; exit 0; }
cd /opt/camofox-browser
# Телеметрия по умолчанию выключена: сбои из-за песочницы не должны уходить issue в репозиторий проекта
CAMOFOX_BIND_HOST="${CAMOFOX_BIND_HOST:-127.0.0.1}" \
CAMOFOX_CRASH_REPORT_ENABLED="${CAMOFOX_CRASH_REPORT_ENABLED:-false}" \
  setsid nohup node server.js >/tmp/camofox-browser.log 2>&1 </dev/null &
for _ in $(seq 30); do
  curl -fsS "$URL/health" >/dev/null 2>&1 && { echo "camofox-browser запущен: $URL (лог: /tmp/camofox-browser.log)"; exit 0; }
  sleep 1
done
echo "camofox-browser не запустился, см. /tmp/camofox-browser.log" >&2
exit 1
EOF
chmod 755 /usr/local/bin/camofox-start
camofox-start --trust-only
echo "camofox-browser $CAMOFOX_REF установлен, запуск: camofox-start"
