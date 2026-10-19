#!/bin/bash
# 构建 BackscreenProbe APK（无 Gradle，直接用 build-tools）
# 依赖：JDK 17、Android SDK（build-tools 34.0.0、platform android-34）
# 签名：通过环境变量传入自己的 keystore，仓库不包含任何签名文件
#   KEYSTORE=/path/to/your.jks KS_PASS=xxxx KEY_PASS=xxxx ./build.sh
set -e
SDK=${ANDROID_SDK:-${ANDROID_HOME:-/opt/android-sdk}}
BT=$SDK/build-tools/34.0.0
AJ=$SDK/platforms/android-34/android.jar
cd "$(dirname "$0")"
rm -rf build
mkdir -p build/classes build/dex

javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$AJ" -d build/classes \
  src/com/zach/backscreenprobe/MainActivity.java \
  src/com/zach/backscreenprobe/TiltView.java \
  src/com/zach/backscreenprobe/SnakeView.java
echo "javac ok"

# 注意：必须把整个 classes 目录打包成 jar，否则匿名内部类（$1/$2）会漏进 dex，运行时直接闪退
jar cf build/classes.jar -C build/classes .
"$BT/d8" --lib "$AJ" --min-api 26 --release --output build/dex build/classes.jar
echo "dex ok"

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$AJ" --manifest AndroidManifest.xml \
  --min-sdk-version 26 --target-sdk-version 34 --version-code 2 --version-name 1.1 build/res.zip
echo "link ok"

cp build/base.apk build/unsigned.apk
cd build/dex && zip -q ../unsigned.apk classes.dex && cd ../..
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk

if [ -n "${KEYSTORE:-}" ]; then
  "$BT/apksigner" sign --ks "$KEYSTORE" \
    --ks-pass "pass:${KS_PASS:?请设置 KS_PASS}" --key-pass "pass:${KEY_PASS:-$KS_PASS}" \
    --v1-signing-enabled true --v2-signing-enabled true \
    --out build/BackscreenProbe-1.1.apk build/aligned.apk
  "$BT/apksigner" verify build/BackscreenProbe-1.1.apk
else
  echo "未提供 KEYSTORE，跳过签名，产物为 build/aligned.apk"
  cp build/aligned.apk build/BackscreenProbe-1.1.apk
fi
"$BT/aapt2" dump badging build/BackscreenProbe-1.1.apk | head -5
ls -la build/BackscreenProbe-1.1.apk
