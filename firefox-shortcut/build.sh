#!/bin/bash
# Builds build/firefox-shortcut.apk without Gradle, using the Android SDK command-line tools.
# Install on the TV with:  adb install -r build/firefox-shortcut.apk
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/36.1.0"
PLATFORM="$SDK/platforms/android-36/android.jar"
export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"

# Throwaway signing key. Keep it: updates must be signed with the same key.
if [ ! -f debug.keystore ]; then
  keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

rm -rf build
mkdir -p build/classes build/dex

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/unaligned.apk -I "$PLATFORM" --manifest AndroidManifest.xml build/res.zip

javac -source 8 -target 8 -Xlint:-options -cp "$PLATFORM" -d build/classes \
  src/local/tv/firefoxshortcut/LaunchActivity.java
"$BT/d8" --min-api 23 --lib "$PLATFORM" --output build/dex \
  build/classes/local/tv/firefoxshortcut/*.class
(cd build/dex && zip -q ../unaligned.apk classes.dex)

"$BT/zipalign" -f 4 build/unaligned.apk build/aligned.apk
"$BT/apksigner" sign --ks debug.keystore --ks-pass pass:android \
  --out build/firefox-shortcut.apk build/aligned.apk
echo "Built $(pwd)/build/firefox-shortcut.apk"
