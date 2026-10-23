#!/usr/bin/env bash
# Постоянная подпись приложений каталога (запускается в CI на macOS перед сборкой).
#
# Подпись ad-hoc меняется с каждой сборкой, и macOS после обновления заново спрашивает разрешения:
# доступ к связке ключей, «Управление приложениями», микрофон, универсальный доступ. Один и тот же
# сертификат для всех сборок даёт стабильное требование подписи — разрешения переживают обновления.
#
# Сертификат самоподписанный и берётся из секретов репозитория:
#   HARBOR_SIGNING_P12      — файл .p12 в base64 (создаётся scripts/make-signing-identity.sh)
#   HARBOR_SIGNING_PASSWORD — пароль к нему
# Если секретов нет, сборка подписывается ad-hoc, как раньше.
# Результат — CODESIGN_IDENTITY (SHA-1 сертификата) и CODESIGN_KEYCHAIN в $GITHUB_ENV.
set -euo pipefail

if [ -z "${HARBOR_SIGNING_P12:-}" ] || [ -z "${HARBOR_SIGNING_PASSWORD:-}" ]; then
  echo "::warning::Секретов подписи нет — сборка будет подписана ad-hoc"
  exit 0
fi

WORK="${RUNNER_TEMP:-/tmp}/harbor-signing"
mkdir -p "$WORK"
KEYCHAIN="$WORK/harbor.keychain-db"
KEYPASS="$(uuidgen)"

printf '%s' "$HARBOR_SIGNING_P12" | base64 --decode > "$WORK/identity.p12"
security delete-keychain "$KEYCHAIN" 2>/dev/null || true
security create-keychain -p "$KEYPASS" "$KEYCHAIN"
security set-keychain-settings -lut 21600 "$KEYCHAIN"
security unlock-keychain -p "$KEYPASS" "$KEYCHAIN"
security import "$WORK/identity.p12" -k "$KEYCHAIN" -P "$HARBOR_SIGNING_PASSWORD" -T /usr/bin/codesign >/dev/null
rm -f "$WORK/identity.p12"
security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$KEYPASS" "$KEYCHAIN" >/dev/null
security list-keychains -d user -s "$KEYCHAIN" $(security list-keychains -d user | tr -d '"')

HASH="$(security find-certificate -c "Harbor Code Signing" -Z "$KEYCHAIN" | awk '/SHA-1/ {print $3}' | head -1)"
[ -n "$HASH" ] || { echo "::error::Сертификат «Harbor Code Signing» не найден в .p12"; exit 1; }

# Проба: подписать копию системной утилиты этим сертификатом.
cp /usr/bin/true "$WORK/probe"
codesign --force --sign "$HASH" --keychain "$KEYCHAIN" "$WORK/probe"
echo "Подпись: Harbor Code Signing ($HASH)"
{
  echo "CODESIGN_IDENTITY=$HASH"
  echo "CODESIGN_KEYCHAIN=$KEYCHAIN"
} >> "${GITHUB_ENV:-/dev/null}"
