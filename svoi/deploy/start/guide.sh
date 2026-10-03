#!/bin/sh
# Writes the plain-language start instructions into a release folder.
#   guide.sh <os> <arch> <dir>      os: windows | darwin | linux | freebsd | android
# Called by `make dist`. Russian first. Windows gets CRLF line ends and a UTF-8 BOM so that every
# Notepad shows the text correctly; macOS also gets a launcher that can be double-clicked.
set -eu
os=$1
arch=$2
dir=$3
here=$(cd "$(dirname "$0")" && pwd)
name="КАК ЗАПУСТИТЬ.txt"
case $os in
  windows) body="windows.txt" ;;
  darwin)  body="macos.txt" ;;
  android) body="android.txt" ;;
  linux)   if [ "$arch" = arm64 ]; then body="linux.txt android.txt"; else body="linux.txt"; fi ;;
  *)       body="linux.txt" ;;
esac
tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT
{
  cat "$here/common-head.txt"
  first=1
  for b in $body; do
    [ $first = 1 ] || echo
    first=0
    cat "$here/$b"
  done
  cat "$here/common-tail.txt"
} | sed "s/svoi-linux-arm64/svoi-$os-$arch/g" > "$tmp"
if [ "$os" = windows ]; then
  { printf '\357\273\277'; sed 's/$/\r/' "$tmp"; } > "$dir/$name"
else
  cp "$tmp" "$dir/$name"
fi
chmod 644 "$dir/$name"
if [ "$os" = darwin ]; then
  cp "$here/Start-Svoi.command" "$dir/Start-Svoi.command"
  chmod 755 "$dir/Start-Svoi.command"
fi
