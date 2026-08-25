#!/bin/bash
# ==============================================================================
# 🌐 FreeNet TV Browser - Samodejni Namestitveni Skript za Android TV / Fire TV
# Repozitorij: https://github.com/memelandfaner/freenet-browser
# ==============================================================================

set -e

APK_URL="https://raw.githubusercontent.com/memelandfaner/freenet-browser/main/Release/Artifacts/tv-browser-release.apk"
TEMP_APK="/tmp/FreeNet-Browser.apk"
TV_IP="${1:-192.168.0.77:5555}"

echo "=========================================================="
echo "🌐 PRENOS IN NAMESTITEV: FreeNet TV Browser"
echo "=========================================================="

echo "📥 Prenašam najnovejšo različico APK..."
curl -L -f -o "$TEMP_APK" "$APK_URL" --progress-bar

if [ ! -f "$TEMP_APK" ] || [ ! -s "$TEMP_APK" ]; then
    echo "❌ Napaka pri prenosu APK paketa!"
    exit 1
fi

echo "✅ APK prenesen ($(du -h "$TEMP_APK" | cut -f1))."

if command -v adb &> /dev/null; then
    echo "📡 Preverjam ADB povezavo s TV ($TV_IP)..."
    adb connect "$TV_IP" 2>/dev/null || true
    echo "📲 Nameščam na televizor..."
    adb -s "$TV_IP" install -r -d "$TEMP_APK"
    echo "🚀 Zaganjam FreeNet Browser..."
    adb -s "$TV_IP" shell am start -n com.example.tvbrowser/.MainActivity
    echo "🎉 Uspešno nameščeno in zagnano na televizorju!"
else
    echo "💡 ADB ni nameščen na tem računalniku. APK je shranjen v: $TEMP_APK"
    echo "Lahko ga ročno namestite ali prenesete na USB ključek / TV."
fi
