#!/usr/bin/env bash
# Упаковывает dist/ROM Forge.app в dist/ROM-Forge.dmg с ярлыком «Программы»
# для установки перетаскиванием. Сначала запустите ./scripts/build-app.sh.
set -euo pipefail

cd "$(dirname "$0")/.."
APP="dist/ROM Forge.app"
DMG="dist/ROM-Forge.dmg"
[[ -d "$APP" ]] || { echo "Нет $APP — сначала ./scripts/build-app.sh"; exit 1; }

STAGE="$(mktemp -d)"
cp -R "$APP" "$STAGE/"
ln -s /Applications "$STAGE/Applications"

rm -f "$DMG"
hdiutil create -volname "ROM Forge" -srcfolder "$STAGE" -fs HFS+ -format UDZO -ov "$DMG"
rm -rf "$STAGE"
echo "✓ Готово: $DMG"
