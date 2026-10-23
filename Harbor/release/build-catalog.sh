#!/usr/bin/env bash
# Сборка catalog.json из описаний (Harbor/Catalog/apps.json) и манифестов опубликованных сборок.
# Запускается в CI после каждой публикации (по одному за раз) и вручную.
set -euo pipefail

REPO="${GITHUB_REPOSITORY:?}"
TAG="catalog"
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${RUNNER_TEMP:-/tmp}/harbor-catalog"
rm -rf "$WORK"
mkdir -p "$WORK/manifests"

gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1 || \
  gh release create "$TAG" --repo "$REPO" --title "Каталог Harbor" --latest=false --notes "Сборки приложений для Harbor."
gh release view "$TAG" --repo "$REPO" --json assets --jq '.assets[].name' > "$WORK/assets.txt"
for id in $(python3 -c 'import json,sys; print(" ".join(a["id"] for a in json.load(open(sys.argv[1]))))' "$HERE/../Catalog/apps.json"); do
  if grep -qx "$id.json" "$WORK/assets.txt"; then
    gh release download "$TAG" --repo "$REPO" -p "$id.json" -D "$WORK/manifests" --clobber
  fi
done

python3 - "$HERE/../Catalog/apps.json" "$WORK/manifests" "$WORK/catalog.json" "https://github.com/$REPO/releases/download/$TAG/" "$WORK/assets.txt" <<'PY'
import json, os, sys, datetime
static_path, manifests, out, base, assets_path = sys.argv[1:6]
assets = set(l.strip() for l in open(assets_path))
apps, signers = [], []
for entry in json.load(open(static_path, encoding="utf-8")):
    app = {k: v for k, v in entry.items() if k != "dir"}
    path = os.path.join(manifests, entry["id"] + ".json")
    m = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else None
    if m and m.get("asset") in assets:
        app.update({
            "available": True,
            "version": m["version"], "build": m["build"],
            "url": base + m["asset"], "sha256": m["sha256"], "size": m["size"],
            "minimumSystem": m.get("minimumSystem") or None,
            "icon": base + m["icon"] if m.get("icon") else None,
            "screenshots": [base + s for s in m.get("screenshots", []) if s in assets],
            "changes": m.get("changes", []), "commit": m.get("commit"), "date": m.get("date"),
            "signer": m.get("signer") or None,
        })
        if m.get("signer"):
            signers.append(m["signer"])
    else:
        app["available"] = False
    apps.append(app)
catalog = {
    "schema": 1,
    "generated": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "signer": max(set(signers), key=signers.count) if signers else None,
    "apps": apps,
}
json.dump(catalog, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print("\n".join(f"  {a['name']}: " + (f"{a['version']} ({a['build']})" if a["available"] else "не опубликован") for a in apps))
PY

cp "$HERE/../install.sh" "$WORK/install.sh"
gh release upload "$TAG" --repo "$REPO" --clobber "$WORK/catalog.json" "$WORK/install.sh"
echo "Каталог обновлён: https://github.com/$REPO/releases/download/$TAG/catalog.json"
