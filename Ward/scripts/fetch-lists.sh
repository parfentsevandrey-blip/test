#!/usr/bin/env bash
# Списки фильтров на момент сборки — в бандл, чтобы защита работала с первого запуска даже без сети.
# Потом Ward обновляет их сам раз в сутки. Адреса — те же, что в Sources/Ward/Core/FilterLists.swift.
set -uo pipefail
OUT="${1:-$(cd "$(dirname "$0")/.." && pwd)/Resources/Filters}"
mkdir -p "$OUT"
fetch() {
  local id="$1" url="$2"
  if curl -fsSL --retry 3 --max-time 60 -o "$OUT/$id.txt.tmp" "$url" && [ -s "$OUT/$id.txt.tmp" ]; then
    mv "$OUT/$id.txt.tmp" "$OUT/$id.txt"
    printf '  %-16s %6s строк\n' "$id" "$(wc -l < "$OUT/$id.txt" | tr -d ' ')"
  else
    rm -f "$OUT/$id.txt.tmp"
    echo "  $id: не скачался — Ward скачает его сам при первом запуске"
  fi
}
fetch easylist       "https://easylist.to/easylist/easylist.txt"
fetch easyprivacy    "https://easylist.to/easylist/easyprivacy.txt"
fetch ubo-filters    "https://ublockorigin.github.io/uAssets/filters/filters.txt"
fetch ubo-privacy    "https://ublockorigin.github.io/uAssets/filters/privacy.txt"
fetch ubo-badware    "https://ublockorigin.github.io/uAssets/filters/badware.txt"
fetch ubo-unbreak    "https://ublockorigin.github.io/uAssets/filters/unbreak.txt"
fetch peterlowe      "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=adblockplus&showintro=0&mimetype=plaintext"
fetch ruadlist       "https://easylist-downloads.adblockplus.org/advblock.txt"
fetch urlhaus        "https://malware-filter.gitlab.io/malware-filter/urlhaus-filter-ag-online.txt"
fetch cookies        "https://secure.fanboy.co.nz/fanboy-cookiemonster.txt"
fetch annoyances     "https://secure.fanboy.co.nz/fanboy-annoyance.txt"
fetch adguard-url    "https://filters.adtidy.org/extension/ublock/filters/17.txt"
date -u +%Y-%m-%dT%H:%M:%SZ > "$OUT/DATE"
exit 0
