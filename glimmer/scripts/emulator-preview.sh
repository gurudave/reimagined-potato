#!/usr/bin/env bash
# Installs the preview build on a running emulator, adds widgets through the
# launcher's own "add to home screen" dialog, and saves screenshots to look over.
# Usage: emulator-preview.sh <preview.apk> <output dir>
set -uo pipefail

APK="$1"
OUT="$2"
PKG=app.glimmer.widgets
mkdir -p "$OUT"

shot() {
  sleep "${2:-2}"
  adb exec-out screencap -p > "$OUT/$1.png"
}

# Prints the centre of the first on-screen element whose text or description matches $1.
find_text() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || return 1
  adb pull /sdcard/ui.xml "$OUT/ui.xml" > /dev/null 2>&1 || return 1
  python3 - "$1" "$OUT/ui.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
pattern = re.compile(sys.argv[1], re.I)
for node in ET.parse(sys.argv[2]).iter('node'):
    label = f"{node.get('text', '')} {node.get('content-desc', '')}".strip()
    if label and pattern.search(label):
        x1, y1, x2, y2 = map(int, re.findall(r'-?\d+', node.get('bounds', '')))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        sys.exit(0)
sys.exit(1)
PY
}

tap_text() {
  local xy
  xy=$(find_text "$1") || { echo "nothing on screen matches '$1'"; return 1; }
  # shellcheck disable=SC2086
  adb shell input tap $xy
}

adb install -r "$APK"
adb logcat -c

echo "== Rendering the widget gallery"
adb shell am start -W -n "$PKG/.DemoActivity" --es mode gallery
sleep 15
adb pull "/sdcard/Android/data/$PKG/files/preview" "$OUT/gallery" || echo "could not pull the gallery"

echo "== First launch"
adb shell am start -W -n "$PKG/.MainActivity"
shot 01-home-empty 5

echo "== Pinning widgets through the launcher"
for style in 0 1 2 3; do
  adb shell am start -W -n "$PKG/.DemoActivity" --es mode pin --ei style "$style"
  sleep 4
  if [ "$style" = 0 ]; then shot 02-pin-dialog 0; fi
  tap_text 'add automatically|add to home screen|^add$'
  sleep 4
done
adb shell input keyevent KEYCODE_HOME
shot 03-launcher 5

echo "== Tapping a widget to flip its photo"
if tap_text 'glimmer photo|photo widget|besties'; then
  shot 04-launcher-after-tap 4
fi

echo "== Back in the app"
adb shell am start -W --activity-clear-task -n "$PKG/.MainActivity"
shot 05-home-list 5
if tap_text 'tap to edit'; then
  shot 06-editor 5
  for n in 07 08 09; do
    adb shell input swipe 540 2050 540 1000 700
    shot "$n-editor-scrolled" 2
  done
  adb shell input keyevent KEYCODE_BACK
  sleep 2
fi

echo "== Trying the other vibes"
for vibe in 'lavender haze' 'matcha latte' 'midnight'; do
  if tap_text "vibe $vibe"; then
    shot "10-vibe-${vibe// /-}" 3
  fi
done
if tap_text 'new widget'; then
  shot 11-new-widget-editor 4
fi

adb logcat -d > "$OUT/logcat.txt"
echo "== Crashes, if any"
grep -A 25 'FATAL EXCEPTION' "$OUT/logcat.txt" | head -80 || true
grep -E 'GlimmerDemo|PhotoStore|WidgetUpdater' "$OUT/logcat.txt" | head -40 || true
exit 0
