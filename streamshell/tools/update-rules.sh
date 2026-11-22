#!/bin/bash
# Refreshes the ad-blocking page scripts from the latest uBlock Origin Lite release.
#
# uBlock Origin Lite (GPLv3, https://github.com/uBlockOrigin/uBOL-home) publishes its
# scriptlet rules as standalone JavaScript. StreamShell injects these three files, in
# this order, at the start of every HBO Max document:
#   scriptlet-begin.js   the scriptlet library
#   ublock-filters.js    the rules (picks the ones matching the current hostname)
#   scriptlet-end.js     cleanup
#
# Usage: tools/update-rules.sh        then rebuild and reinstall the app.
set -euo pipefail
cd "$(dirname "$0")/.."

OUT=app/src/main/assets/rules
SRC=rulesets/scripting/scriptlet/main
FILES=(scriptlet-begin.js ublock-filters.js scriptlet-end.js)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

curl -fsSL --max-time 30 -o "$TMP/release.json" \
  https://api.github.com/repos/uBlockOrigin/uBOL-home/releases/latest
TAG=$(python3 -I -c 'import json,sys; print(json.load(open(sys.argv[1]))["tag_name"])' "$TMP/release.json")
URL=$(python3 -I -c '
import json, sys
assets = json.load(open(sys.argv[1]))["assets"]
print(next(a["browser_download_url"] for a in assets if a["name"].endswith(".chromium.zip")))
' "$TMP/release.json")

echo "uBlock Origin Lite $TAG"
curl -fsSL --max-time 180 -o "$TMP/ubol.zip" "$URL"
for f in "${FILES[@]}"; do
  bsdtar -xf "$TMP/ubol.zip" -C "$TMP" "$SRC/$f"
  test -s "$TMP/$SRC/$f" || { echo "missing or empty: $f" >&2; exit 1; }
  # Refuse a file that is not valid JavaScript rather than ship a broken blocker.
  node --check "$TMP/$SRC/$f"
done
grep -q '"play.hbomax.com"' "$TMP/$SRC/ublock-filters.js" \
  || { echo "this release has no HBO Max rules; keeping the current files" >&2; exit 1; }

mkdir -p "$OUT"
for f in "${FILES[@]}"; do cp "$TMP/$SRC/$f" "$OUT/$f"; done
echo "$TAG" > "$OUT/VERSION"
echo "Rules updated in $OUT"
