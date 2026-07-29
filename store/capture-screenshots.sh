#!/usr/bin/env bash
#
# Capture the Play Store phone screenshot set from a connected device.
#
#   store/capture-screenshots.sh          # run from the repo root
#
# Expects the app INSTALLED AND ALREADY SIGNED IN, on a 1080x1920 device.
#
# Why 1080x1920: Play caps screenshot aspect at 2:1 ("the maximum dimension
# can't be more than twice the minimum"), so the stock emulator's 1080x2400
# (2.22:1) is REJECTED. 1080x1920 is also Google's recommended portrait size
# for promotion eligibility. Build a matching AVD with:
#
#   avdmanager create avd -n hadron_store_1080x1920 -d pixel_2 \
#     -k "system-images;android-36.1;google_apis_playstore;arm64-v8a"
#
# adb screencap emits RGBA; Play requires 24-bit PNG with no alpha, so every
# shot is converted at the end.
#
# The tap coordinates below are calibrated for 1080x1920 @ 420dpi. If the UI
# moves, re-shoot one frame and read the new positions off it rather than
# guessing — a stray tap opens a link in Chrome and you capture Chrome.
set -euo pipefail

PKG=com.hadronmemory.android
OUT=store/screenshots
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
mkdir -p "$OUT"

size=$("$ADB" shell wm size | tr -d '\r' | awk '{print $3}')
[ "$size" = "1080x1920" ] || echo "warning: device is $size, expected 1080x1920"

# SystemUI demo mode: fixed clock, full battery, wifi only, no notification
# icons — otherwise every shot carries the emulator's own status-bar clutter.
demo() { "$ADB" shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
"$ADB" shell settings put global sysui_demo_allowed 1
demo enter
demo clock -e hhmm 0930
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4
demo network -e mobile hide
demo notifications -e visible false

shot() { sleep "${2:-3}"; "$ADB" exec-out screencap -p > "$OUT/$1.png"; echo "  $1"; }

echo "capturing:"
"$ADB" shell am force-stop $PKG
"$ADB" shell am start -n $PKG/.MainActivity >/dev/null
shot 01-memories 7

"$ADB" shell input tap 540 315                  # search field
sleep 1
"$ADB" shell input text "auth"
sleep 3
"$ADB" shell input keyevent KEYCODE_BACK        # closes the IME, not the screen
shot 02-search 2

"$ADB" shell input tap 540 500                  # first search hit
shot 03-node-detail 4
"$ADB" shell input keyevent KEYCODE_BACK
sleep 2

"$ADB" shell am force-stop $PKG                 # clears the query
"$ADB" shell am start -n $PKG/.MainActivity >/dev/null
sleep 6
"$ADB" shell input tap 540 590                  # first memory row
shot 04-memory-detail 4

# 05-signin is captured separately: sign out (or `pm clear`) first, then
#   adb shell am start -n $PKG/.MainActivity && sleep 6
#   adb exec-out screencap -p > store/screenshots/05-signin.png
# Doing it inline would end the session the other shots depend on.

demo exit

echo "post-processing to 24-bit PNG (Play rejects alpha):"
python3 - "$OUT" <<'PY'
from PIL import Image
import pathlib, sys
for p in sorted(pathlib.Path(sys.argv[1]).glob('*.png')):
    Image.open(p).convert('RGB').save(p, 'PNG')
    im = Image.open(p); w, h = im.size
    r = max(w, h) / min(w, h)
    print(f"  {p.name:22} {w}x{h}  ratio {r:.3f}:1  {im.mode}  "
          f"{'OK' if r <= 2.0 and min(w, h) >= 1080 else 'FAILS PLAY LIMITS'}")
PY
