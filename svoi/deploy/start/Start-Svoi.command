#!/bin/sh
# Double-click (the first time: right-click → Open) to start Svoi in a Terminal window.
cd "$(dirname "$0")" || exit 1
# The folder came from a download: let macOS run the program that sits next to this file.
xattr -dr com.apple.quarantine . 2>/dev/null
chmod +x ./svoi 2>/dev/null
exec ./svoi
