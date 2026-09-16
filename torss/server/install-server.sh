#!/usr/bin/env bash
# TorSS: установка сервера Shadowsocks-2022 + ShadowTLS v3 (на базе sing-box)
# на VPS за пределами РФ. Debian 11/12, Ubuntu 22.04/24.04, root.
#
#   curl -fsSL https://raw.githubusercontent.com/<you>/<repo>/main/torss/server/install-server.sh | sudo bash
#   или: sudo bash install-server.sh [-p PORT] [-s SNI]
#
# Что делает:
#   * ставит sing-box (deb с GitHub Releases);
#   * генерирует пароли, пишет /etc/sing-box/config.json:
#       443/tcp  ShadowTLS v3 (маскировка под TLS-рукопожатие к $SNI) -> Shadowsocks 2022-blake3-aes-128-gcm
#   * включает BBR, открывает порт в ufw (если есть), запускает systemd-сервис;
#   * печатает готовый блок "server" для config.json клиента.
#
# Почему так: обычный Shadowsocks в РФ детектируется ТСПУ по паттерну трафика и
# режется через несколько минут/часов. ShadowTLS v3 делает соединение неотличимым
# от TLS 1.3 к настоящему сайту ($SNI): рукопожатие реально проксируется на этот
# сайт, а данные идут только после успешной аутентификации клиента.
set -euo pipefail

PORT=443
SNI="gateway.icloud.com"
while getopts "p:s:h" opt; do
  case $opt in
    p) PORT="$OPTARG" ;;
    s) SNI="$OPTARG" ;;
    h) sed -n '2,20p' "$0"; exit 0 ;;
    *) exit 1 ;;
  esac
done

[ "$(id -u)" -eq 0 ] || { echo "run as root"; exit 1; }
command -v curl >/dev/null || apt-get install -y curl

echo "== sing-box"
if ! command -v sing-box >/dev/null; then
  ARCH=$(dpkg --print-architecture)   # amd64 | arm64
  TAG=$(curl -fsSL https://api.github.com/repos/SagerNet/sing-box/releases/latest | grep -m1 '"tag_name"' | sed -E 's/.*"v([^"]+)".*/\1/')
  DEB="sing-box_${TAG}_linux_${ARCH}.deb"
  curl -fSL -o "/tmp/$DEB" "https://github.com/SagerNet/sing-box/releases/download/v${TAG}/${DEB}"
  dpkg -i "/tmp/$DEB"
  rm -f "/tmp/$DEB"
fi
sing-box version | head -1

# Проверяем, что SNI-сайт отвечает по TLS 1.3 с этого сервера: иначе ShadowTLS не заработает.
echo "== проверка $SNI:443"
if ! timeout 8 bash -c "cat </dev/null >/dev/tcp/$SNI/443" 2>/dev/null; then
  echo "!! $SNI:443 недоступен с этого сервера. Выберите другой SNI (-s), например www.microsoft.com или dl.google.com"
  exit 1
fi

SS_PASSWORD=$(sing-box generate rand 16 --base64)      # 2022-blake3-aes-128-gcm: 16 байт base64
STLS_PASSWORD=$(sing-box generate rand 24 --base64)

mkdir -p /etc/sing-box
if [ -f /etc/sing-box/config.json ]; then
  cp /etc/sing-box/config.json "/etc/sing-box/config.json.bak.$(date +%s)"
fi
cat >/etc/sing-box/config.json <<EOF
{
  "log": { "level": "warn", "timestamp": true },
  "inbounds": [
    {
      "type": "shadowtls",
      "tag": "stls-in",
      "listen": "::",
      "listen_port": ${PORT},
      "version": 3,
      "users": [ { "name": "torss", "password": "${STLS_PASSWORD}" } ],
      "handshake": { "server": "${SNI}", "server_port": 443 },
      "strict_mode": true,
      "detour": "ss-in"
    },
    {
      "type": "shadowsocks",
      "tag": "ss-in",
      "listen": "127.0.0.1",
      "listen_port": 18388,
      "method": "2022-blake3-aes-128-gcm",
      "password": "${SS_PASSWORD}"
    }
  ],
  "outbounds": [ { "type": "direct", "tag": "direct" } ]
}
EOF
chmod 600 /etc/sing-box/config.json
sing-box check -c /etc/sing-box/config.json

echo "== BBR"
cat >/etc/sysctl.d/99-torss.conf <<EOF
net.core.default_qdisc = fq
net.ipv4.tcp_congestion_control = bbr
net.ipv4.tcp_fastopen = 3
EOF
sysctl -p /etc/sysctl.d/99-torss.conf >/dev/null || true

if command -v ufw >/dev/null && ufw status | grep -q "Status: active"; then
  ufw allow "${PORT}/tcp" >/dev/null && echo "ufw: открыт ${PORT}/tcp"
fi

systemctl enable --now sing-box
systemctl restart sing-box
sleep 1
systemctl is-active sing-box >/dev/null || { journalctl -u sing-box -n 30 --no-pager; exit 1; }

IP4=$(curl -4 -fsS --max-time 8 https://api.ipify.org || curl -4 -fsS --max-time 8 https://ifconfig.me || hostname -I | awk '{print $1}')

cat <<EOF

=====================================================================
Сервер готов. Вставьте этот блок в config.json клиента (поле "server"):

  "server": {
    "address": "${IP4}",
    "port": ${PORT},
    "method": "2022-blake3-aes-128-gcm",
    "password": "${SS_PASSWORD}",
    "shadowtls": {
      "enabled": true,
      "password": "${STLS_PASSWORD}",
      "sni": "${SNI}"
    }
  }

Файл конфигурации клиента: %LOCALAPPDATA%\\TorSS\\config.json
Логи сервера:               journalctl -u sing-box -f
Сменить пароли:             повторно запустить этот скрипт
=====================================================================
EOF
