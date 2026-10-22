#!/usr/bin/env bash
# Сборка ядра Xray-core из исходников (Go) для Apple Silicon.
#   ./scripts/build-core.sh        — собирает Vendor/xray, если там нет нужной версии
#   XRAY_TAG=v26.9.30 ./scripts/build-core.sh
#
# Результат (в .gitignore):
#   Vendor/xray/xray     — ядро (статический бинарник, без cgo)
#   Vendor/xray/LICENSE  — лицензия Xray-core (MPL-2.0)
#   Vendor/xray/TAG
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TAG="${XRAY_TAG:-$(cat "$ROOT/XRAY_VERSION")}"
OUT="$ROOT/Vendor/xray"
SRC="${XRAY_SRC:-$ROOT/Vendor/xray-src}"

step() { printf '\n\033[1;36m◆ %s\033[0m\n' "$1"; }

if [[ -f "$OUT/TAG" && "$(cat "$OUT/TAG")" == "$TAG" && -x "$OUT/xray" ]]; then
  echo "Ядро Xray $TAG уже собрано — пропускаю"
  exit 0
fi

command -v go >/dev/null || { echo "Нужен Go: brew install go"; exit 1; }

step "Исходники Xray-core $TAG"
if [[ ! -d "$SRC/.git" ]] || [[ "$(git -C "$SRC" describe --tags --exact-match 2>/dev/null)" != "$TAG" ]]; then
  rm -rf "$SRC"
  git clone --quiet --depth 1 --branch "$TAG" https://github.com/XTLS/Xray-core.git "$SRC"
fi
git -C "$SRC" log -1 --format='  %h %cd %s' --date=short

step "Компиляция ($(go version | cut -d' ' -f3) → нужная версия Go подтянется сама, darwin/arm64, без cgo)"
mkdir -p "$OUT"
# Как в официальных сборках проекта: без cgo, без путей сборки и идентификатора — воспроизводимо.
(cd "$SRC" && GOTOOLCHAIN=auto CGO_ENABLED=0 GOOS=darwin GOARCH=arm64 \
  go build -trimpath -buildvcs=false -ldflags "-s -w -buildid=" -o "$OUT/xray" ./main)
cp "$SRC/LICENSE" "$OUT/LICENSE"
echo "$TAG" > "$OUT/TAG"
file "$OUT/xray" 2>/dev/null | sed 's/^/  /' || true
du -h "$OUT/xray" | sed 's/^/  /'
echo "Готово: $OUT"
