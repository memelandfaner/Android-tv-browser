#!/usr/bin/env bash
# ==============================================================================
# 🚀 BUILD & DEPLOY SCRIPT: ANDROID TV BROWSER (KOTLIN MVVM CHROMIUM ENGINE)
# Target: Philips 50PUS8507/12 UHD Android TV 11
# IP: 192.168.0.77:5555
# ==============================================================================
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS_DIR="/home/janez/Namizje/Neimenovana mapa/streamN-TV2/android_tv/.tools"
KOTLINC="$TOOLS_DIR/kotlinc/bin/kotlinc"
KOTLIN_LIB="$TOOLS_DIR/kotlinc/lib/kotlin-stdlib.jar"
BUILD_DIR="$DIR/build"
RELEASE_DIR="$DIR/Release/Artifacts"
TV_IP="192.168.0.77:5555"

echo "=========================================================="
echo "🌐 GRADIM ANDROID TV BROWSER (KOTLIN BETA APK)"
echo "=========================================================="

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/gen"
mkdir -p "$BUILD_DIR/classes"
mkdir -p "$BUILD_DIR/dex"
mkdir -p "$RELEASE_DIR"

echo "⚙️ 1/5: Prevajam Android XML vire (AAPT2)..."
"$TOOLS_DIR/aapt2" compile --dir "$DIR/res" -o "$BUILD_DIR/compiled_res.zip"
"$TOOLS_DIR/aapt2" link -I "$TOOLS_DIR/android.jar" \
    --manifest "$DIR/AndroidManifest.xml" \
    --min-sdk-version 28 \
    --target-sdk-version 34 \
    --version-code 2 \
    --version-name "2.0.0-beta" \
    -o "$BUILD_DIR/resources.apk" \
    --java "$BUILD_DIR/gen" \
    "$BUILD_DIR/compiled_res.zip"

echo "☕ 2/5: Prevajam Kotlin izvorno kodo (kotlinc)..."
"$KOTLINC" -cp "$TOOLS_DIR/android.jar:$BUILD_DIR/gen" \
    -d "$BUILD_DIR/classes" \
    -jvm-target 1.8 \
    "$DIR/src/main/kotlin/com/example/tvbrowser/"*.kt \
    "$BUILD_DIR/gen/com/example/tvbrowser/R.java"

echo "⚡ 3/5: Prevajam v Dalvik Executable (D8)..."
java -cp "$TOOLS_DIR/r8.jar" com.android.tools.r8.D8 \
    --min-api 28 \
    --output "$BUILD_DIR/dex" \
    --lib "$TOOLS_DIR/android.jar" \
    "$BUILD_DIR/classes/com/example/tvbrowser/"*.class \
    "$KOTLIN_LIB"

echo "📦 4/5: Sestavljam APK paket..."
cp "$BUILD_DIR/resources.apk" "$BUILD_DIR/unaligned.apk"
cd "$BUILD_DIR/dex"
jar -uf "$BUILD_DIR/unaligned.apk" classes.dex
cd "$DIR"

echo "✍️ 5/5: Podpisujem APK paket z uber-apk-signer..."
java -jar "$TOOLS_DIR/uber-apk-signer.jar" \
    --apks "$BUILD_DIR/unaligned.apk" \
    --out "$BUILD_DIR/signed" \
    --allowResign

FINAL_APK="$RELEASE_DIR/tv-browser-release.apk"
cp "$BUILD_DIR/signed/unaligned-aligned-debugSigned.apk" "$FINAL_APK"
cp "$FINAL_APK" "$DIR/Android-tv-browser.apk"

echo ""
echo "=========================================================="
echo "🎉 ZGRAJEN SIGNED APK: $FINAL_APK"
echo "=========================================================="

if [ "$1" == "--install" ] || [ "$1" == "-i" ] || [ -z "$1" ]; then
    echo "📡 Povezujem se z Android TV ($TV_IP)..."
    adb connect "$TV_IP"
    adb -s "$TV_IP" wait-for-device
    echo "📲 Nameščam na televizor..."
    adb -s "$TV_IP" install -r -d "$FINAL_APK"
    adb -s "$TV_IP" shell pm grant com.example.tvbrowser android.permission.RECORD_AUDIO 2>/dev/null || true
    echo "🚀 Zaganjam TV Brskalnik..."
    adb -s "$TV_IP" shell am start -n com.example.tvbrowser/.MainActivity
    echo "✅ Uspešno zagnano na televizorju!"
fi
