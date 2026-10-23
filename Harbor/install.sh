#!/bin/bash
# Установка и обновление приложений каталога из Терминала — без предупреждений Gatekeeper.
#
#   curl -fsSL https://github.com/parfentsevandrey-blip/test/releases/download/catalog/install.sh | bash
#       — поставить Harbor (дальше всё ставится и обновляется из него)
#   … | bash -s -- ward swarm      — конкретные приложения
#   … | bash -s -- --all           — все опубликованные
#   … | bash -s -- --update        — обновить всё, что уже установлено
#   … | bash -s -- --list          — показать каталог
#
# Почему без предупреждений: «неизвестного разработчика» macOS проверяет у файлов с меткой карантина,
# которую ставит браузер. curl её не ставит, а скрипт к тому же снимает её с уже установленных копий.
# Перед установкой проверяются SHA-256 архива и подпись приложения.
#
# Обновление не трогает данные: настройки и файлы приложений лежат в ~/Library, а заменяется только
# сам бандл. Запущенное приложение штатно закрывается и после замены открывается снова. Предыдущая
# версия остаётся в ~/Library/Application Support/Harbor/Backups.
set -euo pipefail

CATALOG_URL="${HARBOR_CATALOG_URL:-https://github.com/parfentsevandrey-blip/test/releases/download/catalog/catalog.json}"
BACKUPS="${HARBOR_SUPPORT_DIR:-$HOME/Library/Application Support/Harbor}/Backups"
if [ -n "${HARBOR_INSTALL_DIR:-}" ]; then
  DEST="$HARBOR_INSTALL_DIR"
elif [ -w /Applications ]; then
  DEST="/Applications"
else
  DEST="$HOME/Applications"
fi
mkdir -p "$DEST" "$BACKUPS"

bold() { printf '\033[1m%s\033[0m\n' "$*"; }
info() { printf '  %s\n' "$*"; }
fail() { printf '\033[31m✗ %s\033[0m\n' "$*" >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

curl -fsSL --retry 3 "$CATALOG_URL" -o "$WORK/catalog.json" || fail "Каталог недоступен: $CATALOG_URL"

# JSON разбирает JavaScript for Automation — он есть в любой macOS, ставить ничего не нужно.
cat > "$WORK/parse.js" <<'JS'
function run(argv) {
  const text = $.NSString.stringWithContentsOfFileEncodingError(argv[0], $.NSUTF8StringEncoding, null).js
  const catalog = JSON.parse(text)
  return catalog.apps.map(a => [a.id, a.name, a.bundleID, a.version || "", a.build || 0, a.url || "",
    a.sha256 || "", a.available ? 1 : 0, a.tagline || ""].join("\t")).join("\n")
}
JS
osascript -l JavaScript "$WORK/parse.js" "$WORK/catalog.json" > "$WORK/apps.tsv" || fail "Не удалось прочитать каталог"

plist() { /usr/libexec/PlistBuddy -c "Print :$2" "$1/Contents/Info.plist" 2>/dev/null || true; }

# Где уже стоит приложение (по идентификатору бандла).
locate() {
  local bundle="$1" name="$2"
  for p in "$DEST/$name.app" "/Applications/$name.app" "$HOME/Applications/$name.app"; do
    if [ -d "$p" ] && [ "$(plist "$p" CFBundleIdentifier)" = "$bundle" ]; then echo "$p"; return; fi
  done
}

# a новее b? Версии вида «1.2.0» и номер сборки.
newer() {
  [ "$1" = "$2" ] && return 1
  [ "$(printf '%s\n%s\n' "$1" "$2" | sort -t. -k1,1n -k2,2n -k3,3n -k4,4n | tail -1)" = "$1" ]
}

install_app() {
  local id="$1" name="$2" bundle="$3" version="$4" build="$5" url="$6" sum="$7"
  local target current="" was_running=0
  target="$(locate "$bundle" "$name")"
  [ -n "$target" ] || target="$DEST/$name.app"
  if [ -d "$target" ]; then
    current="$(plist "$target" CFBundleShortVersionString).$(plist "$target" CFBundleVersion)"
    if ! newer "$version.$build" "$current"; then
      xattr -dr com.apple.quarantine "$target" 2>/dev/null || true
      info "$name уже последней версии ($version, сборка $build)"
      return
    fi
  fi
  bold "$name $version (сборка $build)"
  local stage
  stage="$(mktemp -d "$(dirname "$target")/.harbor-XXXXXX")"
  info "Скачиваю…"
  curl -fL --retry 3 --progress-bar "$url" -o "$stage/app.zip" || { rm -rf "$stage"; fail "Не удалось скачать $name"; }
  echo "$sum  $stage/app.zip" | shasum -a 256 -c - >/dev/null 2>&1 || { rm -rf "$stage"; fail "Контрольная сумма $name не совпала"; }
  ditto -x -k "$stage/app.zip" "$stage"
  [ "$(plist "$stage/$name.app" CFBundleIdentifier)" = "$bundle" ] || { rm -rf "$stage"; fail "В архиве не $name"; }
  codesign --verify --strict "$stage/$name.app" 2>/dev/null || { rm -rf "$stage"; fail "Подпись $name не прошла проверку"; }
  info "Проверено: SHA-256 и подпись"

  if [ -d "$target" ]; then
    if pgrep -f "$target/Contents/MacOS/" >/dev/null 2>&1; then
      was_running=1
      info "Закрываю $name (он сохранит своё состояние)…"
      osascript -e "tell application id \"$bundle\" to quit" >/dev/null 2>&1 || true
      for _ in $(seq 1 100); do pgrep -f "$target/Contents/MacOS/" >/dev/null 2>&1 || break; sleep 0.2; done
      pkill -f "$target/Contents/MacOS/" 2>/dev/null || true
    fi
    rm -rf "${BACKUPS:?}/$bundle"
    mkdir -p "$BACKUPS/$bundle"
    mv "$target" "$BACKUPS/$bundle/$name $current.app" 2>/dev/null \
      || { rm -rf "$stage"; fail "macOS не дала заменить $name — разрешите Терминалу «Управление приложениями» в настройках конфиденциальности"; }
  fi
  mv "$stage/$name.app" "$target"
  rm -rf "$stage"
  xattr -dr com.apple.quarantine "$target" 2>/dev/null || true
  /System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister -f "$target" >/dev/null 2>&1 || true
  info "Готово: $target${current:+ (данные и настройки на месте)}"
  if [ "$was_running" = 1 ] || { [ "$id" = harbor ] && [ -z "${HARBOR_NO_LAUNCH:-}" ]; }; then
    open "$target"
  fi
}

MODE="harbor"
WANTED=()
for arg in "$@"; do
  case "$arg" in
    --all) MODE="all" ;;
    --update) MODE="update" ;;
    --list) MODE="list" ;;
    -*) fail "Неизвестный параметр $arg" ;;
    *) MODE="some"; WANTED+=("$(echo "$arg" | tr '[:upper:]' '[:lower:]')") ;;
  esac
done

found=0
while IFS=$'\t' read -r id name bundle version build url sum available tagline; do
  [ -n "$id" ] || continue
  if [ "$MODE" = "list" ]; then
    installed="$(locate "$bundle" "$name")"
    state="не опубликован"
    [ "$available" = 1 ] && state="$version ($build)"
    [ -n "$installed" ] && state="$state · установлен $(plist "$installed" CFBundleShortVersionString) ($(plist "$installed" CFBundleVersion))"
    printf '  %-8s %-34s %s\n' "$name" "$state" "$tagline"
    found=1
    continue
  fi
  case "$MODE" in
    harbor) [ "$id" = harbor ] || continue ;;
    some) printf '%s\n' "${WANTED[@]}" | grep -qx "$id" || continue ;;
    update) [ -n "$(locate "$bundle" "$name")" ] || continue ;;
  esac
  found=1
  if [ "$available" != 1 ]; then info "$name ещё не опубликован"; continue; fi
  install_app "$id" "$name" "$bundle" "$version" "$build" "$url" "$sum"
done < "$WORK/apps.tsv"

[ "$found" = 1 ] || fail "В каталоге нет таких приложений (список: --list)"
