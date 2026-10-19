#!/bin/bash
set -e
SDK=/opt/android-sdk
BT=$SDK/build-tools/34.0.0
AJ=$SDK/platforms/android-34/android.jar
cd /root/.openclaw/workspace/userdata/158378947427704832/apk2
rm -rf build
mkdir -p build/classes build/dex

javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$AJ" -d build/classes \
  src/com/zach/backscreenprobe/MainActivity.java \
  src/com/zach/backscreenprobe/TiltView.java \
  src/com/zach/backscreenprobe/SnakeView.java
echo "javac ok"

"$BT/d8" --lib "$AJ" --min-api 26 --release --output build/dex \
  build/classes/com/zach/backscreenprobe/MainActivity.class \
  build/classes/com/zach/backscreenprobe/TiltView.class \
  build/classes/com/zach/backscreenprobe/SnakeView.class \
  build/classes/com/zach/backscreenprobe/SnakeView\$TiltSource.class
echo "dex ok"

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$AJ" --manifest AndroidManifest.xml \
  --min-sdk-version 26 --target-sdk-version 34 --version-code 1 --version-name 1.0 build/res.zip
echo "link ok"

cp build/base.apk build/unsigned.apk
cd build/dex && zip -q ../unsigned.apk classes.dex && cd ../..
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk

KEYSTORE=/root/.openclaw/workspace/userdata/158378947427704832/apk-src/keystore.jks
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:abgames123 --key-pass pass:abgames123 \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out build/BackscreenProbe-1.0.apk build/aligned.apk

"$BT/apksigner" verify build/BackscreenProbe-1.0.apk
"$BT/aapt2" dump badging build/BackscreenProbe-1.0.apk | head -5
ls -la build/BackscreenProbe-1.0.apk
