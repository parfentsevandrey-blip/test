#!/usr/bin/env bash
# =============================================================================
#  deploy/linux/setup-server.sh — превращает компьютер с Ubuntu / Debian / Linux Mint
#  (например, ноутбук, который всегда включён) в веб-сервер для этого сайта.
#
#  Что делает:
#    • ставит Caddy — веб-сервер, который сам получает и продлевает
#      бесплатный HTTPS-сертификат (Let's Encrypt);
#    • скачивает сайт из GitHub в /var/www/… и каждые 5 минут подтягивает
#      изменения (таймер site-update);
#    • запрещает ноутбуку засыпать, в том числе при закрытой крышке;
#    • включает файрвол: открыты только SSH (22), HTTP (80) и HTTPS (443);
#    • по желанию обновляет динамический DNS (DuckDNS, deSEC и т. п.),
#      если внешний IP-адрес у вас меняется.
#
#  Запуск (повторный запуск безопасен, так же меняются настройки):
#    sudo bash deploy/linux/setup-server.sh
#
#  Ответы на вопросы можно передать заранее переменными окружения:
#    DOMAIN         домен сайта (можно несколько через пробел); пустая строка —
#                   сайт только по http://<IP ноутбука> (проверка дома, туннель)
#    DUCKDNS_TOKEN  токен DuckDNS, если домен вида имя.duckdns.org
#    DDNS_URL       ссылка обновления другого сервиса динамического DNS
#    REPO_URL       репозиторий с сайтом (по умолчанию — этот, на GitHub)
#    BRANCH         ветка с сайтом (по умолчанию — основная ветка репозитория)
#    SITE_DIR       куда положить сайт (по умолчанию /var/www/kutuzovsky-12)
#    FIREWALL=0     не включать файрвол
#    KEEP_AWAKE=0   не менять настройки сна
#  Пример: sudo env DOMAIN=mysite.ru bash deploy/linux/setup-server.sh
# =============================================================================
set -euo pipefail
umask 022

CONF=/etc/site-server.conf
CADDY_LIST=/etc/apt/sources.list.d/caddy-stable.list
CADDY_KEYRING=/usr/share/keyrings/caddy-stable-archive-keyring.gpg
SETTINGS=(DOMAIN DUCKDNS_TOKEN DDNS_URL REPO_URL BRANCH SITE_DIR)
SCRIPT_PATH=$(realpath "$0" 2>/dev/null || echo "$0")

say()  { printf '\n\033[1;32m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[!]\033[0m %b\n' "$*" >&2; }
die()  { printf '\033[1;31m[x]\033[0m %b\n' "$*" >&2; exit 1; }
trim() { local s=$1; s=${s#"${s%%[![:space:]]*}"}; printf '%s' "${s%"${s##*[![:space:]]}"}"; }

[[ $EUID -eq 0 ]] || die "Нужны права администратора. Запустите так:\n    sudo bash $SCRIPT_PATH"
if ! command -v apt-get >/dev/null || [[ ! -d /run/systemd/system ]]; then
  die "Скрипт рассчитан на Ubuntu, Debian или Linux Mint (apt + systemd)."
fi

# --- Настройки: переменные окружения > прошлый запуск > значения по умолчанию ---
declare -A FROM_ENV=()
for v in "${SETTINGS[@]}"; do
  if [[ -n ${!v+x} ]]; then FROM_ENV[$v]=${!v}; fi
done
if [[ -r $CONF ]]; then
  # shellcheck source=/dev/null
  source "$CONF"
fi
for v in "${!FROM_ENV[@]}"; do printf -v "$v" '%s' "${FROM_ENV[$v]}"; done
DOMAIN=${DOMAIN-} DUCKDNS_TOKEN=${DUCKDNS_TOKEN-} DDNS_URL=${DDNS_URL-} BRANCH=${BRANCH-}
REPO_URL=${REPO_URL:-https://github.com/parfentsevandrey-blip/test.git}
SITE_DIR=${SITE_DIR:-/var/www/kutuzovsky-12}
FIREWALL=${FIREWALL:-1} KEEP_AWAKE=${KEEP_AWAKE:-1}

INTERACTIVE=false
if [[ ${NONINTERACTIVE:-0} != 1 ]] && { : </dev/tty; } 2>/dev/null; then INTERACTIVE=true; fi

# ask ПЕРЕМЕННАЯ "Вопрос" [secret] — Enter оставляет текущее значение, «-» его стирает.
ask() {
  local var=$1 question=$2 secret=${3:-} cur shown ans
  [[ -n ${FROM_ENV[$var]+x} ]] && return 0
  $INTERACTIVE || return 0
  cur=${!var}
  if [[ -n $cur ]]; then
    shown=$cur
    [[ -n $secret ]] && shown="сохранён"
    read -r -p "$question [$shown] (Enter — оставить, «-» — стереть): " ans </dev/tty
    ans=$(trim "$ans")
    case $ans in
      '') return 0 ;;
      -) ans='' ;;
    esac
  else
    read -r -p "$question: " ans </dev/tty
    ans=$(trim "$ans")
  fi
  printf -v "$var" '%s' "$ans"
}

# Приводит DOMAIN к виду "a.ru b.ru": убирает http://, слэши, переводит
# кириллицу в punycode (кутузовский.рф -> xn--...) и проверяет, что это домен.
normalize_domains() {
  local d out=()
  for d in ${DOMAIN//,/ }; do
    d=${d#*://}
    d=${d%%/*}
    d=${d%.}
    if [[ $d == *[![:ascii:]]* ]]; then
      d=$(python3 -c 'import sys; print(sys.argv[1].encode("idna").decode())' "$d" 2>/dev/null) \
        || die "Не получилось перевести домен в punycode — введите его латиницей (xn--…)."
    fi
    d=${d,,}
    [[ $d =~ ^([a-z0-9]([a-z0-9-]*[a-z0-9])?\.)+[a-z0-9-]{2,}$ ]] \
      || die "«$d» не похоже на доменное имя (пример: mysite.ru или kutuzovsky12.duckdns.org)."
    out+=("$d")
  done
  DOMAIN=${out[*]-}
}

# Имя на DuckDNS из домена вида имя.duckdns.org (или пусто).
duckdns_name() {
  local d
  for d in $DOMAIN; do
    if [[ $d == *.duckdns.org ]]; then
      d=${d%.duckdns.org}
      printf '%s' "${d##*.}"
      return
    fi
  done
}

save_settings() {
  local v
  (
    umask 077
    {
      echo "# Настройки сервера сайта (deploy/linux/setup-server.sh)."
      echo "# Меняйте их повторным запуском скрипта: sudo bash $SCRIPT_PATH"
      for v in "${SETTINGS[@]}"; do printf '%s=%q\n' "$v" "${!v}"; done
    } >"$CONF.new"
    mv -f "$CONF.new" "$CONF"
  )
}

# apt-get update. Если мешает сломанный репозиторий Caddy (например, у него истёк
# ключ подписи), убираем его — тогда Caddy поставится из репозитория дистрибутива.
apt_update() {
  local err
  err=$(apt-get -qq update 2>&1 >/dev/null) && return 0
  if [[ $err == *dl.cloudsmith.io/public/caddy* ]]; then
    warn "Официальный репозиторий Caddy сейчас не работает — беру Caddy из репозитория дистрибутива."
    rm -f "$CADDY_LIST" "$CADDY_KEYRING"
    CADDY_REPO_BROKEN=1
    err=$(apt-get -qq update 2>&1 >/dev/null) && return 0
  fi
  printf '%s\n' "$err" >&2
  die "apt-get update завершился с ошибкой — проверьте подключение к интернету."
}

install_packages() {
  say "Устанавливаю нужные программы (git, curl, Caddy)…"
  export DEBIAN_FRONTEND=noninteractive
  apt_update
  apt-get -y -qq install git curl ca-certificates gnupg iproute2 >/dev/null

  # Официальный репозиторий Caddy (https://caddyserver.com/docs/install) даёт свежие
  # версии; если он недоступен, Caddy поставится из репозитория дистрибутива.
  if [[ ! -f $CADDY_LIST && -z ${CADDY_REPO_BROKEN-} ]]; then
    local tmp
    tmp=$(mktemp -d)
    if curl -fsSL --max-time 60 https://dl.cloudsmith.io/public/caddy/stable/gpg.key -o "$tmp/key" \
      && curl -fsSL --max-time 60 https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt -o "$tmp/list"; then
      gpg --batch --yes --dearmor -o "$CADDY_KEYRING" "$tmp/key"
      chmod 644 "$CADDY_KEYRING"
      install -m 644 "$tmp/list" "$CADDY_LIST"
      apt_update
    else
      warn "Официальный репозиторий Caddy недоступен — беру Caddy из репозитория дистрибутива."
    fi
    rm -rf "$tmp"
  fi
  apt-get -y -qq install caddy >/dev/null \
    || die "Не удалось установить Caddy. Проверьте интернет и запустите скрипт ещё раз."
}

install_helpers() {
  cat >/usr/local/bin/site-update <<'EOF'
#!/usr/bin/env bash
# Подтягивает свежую версию сайта из GitHub. Запускается таймером
# site-update.timer каждые 5 минут; вручную: sudo site-update
set -euo pipefail
source /etc/site-server.conf
umask 022
cd "$SITE_DIR"
git remote set-url origin "$REPO_URL"
git fetch --quiet --depth 1 origin "$BRANCH"
new=$(git rev-parse FETCH_HEAD)
if [[ $(git rev-parse HEAD) != "$new" ]]; then
  git reset --quiet --hard "$new"
  git clean -fdq
  echo "Сайт обновлён: $(git log -1 --format='%h %s')"
elif [[ -t 1 ]]; then
  echo "Обновлений нет. На сервере: $(git log -1 --format='%h %s')"
fi
EOF

  cat >/usr/local/bin/ddns-update <<'EOF'
#!/usr/bin/env bash
# Сообщает сервису динамического DNS текущий внешний IPv4-адрес.
# Запускается таймером ddns-update.timer каждые 5 минут; вручную: sudo ddns-update
set -euo pipefail
source /etc/site-server.conf
[[ -n $DDNS_URL ]] || exit 0
resp=$(curl -4 -fsS --max-time 30 "$DDNS_URL")
echo "Ответ сервиса DDNS: $resp"
case $resp in
  KO* | bad* | nohost* | notfqdn* | abuse* | 911* | dnserr*) exit 1 ;;
esac
EOF
  chmod 755 /usr/local/bin/site-update /usr/local/bin/ddns-update

  local name what
  for name in site-update ddns-update; do
    if [[ $name == site-update ]]; then what="Обновление сайта из GitHub"
    else what="Обновление динамического DNS"
    fi
    cat >"/etc/systemd/system/$name.service" <<EOF
[Unit]
Description=$what
Wants=network-online.target
After=network-online.target

[Service]
Type=oneshot
Environment=HOME=/root
ExecStart=/usr/local/bin/$name
EOF
    cat >"/etc/systemd/system/$name.timer" <<EOF
[Unit]
Description=$what (каждые 5 минут)

[Timer]
OnBootSec=1min
OnUnitActiveSec=5min
RandomizedDelaySec=20s

[Install]
WantedBy=timers.target
EOF
  done
  systemctl daemon-reload
}

deploy_site() {
  say "Скачиваю сайт из GitHub в $SITE_DIR…"
  if [[ -z $BRANCH ]]; then
    BRANCH=$(git ls-remote --symref "$REPO_URL" HEAD 2>/dev/null \
      | awk '$1 == "ref:" { sub("^refs/heads/", "", $2); print $2; exit }') || true
    [[ -n $BRANCH ]] || die "Не удалось связаться с $REPO_URL — проверьте интернет."
  fi
  if [[ -d $SITE_DIR/.git ]]; then
    git -C "$SITE_DIR" remote set-url origin "$REPO_URL"
  elif [[ -e $SITE_DIR && -n $(ls -A "$SITE_DIR" 2>/dev/null) ]]; then
    die "Папка $SITE_DIR уже существует и не пуста.\nУкажите другую: sudo env SITE_DIR=/var/www/другая-папка bash $SCRIPT_PATH"
  else
    mkdir -p "$(dirname "$SITE_DIR")"
    git clone --quiet --depth 1 --branch "$BRANCH" "$REPO_URL" "$SITE_DIR" \
      || die "Не удалось скачать сайт (репозиторий $REPO_URL, ветка $BRANCH)."
  fi
  save_settings
  /usr/local/bin/site-update >/dev/null
  echo "Ветка $BRANCH, версия $(git -C "$SITE_DIR" log -1 --format='%h %s')"
  systemctl enable --now site-update.timer >/dev/null 2>&1
}

write_caddyfile() {
  say "Настраиваю веб-сервер Caddy…"
  local busy
  busy=$(ss -Hltnp '( sport = :80 or sport = :443 )' 2>/dev/null | grep -v '"caddy"' || true)
  [[ -z $busy ]] || die "Порт 80 или 443 уже занят другой программой:\n$busy\nОстановите её (например: sudo systemctl disable --now apache2) и запустите скрипт снова."

  if [[ -f /etc/caddy/Caddyfile && ! -f /etc/caddy/Caddyfile.orig ]] \
    && ! grep -q 'setup-server.sh' /etc/caddy/Caddyfile; then
    cp /etc/caddy/Caddyfile /etc/caddy/Caddyfile.orig
  fi
  {
    cat <<EOF
# Создано deploy/linux/setup-server.sh — при повторном запуске скрипта файл перезаписывается.
# Справка по формату: https://caddyserver.com/docs/caddyfile

(site) {
	root * $SITE_DIR
	encode zstd gzip
	file_server {
		hide .git .github deploy
	}
	header {
		X-Content-Type-Options nosniff
		Referrer-Policy strict-origin-when-cross-origin
	}
}
EOF
    if [[ -n $DOMAIN ]]; then
      printf '\n# Сайт в интернете. HTTPS-сертификат Caddy получит и будет продлевать сам.\n'
      printf '%s {\n\timport site\n}\n' "${DOMAIN// /, }"
    fi
    printf '\n# Тот же сайт по http://<IP ноутбука> — для проверки из домашней сети\n'
    printf '# (и для туннеля, если внешнего IP нет).\n:80 {\n\timport site\n}\n'
  } >/etc/caddy/Caddyfile.new

  if ! caddy validate --adapter caddyfile --config /etc/caddy/Caddyfile.new >/dev/null 2>&1; then
    caddy validate --adapter caddyfile --config /etc/caddy/Caddyfile.new || true
    die "Caddy не принял настройки (см. ошибку выше). Старый файл /etc/caddy/Caddyfile не тронут."
  fi
  mv -f /etc/caddy/Caddyfile.new /etc/caddy/Caddyfile
  systemctl enable caddy >/dev/null 2>&1
  systemctl reload-or-restart caddy \
    || die "Caddy не запустился. Подробности: sudo journalctl -u caddy -n 30"
}

setup_ddns() {
  if [[ -n $DDNS_URL ]]; then
    say "Включаю обновление динамического DNS…"
    /usr/local/bin/ddns-update \
      || warn "Сервис DDNS ответил ошибкой — проверьте токен или ссылку обновления."
    systemctl enable --now ddns-update.timer >/dev/null 2>&1
  else
    systemctl disable --now ddns-update.timer >/dev/null 2>&1 || true
  fi
}

keep_awake() {
  say "Запрещаю ноутбуку засыпать (в том числе при закрытой крышке)…"
  systemctl mask sleep.target suspend.target hibernate.target hybrid-sleep.target \
    suspend-then-hibernate.target >/dev/null 2>&1
  mkdir -p /etc/systemd/logind.conf.d
  cat >/etc/systemd/logind.conf.d/90-site-server.conf <<'EOF'
# Создано deploy/linux/setup-server.sh: ноутбук-сервер не засыпает от крышки и кнопок сна.
[Login]
HandleLidSwitch=ignore
HandleLidSwitchExternalPower=ignore
HandleLidSwitchDocked=ignore
HandleSuspendKey=ignore
HandleHibernateKey=ignore
IdleAction=ignore
EOF
  # Энергосбережение Wi-Fi на некоторых адаптерах приводит к обрывам связи.
  # Имя файла начинается с «zz-», чтобы перекрыть default-wifi-powersave-on.conf.
  if [[ -d /etc/NetworkManager ]]; then
    mkdir -p /etc/NetworkManager/conf.d
    printf '[connection]\nwifi.powersave = 2\n' >/etc/NetworkManager/conf.d/zz-site-server-wifi-powersave-off.conf
  fi
}

# Если пароль Wi-Fi сохранён только для одного пользователя, после перезагрузки
# ноутбук не подключится к сети, пока кто-нибудь не войдёт в систему.
check_wifi() {
  if ! command -v nmcli >/dev/null || ! systemctl is-active --quiet NetworkManager; then return 0; fi
  local uuid type name perms flags
  while IFS=: read -r uuid type name; do
    [[ $type == 802-11-wireless ]] || continue
    perms=$(nmcli -g connection.permissions connection show "$uuid" 2>/dev/null || true)
    flags=$(nmcli -g 802-11-wireless-security.psk-flags connection show "$uuid" 2>/dev/null || true)
    if [[ -n $perms || $flags == 1* ]]; then
      warn "Сеть Wi-Fi «$name» доступна только вашему пользователю — после перезагрузки\n    ноутбук не подключится к ней сам. Откройте «Настройки → Wi-Fi → ⚙ у сети»\n    и включите «Подключаться автоматически» и «Сделать доступным для других пользователей»."
    fi
  done < <(nmcli -t -f UUID,TYPE,NAME connection show --active 2>/dev/null)
}

setup_firewall() {
  say "Включаю файрвол (открыты только порты 22, 80, 443)…"
  command -v ufw >/dev/null || apt-get -y -qq install ufw >/dev/null
  ufw allow 22/tcp comment 'SSH' >/dev/null
  ufw allow 80/tcp comment 'HTTP' >/dev/null
  ufw allow 443/tcp comment 'HTTPS' >/dev/null
  ufw allow 443/udp comment 'HTTP/3' >/dev/null
  ufw --force enable >/dev/null
}

ensure_security_updates() {
  dpkg -s unattended-upgrades >/dev/null 2>&1 || apt-get -y -qq install unattended-upgrades >/dev/null
  if [[ ! -e /etc/apt/apt.conf.d/20auto-upgrades ]]; then
    printf 'APT::Periodic::Update-Package-Lists "1";\nAPT::Periodic::Unattended-Upgrade "1";\n' \
      >/etc/apt/apt.conf.d/20auto-upgrades
  fi
}

summary() {
  local lan_ip pub_ip d dns_ip ok=true
  lan_ip=$(ip -4 route get 1.1.1.1 2>/dev/null \
    | awk '{ for (i = 1; i < NF; i++) if ($i == "src") { print $(i + 1); exit } }') || true
  sleep 1
  curl -fsS -o /dev/null --max-time 10 http://127.0.0.1/ || ok=false

  echo
  echo "=================================================================="
  if $ok; then
    echo " Готово! Сайт работает."
  else
    warn "Сайт не отвечает на этом ноутбуке. Смотрите журнал: sudo journalctl -u caddy -n 30"
  fi
  echo
  echo " • В домашней сети:  http://${lan_ip:-<IP ноутбука>}"
  if [[ -n $DOMAIN ]]; then
    for d in $DOMAIN; do echo " • В интернете:      https://$d"; done
    pub_ip=$(curl -4 -fsS --max-time 10 https://api.ipify.org 2>/dev/null \
      || curl -4 -fsS --max-time 10 https://ifconfig.me/ip 2>/dev/null || true)
    echo
    echo " Проверка домена (ваш внешний IP: ${pub_ip:-не удалось узнать}):"
    for d in $DOMAIN; do
      dns_ip=$(getent ahostsv4 "$d" 2>/dev/null | awk 'NR == 1 { print $1 }') || true
      if [[ -z $dns_ip ]]; then
        echo "   ✗ $d пока не находится в DNS — проверьте настройки домена"
        echo "     (после изменений может пройти от минуты до нескольких часов)."
      elif [[ -n $pub_ip && $dns_ip != "$pub_ip" ]]; then
        echo "   ✗ $d указывает на $dns_ip, а должен — на $pub_ip."
      else
        echo "   ✓ $d → $dns_ip"
      fi
    done
    echo
    echo " Чтобы сайт открывался из интернета, на роутере нужно:"
    echo "   1) закрепить за этим ноутбуком адрес ${lan_ip:-<IP ноутбука>};"
    echo "   2) пробросить порты TCP 80 и TCP 443 на ${lan_ip:-<IP ноутбука>}."
    echo " Сертификат HTTPS появится сам через минуту-другую после этого."
    echo " Проверяйте с телефона через мобильный интернет (не через домашний Wi-Fi)."
  else
    echo
    echo " Откройте этот адрес на телефоне или компьютере, подключённом к тому же Wi-Fi."
    echo " Домен можно добавить позже — просто запустите скрипт ещё раз."
  fi
  echo
  echo " Полезные команды:"
  echo "   sudo systemctl status caddy        работает ли веб-сервер"
  echo "   sudo journalctl -u caddy -n 50     журнал (видно, получен ли сертификат)"
  echo "   sudo site-update                   обновить сайт из GitHub прямо сейчас"
  echo "   sudo bash $SCRIPT_PATH"
  echo "                                      изменить настройки (домен и т. п.)"
  if [[ $KEEP_AWAKE == 1 ]]; then
    echo
    echo " Перезагрузите ноутбук (sudo reboot), чтобы применились настройки сна и Wi-Fi."
  fi
  echo "=================================================================="
}

# ------------------------------------------------------------------- вопросы
if $INTERACTIVE && [[ -z ${FROM_ENV[DOMAIN]+x} ]]; then
  cat <<'EOF'

Настройка домашнего веб-сервера для сайта.

Домен — адрес сайта в интернете, например kutuzovsky12.duckdns.org или mysite.ru.
Если роутер и домен ещё не настроены, просто нажмите Enter: сайт заработает
в домашней сети, а домен можно будет добавить позже, запустив скрипт ещё раз.

EOF
fi
ask DOMAIN "Домен сайта"
normalize_domains
duck=$(duckdns_name)
if [[ -n $duck ]]; then
  ask DUCKDNS_TOKEN "Токен DuckDNS (вверху страницы duckdns.org после входа)" secret
  DUCKDNS_TOKEN=$(trim "$DUCKDNS_TOKEN")
  if [[ -n $DUCKDNS_TOKEN ]]; then
    [[ $DUCKDNS_TOKEN =~ ^[A-Za-z0-9-]{20,}$ ]] || die "Токен DuckDNS выглядит неверно — скопируйте его со страницы duckdns.org целиком."
    DDNS_URL="https://www.duckdns.org/update?domains=$duck&token=$DUCKDNS_TOKEN&ip="
  else
    warn "Без токена DuckDNS адрес $duck.duckdns.org не будет следовать за сменой вашего IP."
  fi
elif [[ -n $DOMAIN ]]; then
  ask DDNS_URL "Ссылка обновления DDNS, если внешний IP меняется (Enter — не нужно)" secret
fi
[[ -z $DDNS_URL || $DDNS_URL =~ ^https?:// ]] || die "Ссылка DDNS должна начинаться с https://"

# ------------------------------------------------------------------- установка
install_packages
install_helpers
deploy_site
write_caddyfile
setup_ddns
if [[ $KEEP_AWAKE == 1 ]]; then keep_awake; fi
if [[ $FIREWALL == 1 ]]; then setup_firewall; fi
ensure_security_updates
check_wifi
summary
