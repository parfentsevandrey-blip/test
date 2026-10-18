#!/usr/bin/env bash
# Собирает ROM Forge.app (универсальный бинарник arm64 + x86_64) в папку dist/.
# Использование: ./scripts/build-app.sh [--bundle-tools]
#   --bundle-tools  положить Android Platform Tools внутрь .app (без этого
#                   приложение найдёт их на Mac или скачает при первом запуске).
set -euo pipefail

cd "$(dirname "$0")/.."
APP_NAME="ROM Forge"
BUNDLE_ID="app.romforge.mac"
VERSION="${VERSION:-1.0.0}"
DIST="dist"
APP="$DIST/$APP_NAME.app"

ARCHS=(--arch arm64 --arch x86_64)

echo "▸ swift build (release)"
swift build -c release "${ARCHS[@]}"
BIN_DIR="$(swift build -c release "${ARCHS[@]}" --show-bin-path)"

echo "▸ Сборка бандла"
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp "$BIN_DIR/ROMForge" "$APP/Contents/MacOS/ROMForge"

cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleName</key><string>$APP_NAME</string>
  <key>CFBundleDisplayName</key><string>$APP_NAME</string>
  <key>CFBundleIdentifier</key><string>$BUNDLE_ID</string>
  <key>CFBundleExecutable</key><string>ROMForge</string>
  <key>CFBundleIconFile</key><string>AppIcon</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>$VERSION</string>
  <key>CFBundleVersion</key><string>$VERSION</string>
  <key>CFBundleDevelopmentRegion</key><string>ru</string>
  <key>LSMinimumSystemVersion</key><string>14.0</string>
  <key>LSApplicationCategoryType</key><string>public.app-category.utilities</string>
  <key>NSHighResolutionCapable</key><true/>
  <key>NSPrincipalClass</key><string>NSApplication</string>
</dict>
</plist>
PLIST

echo "▸ Иконка"
ICONSET="$(mktemp -d)/AppIcon.iconset"
if swift scripts/make-icon.swift "$ICONSET" && iconutil -c icns "$ICONSET" -o "$APP/Contents/Resources/AppIcon.icns"; then
  echo "  иконка создана"
else
  echo "  не удалось создать иконку — пропускаем"
fi

if [[ "${1:-}" == "--bundle-tools" ]]; then
  echo "▸ Загрузка Android Platform Tools"
  TMP="$(mktemp -d)"
  curl -fsSL -o "$TMP/pt.zip" https://dl.google.com/android/repository/platform-tools-latest-darwin.zip
  ditto -x -k "$TMP/pt.zip" "$APP/Contents/Resources/"
  rm -rf "$TMP"
fi

echo "▸ Ad-hoc подпись"
codesign --force --deep --sign - "$APP"

echo "✓ Готово: $APP"
