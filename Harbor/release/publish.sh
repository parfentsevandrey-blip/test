#!/usr/bin/env bash
# Публикация собранного приложения в каталог Harbor (CI, macOS, после зелёных самопроверок).
#   Harbor/release/publish.sh <id> <путь/Name.app> [скриншот.png …]
#
# Всё лежит в одном релизе с тегом «catalog»: архив Name-<версия>-<сборка>.zip, иконка, скриншоты
# и манифест <id>.json. Каталог (catalog.json) собирает из манифестов build-catalog.sh.
#
# Защита от отката: если ветка этой сборки не содержит коммит прошлой публикации (сборка из старой
# или параллельной ветки), публикация пропускается.
set -euo pipefail

ID="$1"
APP="$2"
shift 2
REPO="${GITHUB_REPOSITORY:?}"
SHA="${GITHUB_SHA:?}"
TAG="catalog"
NAME="$(basename "$APP" .app)"
WORK="${RUNNER_TEMP:-/tmp}/publish-$ID"
rm -rf "$WORK"
mkdir -p "$WORK"

plist() { /usr/libexec/PlistBuddy -c "Print :$1" "$APP/Contents/Info.plist" 2>/dev/null || true; }
VERSION="$(plist CFBundleShortVersionString)"
BUILD="$(plist CFBundleVersion)"
BUNDLE="$(plist CFBundleIdentifier)"
MINOS="$(plist LSMinimumSystemVersion)"

gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1 || \
  gh release create "$TAG" --repo "$REPO" --title "Каталог Harbor" --latest=false \
    --notes "Сборки приложений для Harbor. Установка Harbor одной командой в Терминале:
\`curl -fsSL https://github.com/$REPO/releases/download/$TAG/install.sh | bash\`" || true

# Прошлая публикация этого приложения.
PREV_COMMIT=""
PREV_BUILD=0
if gh release download "$TAG" --repo "$REPO" -p "$ID.json" -D "$WORK/prev" >/dev/null 2>&1; then
  PREV_COMMIT="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("commit",""))' "$WORK/prev/$ID.json")"
  PREV_BUILD="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("build",0))' "$WORK/prev/$ID.json")"
fi
CHANGES="$WORK/changes.txt"
if [ -n "$PREV_COMMIT" ]; then
  STATUS="$(gh api "repos/$REPO/compare/$PREV_COMMIT...$SHA" --jq .status 2>/dev/null || echo unknown)"
  case "$STATUS" in
    ahead) ;;
    identical) echo "Этот коммит уже опубликован ($ID $VERSION ($PREV_BUILD))"; exit 0 ;;
    *) echo "::warning::$ID не опубликован: ветка не продолжает прошлую публикацию ($STATUS)"; exit 0 ;;
  esac
  gh api "repos/$REPO/compare/$PREV_COMMIT...$SHA" \
    --jq '.commits[].commit.message | split("\n")[0]' 2>/dev/null | grep -v '\[skip ci\]' | tail -12 > "$CHANGES" || true
fi
[ -s "$CHANGES" ] || git log -1 --format=%s > "$CHANGES" 2>/dev/null || echo "Новая сборка" > "$CHANGES"

ZIP="$NAME-$VERSION-$BUILD.zip"
ditto -c -k --sequesterRsrc --keepParent "$APP" "$WORK/$ZIP"
SUM="$(shasum -a 256 "$WORK/$ZIP" | cut -d' ' -f1)"
SIZE="$(stat -f%z "$WORK/$ZIP")"

# Сертификат подписи: SHA-1 корневого (последнего) сертификата цепочки; пусто — ad-hoc.
SIGNER=""
(cd "$WORK" && codesign -d --extract-certificates="$WORK/cert" "$APP" >/dev/null 2>&1) || true
LAST="$(ls "$WORK"/cert* 2>/dev/null | sort -V | tail -1 || true)"
[ -n "$LAST" ] && SIGNER="$(shasum -a 1 "$LAST" | cut -d' ' -f1 | tr '[:lower:]' '[:upper:]')"
if [ -n "${CODESIGN_IDENTITY:-}" ] && [ "$SIGNER" != "$CODESIGN_IDENTITY" ]; then
  echo "::error::$NAME подписан не тем сертификатом ($SIGNER вместо $CODESIGN_IDENTITY)"
  exit 1
fi

ASSETS=("$WORK/$ZIP")
ICON=""
if [ -f "$APP/Contents/Resources/AppIcon.icns" ]; then
  sips -s format png -Z 512 "$APP/Contents/Resources/AppIcon.icns" --out "$WORK/$NAME-icon.png" >/dev/null
  ICON="$NAME-icon.png"
  ASSETS+=("$WORK/$ICON")
fi
SHOTS=()
n=0
for shot in "$@"; do
  [ -f "$shot" ] || continue
  n=$((n + 1))
  sips -Z 1600 "$shot" --out "$WORK/$NAME-shot-$n.png" >/dev/null
  SHOTS+=("$NAME-shot-$n.png")
  ASSETS+=("$WORK/$NAME-shot-$n.png")
done

python3 - "$WORK/$ID.json" <<PY
import json, sys
changes = [l.strip() for l in open("$CHANGES", encoding="utf-8") if l.strip()]
manifest = {
    "id": "$ID", "name": "$NAME", "bundleID": "$BUNDLE",
    "version": "$VERSION", "build": int("$BUILD" or 0),
    "asset": "$ZIP", "sha256": "$SUM", "size": int("$SIZE"),
    "minimumSystem": "$MINOS", "icon": "$ICON" or None,
    "screenshots": [s for s in "${SHOTS[*]:-}".split(" ") if s],
    "changes": changes, "commit": "$SHA", "branch": "${GITHUB_REF_NAME:-}",
    "date": "$(date -u +%Y-%m-%dT%H:%M:%SZ)", "signer": "$SIGNER",
}
json.dump(manifest, open(sys.argv[1], "w"), ensure_ascii=False, indent=2)
PY

# Сначала архив и картинки, потом манифест: каталог никогда не ссылается на ещё не загруженный архив.
gh release upload "$TAG" --repo "$REPO" --clobber "${ASSETS[@]}"
gh release upload "$TAG" --repo "$REPO" --clobber "$WORK/$ID.json"
echo "Опубликовано: $NAME $VERSION ($BUILD), $SIZE байт, sha256 $SUM"

# Старые архивы этого приложения: оставляем два последних.
gh release view "$TAG" --repo "$REPO" --json assets --jq ".assets[].name" > "$WORK/assets.txt"
python3 - "$NAME" "$WORK/assets.txt" <<'PY' | while read -r old; do gh release delete-asset "$TAG" "$old" --repo "$REPO" -y || true; done
import re, sys
name, items = sys.argv[1], []
for line in open(sys.argv[2]):
    m = re.fullmatch(re.escape(name) + r"-[0-9.]+-([0-9]+)\.zip", line.strip())
    if m:
        items.append((int(m.group(1)), line.strip()))
for _, old in sorted(items)[:-2]:
    print(old)
PY
