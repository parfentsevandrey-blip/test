#!/usr/bin/env bash
# Сборка Sweep.app и Sweep.dmg.
#   ./scripts/build.sh            — релизная сборка под Apple Silicon → dist/Sweep.app + dist/Sweep.dmg
#   ./scripts/build.sh --no-dmg   — только .app
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

NAME="Sweep"
BUNDLE_ID="app.sweep.cleaner"
VERSION="${VERSION:-1.0.0}"
BUILD_NUMBER="${BUILD_NUMBER:-1}"
DIST="$ROOT/dist"
APP="$DIST/$NAME.app"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

step() { printf '\n\033[1;35m◆ %s\033[0m\n' "$1"; }

step "Компиляция (Swift $(swift --version 2>/dev/null | head -1 | sed 's/.*version \([0-9.]*\).*/\1/'), arm64, -O)"
swift build -c release --arch arm64
BIN="$(swift build -c release --arch arm64 --show-bin-path)/$NAME"

step "Бандл"
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp "$BIN" "$APP/Contents/MacOS/$NAME"
strip -x "$APP/Contents/MacOS/$NAME" 2>/dev/null || true

step "Иконка"
swift scripts/make-icon.swift "$WORK"
iconutil -c icns "$WORK/AppIcon.iconset" -o "$APP/Contents/Resources/AppIcon.icns"
ICON_NAME_KEY=""
# Liquid Glass-иконка из слоёв Icon Composer (macOS 26+). Если actool её не понял —
# остаётся обычная .icns, сборка не падает.
if xcrun actool "$WORK/AppIcon.icon" --compile "$APP/Contents/Resources" \
      --app-icon AppIcon --include-all-app-icons \
      --platform macosx --target-device mac --minimum-deployment-target 26.0 \
      --output-partial-info-plist "$WORK/icon-partial.plist" \
      --output-format human-readable-text --errors >"$WORK/actool.log" 2>&1 \
   && [ -f "$APP/Contents/Resources/Assets.car" ]; then
  echo "  Assets.car с Liquid Glass-иконкой собран"
  ICON_NAME_KEY="<key>CFBundleIconName</key><string>AppIcon</string>"
else
  echo "  actool не собрал .icon — используется AppIcon.icns"
  sed 's/^/    /' "$WORK/actool.log" | head -20 || true
fi

step "Info.plist"
cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleName</key><string>$NAME</string>
  <key>CFBundleDisplayName</key><string>$NAME</string>
  <key>CFBundleIdentifier</key><string>$BUNDLE_ID</string>
  <key>CFBundleExecutable</key><string>$NAME</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>$VERSION</string>
  <key>CFBundleVersion</key><string>$BUILD_NUMBER</string>
  <key>CFBundleDevelopmentRegion</key><string>ru</string>
  <key>CFBundleIconFile</key><string>AppIcon</string>
  $ICON_NAME_KEY
  <key>LSMinimumSystemVersion</key><string>26.0</string>
  <key>LSApplicationCategoryType</key><string>public.app-category.utilities</string>
  <key>NSHighResolutionCapable</key><true/>
  <key>NSPrincipalClass</key><string>NSApplication</string>
  <key>NSHumanReadableCopyright</key><string>Sweep — очистка и обслуживание Mac</string>
  <key>NSDownloadsFolderUsageDescription</key><string>Sweep ищет забытые установщики, крупные файлы и дубликаты в Загрузках. Без вашего подтверждения ничего не удаляется.</string>
  <key>NSDesktopFolderUsageDescription</key><string>Sweep ищет крупные файлы и дубликаты на Рабочем столе. Без вашего подтверждения ничего не удаляется.</string>
  <key>NSDocumentsFolderUsageDescription</key><string>Sweep ищет крупные файлы и дубликаты в Документах. Без вашего подтверждения ничего не удаляется.</string>
  <key>NSRemovableVolumesUsageDescription</key><string>Sweep показывает Корзину и занятое место на внешних дисках.</string>
  <key>NSNetworkVolumesUsageDescription</key><string>Sweep показывает занятое место на сетевых дисках, если вы их выбрали.</string>
  <key>NSAppleEventsUsageDescription</key><string>Sweep просит Finder очистить Корзину, если у него нет полного доступа к диску.</string>
  <key>NSSystemAdministrationUsageDescription</key><string>Пароль нужен только для системных файлов программ и команд обслуживания.</string>
</dict>
</plist>
PLIST
plutil -lint "$APP/Contents/Info.plist"

step "Подпись (ad-hoc)"
codesign --force --sign - --timestamp=none "$APP"
codesign --verify --strict "$APP" && echo "  подпись в порядке"

if [[ "${1:-}" != "--no-dmg" ]]; then
  step "Образ диска"
  "$ROOT/scripts/package-dmg.sh" "$APP" "$DIST/$NAME.dmg" "$VERSION"
fi

step "Готово"
du -sh "$APP" | sed 's/^/  /'
[ -f "$DIST/$NAME.dmg" ] && du -sh "$DIST/$NAME.dmg" | sed 's/^/  /'
