#!/usr/bin/env bash
# Сборка Vessel.app и Vessel.dmg.
#   ./scripts/build.sh            — релизная сборка под Apple Silicon → dist/Vessel.app + dist/Vessel.dmg
#   ./scripts/build.sh --no-dmg   — только .app
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

NAME="Vessel"
BUNDLE_ID="app.vessel.vm"
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
  <key>LSApplicationCategoryType</key><string>public.app-category.developer-tools</string>
  <key>NSHighResolutionCapable</key><true/>
  <key>NSPrincipalClass</key><string>NSApplication</string>
  <key>NSHumanReadableCopyright</key><string>Vessel — другие системы на Mac</string>
  <key>NSMicrophoneUsageDescription</key><string>Микрофон Mac передаётся в виртуальную машину, только если вы включили его в её настройках.</string>
</dict>
</plist>
PLIST
plutil -lint "$APP/Contents/Info.plist"

step "Подпись"
# Без права com.apple.security.virtualization Virtualization.framework не создаст машину.
cat > "$WORK/Vessel.entitlements" <<ENT
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>com.apple.security.virtualization</key><true/>
</dict>
</plist>
ENT
codesign --force --sign "${CODESIGN_IDENTITY:--}" --timestamp=none --entitlements "$WORK/Vessel.entitlements" "$APP"
codesign -d --entitlements - "$APP" 2>/dev/null | grep -q virtualization && echo "  право на виртуализацию есть"
codesign --verify --strict "$APP" && echo "  подпись в порядке"

if [[ "${1:-}" != "--no-dmg" ]]; then
  step "Образ диска"
  "$ROOT/scripts/package-dmg.sh" "$APP" "$DIST/$NAME.dmg" "$VERSION"
fi

step "Готово"
du -sh "$APP" | sed 's/^/  /'
[ -f "$DIST/$NAME.dmg" ] && du -sh "$DIST/$NAME.dmg" | sed 's/^/  /'
