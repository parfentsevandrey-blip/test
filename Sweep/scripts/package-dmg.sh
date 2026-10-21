#!/usr/bin/env bash
# Упаковка .app в DMG с оформленным окном «перетащите в Программы».
#   ./scripts/package-dmg.sh <Sweep.app> <out.dmg> [version]
set -euo pipefail

APP="$1"
OUT="$2"
VERSION="${3:-1.0.0}"
NAME="$(basename "$APP" .app)"
VOLUME="$NAME $VERSION"
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d)"
MOUNT="$WORK/mnt"
trap 'hdiutil detach "$MOUNT" -quiet -force >/dev/null 2>&1 || true; rm -rf "$WORK"' EXIT

mkdir -p "$WORK/stage/.background" "$MOUNT"
cp -R "$APP" "$WORK/stage/"
ln -s /Applications "$WORK/stage/Applications"
swift "$HERE/dmg-background.swift" "$WORK/bg1x.png" 600 400 1 "$NAME"
swift "$HERE/dmg-background.swift" "$WORK/bg2x.png" 600 400 2 "$NAME"
tiffutil -cathidpicheck "$WORK/bg1x.png" "$WORK/bg2x.png" -out "$WORK/stage/.background/background.tiff" >/dev/null 2>&1 \
  || cp "$WORK/bg2x.png" "$WORK/stage/.background/background.tiff"

SIZE_MB=$(( $(du -sm "$WORK/stage" | cut -f1) + 40 ))
hdiutil create -quiet -srcfolder "$WORK/stage" -volname "$VOLUME" -fs HFS+ -format UDRW -size "${SIZE_MB}m" "$WORK/rw.dmg"
hdiutil attach -quiet -readwrite -noverify -noautoopen -mountpoint "$MOUNT" "$WORK/rw.dmg"

# Раскладка окна Finder. На машине без графической сессии AppleScript может не сработать —
# тогда образ просто будет без оформления (ждём не дольше минуты).
cat > "$WORK/layout.applescript" <<APPLESCRIPT
tell application "Finder"
  set d to disk "$VOLUME"
  open d
  set w to container window of d
  set current view of w to icon view
  set toolbar visible of w to false
  set statusbar visible of w to false
  set bounds of w to {200, 120, 800, 520}
  set o to icon view options of w
  set arrangement of o to not arranged
  set icon size of o to 112
  set text size of o to 13
  set background picture of o to file ".background:background.tiff"
  set position of item "$NAME.app" of w to {160, 200}
  set position of item "Applications" of w to {440, 200}
  update d without registering applications
  delay 1
  close w
end tell
APPLESCRIPT
osascript "$WORK/layout.applescript" >/dev/null 2>&1 &
LAYOUT_PID=$!
( sleep 60; kill "$LAYOUT_PID" 2>/dev/null ) &
WATCHDOG=$!
if wait "$LAYOUT_PID"; then
  echo "  окно DMG оформлено"
else
  echo "  оформление окна DMG пропущено (нет доступа к Finder)"
fi
kill "$WATCHDOG" 2>/dev/null || true

cp "$WORK/stage/$NAME.app/Contents/Resources/AppIcon.icns" "$MOUNT/.VolumeIcon.icns" 2>/dev/null && SetFile -a C "$MOUNT" 2>/dev/null || true
sync
hdiutil detach "$MOUNT" -quiet -force
rm -f "$OUT"
hdiutil convert -quiet "$WORK/rw.dmg" -format ULFO -o "$OUT"
hdiutil verify -quiet "$OUT"
echo "  $OUT"
