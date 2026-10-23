#!/usr/bin/env bash
# Создаёт постоянный сертификат подписи «Harbor Code Signing» и кладёт его в секреты репозитория.
# Запускается один раз на вашем Mac:
#   ./Harbor/scripts/make-signing-identity.sh
#
# Зачем: с одним и тем же сертификатом macOS узнаёт приложения после обновления и не спрашивает
# заново доступ к связке ключей, микрофону, универсальному доступу и «Управлению приложениями».
# Сертификат самоподписанный: платная программа Apple не нужна. Закрытый ключ не покидает ваш Mac
# иначе как в зашифрованные секреты GitHub Actions.
set -euo pipefail

REPO="${1:-parfentsevandrey-blip/test}"
OUT="$HOME/Library/Application Support/Harbor/signing"
mkdir -p "$OUT"
chmod 700 "$OUT"
cd "$OUT"

if [ -f identity.p12 ]; then
  echo "Сертификат уже есть: $OUT/identity.p12 — используем его."
else
  cat > cert.cnf <<CNF
[req]
distinguished_name = dn
prompt = no
[dn]
CN = Harbor Code Signing
O = Harbor
[ext]
basicConstraints = critical,CA:false
keyUsage = critical,digitalSignature
extendedKeyUsage = critical,codeSigning
subjectKeyIdentifier = hash
CNF
  /usr/bin/openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 7300 \
    -keyout key.pem -out cert.pem -config cert.cnf -extensions ext 2>/dev/null
  uuidgen > password.txt
  chmod 600 password.txt
  /usr/bin/openssl pkcs12 -export -inkey key.pem -in cert.pem -name "Harbor Code Signing" \
    -out identity.p12 -passout "file:password.txt"
  rm -f key.pem cert.cnf
  chmod 600 identity.p12
  echo "Создан сертификат: $OUT/identity.p12 (сохраните папку — это ваш ключ подписи)"
fi

P12="$(base64 -i identity.p12)"
PASSWORD="$(cat password.txt)"

if command -v gh >/dev/null && gh auth status >/dev/null 2>&1; then
  printf '%s' "$P12" | gh secret set HARBOR_SIGNING_P12 --repo "$REPO"
  printf '%s' "$PASSWORD" | gh secret set HARBOR_SIGNING_PASSWORD --repo "$REPO"
  echo "Готово: секреты HARBOR_SIGNING_P12 и HARBOR_SIGNING_PASSWORD записаны в $REPO."
else
  echo
  echo "GitHub CLI (gh) не найден или не вошёл в аккаунт. Добавьте два секрета вручную:"
  echo "  https://github.com/$REPO/settings/secrets/actions → New repository secret"
  echo
  printf '%s' "$P12" | pbcopy
  echo "1. HARBOR_SIGNING_P12 — значение уже скопировано в буфер обмена, вставьте (⌘V) и сохраните."
  read -r -p "   Нажмите Return, когда сохраните первый секрет… " _
  printf '%s' "$PASSWORD" | pbcopy
  echo "2. HARBOR_SIGNING_PASSWORD — пароль скопирован в буфер обмена, вставьте и сохраните."
fi
