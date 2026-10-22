#!/usr/bin/env bash
# Сборка Ward.app и Ward.dmg.
#   ./scripts/build.sh            — списки фильтров + приложение → dist/Ward.app + dist/Ward.dmg
#   ./scripts/build.sh --no-dmg   — только .app
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

NAME="Ward"
BUNDLE_ID="app.ward.browser"
VERSION="${VERSION:-1.0.0}"
BUILD_NUMBER="${BUILD_NUMBER:-1}"
DIST="$ROOT/dist"
APP="$DIST/$NAME.app"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

step() { printf '\n\033[1;35m◆ %s\033[0m\n' "$1"; }

step "Компиляция (Swift $(swift --version 2>/dev/null | head -1 | sed 's/.*version \([0-9.]*\).*/\1/'), arm64, -O)"
swift build -c release --arch arm64
BIN_DIR="$(swift build -c release --arch arm64 --show-bin-path)"

step "Бандл"
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp "$BIN_DIR/$NAME" "$APP/Contents/MacOS/$NAME"
strip -x "$APP/Contents/MacOS/$NAME" 2>/dev/null || true

step "Списки фильтров"
if [ ! -f "$ROOT/Resources/Filters/DATE" ] || [ -n "${REFRESH_LISTS:-}" ]; then "$ROOT/scripts/fetch-lists.sh"; fi
mkdir -p "$APP/Contents/Resources/Filters"
cp "$ROOT"/Resources/Filters/*.txt "$ROOT/Resources/Filters/DATE" "$APP/Contents/Resources/Filters/" 2>/dev/null || true
echo "  в бандле: $(ls "$APP/Contents/Resources/Filters" | grep -c txt) списков, $(du -sh "$APP/Contents/Resources/Filters" | cut -f1)"

step "Иконка"
swift scripts/make-icon.swift "$WORK"
iconutil -c icns "$WORK/AppIcon.iconset" -o "$APP/Contents/Resources/AppIcon.icns"
ICON_NAME_KEY=""
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
  <key>LSArchitecturePriority</key><array><string>arm64</string></array>
  <key>LSRequiresNativeExecution</key><true/>
  <key>LSApplicationCategoryType</key><string>public.app-category.productivity</string>
  <key>NSHighResolutionCapable</key><true/>
  <key>NSPrincipalClass</key><string>NSApplication</string>
  <key>CFBundleURLTypes</key>
  <array>
    <dict>
      <key>CFBundleURLName</key><string>Web site URL</string>
      <key>CFBundleTypeRole</key><string>Viewer</string>
      <key>CFBundleURLSchemes</key><array><string>http</string><string>https</string></array>
    </dict>
  </array>
  <key>CFBundleDocumentTypes</key>
  <array>
    <dict>
      <key>CFBundleTypeName</key><string>HTML document</string>
      <key>CFBundleTypeRole</key><string>Viewer</string>
      <key>LSHandlerRank</key><string>Default</string>
      <key>LSItemContentTypes</key><array><string>public.html</string><string>public.xhtml</string></array>
    </dict>
  </array>
  <!-- Браузеру нужны и сайты без HTTPS: Ward сначала пробует HTTPS и предупреждает, если сайт без защиты. -->
  <key>NSAppTransportSecurity</key><dict><key>NSAllowsArbitraryLoadsInWebContent</key><true/></dict>
  <key>NSCameraUsageDescription</key><string>Сайт просит доступ к камере — Ward спросит вас для каждого сайта отдельно.</string>
  <key>NSMicrophoneUsageDescription</key><string>Сайт просит доступ к микрофону — Ward спросит вас для каждого сайта отдельно.</string>
  <key>NSHumanReadableCopyright</key><string>Ward — браузер, который не даёт следить. Движок WebKit; списки фильтров EasyList, EasyPrivacy, uBlock Origin, RU AdList, URLhaus, Peter Lowe — их авторов.</string>
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
