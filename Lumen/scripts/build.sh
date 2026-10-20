#!/usr/bin/env bash
# Сборка Lumen.app и Lumen.dmg.
#   ./scripts/build.sh            — релизная сборка под Apple Silicon → dist/Lumen.app + dist/Lumen.dmg
#   ./scripts/build.sh --no-dmg   — только .app
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

NAME="Lumen"
BUNDLE_ID="app.lumen.player"
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
VIDEO_EXTS=(mp4 m4v mov qt 3gp 3g2 mkv mk3d webm)
AUDIO_EXTS=(mka m4a mp3 aac wav aif aiff caf flac ac3 eac3)
ext_array() { local out=""; for e in "$@"; do out+="<string>$e</string>"; done; echo "$out"; }

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
  <key>LSApplicationCategoryType</key><string>public.app-category.entertainment</string>
  <key>NSHighResolutionCapable</key><true/>
  <key>NSSupportsAutomaticGraphicsSwitching</key><true/>
  <key>NSPrincipalClass</key><string>NSApplication</string>
  <key>NSHumanReadableCopyright</key><string>Lumen — видеоплеер для macOS</string>
  <key>NSDownloadsFolderUsageDescription</key><string>Lumen открывает видео из папки «Загрузки» и показывает их обложки в библиотеке.</string>
  <key>NSDesktopFolderUsageDescription</key><string>Lumen открывает видео с Рабочего стола и показывает их обложки в библиотеке.</string>
  <key>NSDocumentsFolderUsageDescription</key><string>Lumen открывает видео из папки «Документы» и показывает их обложки в библиотеке.</string>
  <key>NSRemovableVolumesUsageDescription</key><string>Lumen воспроизводит видео с внешних дисков.</string>
  <key>NSNetworkVolumesUsageDescription</key><string>Lumen воспроизводит видео с сетевых дисков.</string>
  <key>UTImportedTypeDeclarations</key>
  <array>
    <dict>
      <key>UTTypeIdentifier</key><string>org.matroska.mkv</string>
      <key>UTTypeDescription</key><string>Видео Matroska</string>
      <key>UTTypeConformsTo</key><array><string>public.movie</string></array>
      <key>UTTypeTagSpecification</key><dict><key>public.filename-extension</key><array><string>mkv</string><string>mk3d</string></array></dict>
    </dict>
    <dict>
      <key>UTTypeIdentifier</key><string>org.webmproject.webm</string>
      <key>UTTypeDescription</key><string>Видео WebM</string>
      <key>UTTypeConformsTo</key><array><string>public.movie</string></array>
      <key>UTTypeTagSpecification</key><dict><key>public.filename-extension</key><array><string>webm</string></array></dict>
    </dict>
    <dict>
      <key>UTTypeIdentifier</key><string>org.matroska.mka</string>
      <key>UTTypeDescription</key><string>Аудио Matroska</string>
      <key>UTTypeConformsTo</key><array><string>public.audio</string></array>
      <key>UTTypeTagSpecification</key><dict><key>public.filename-extension</key><array><string>mka</string></array></dict>
    </dict>
  </array>
  <key>CFBundleDocumentTypes</key>
  <array>
    <dict>
      <key>CFBundleTypeName</key><string>Видео</string>
      <key>CFBundleTypeRole</key><string>Viewer</string>
      <key>LSHandlerRank</key><string>Alternate</string>
      <key>LSItemContentTypes</key>
      <array>
        <string>public.movie</string>
        <string>public.mpeg-4</string>
        <string>com.apple.quicktime-movie</string>
        <string>org.matroska.mkv</string>
        <string>org.webmproject.webm</string>
      </array>
      <key>CFBundleTypeExtensions</key><array>$(ext_array "${VIDEO_EXTS[@]}")</array>
    </dict>
    <dict>
      <key>CFBundleTypeName</key><string>Аудио</string>
      <key>CFBundleTypeRole</key><string>Viewer</string>
      <key>LSHandlerRank</key><string>Alternate</string>
      <key>LSItemContentTypes</key><array><string>public.audio</string><string>org.matroska.mka</string></array>
      <key>CFBundleTypeExtensions</key><array>$(ext_array "${AUDIO_EXTS[@]}")</array>
    </dict>
  </array>
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
