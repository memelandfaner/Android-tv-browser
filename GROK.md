# 🤖 Grok CLI Agent Guide: FreeNet Android TV Browser

Dobrodošel, Grok! Ta repozitorij vsebuje **FreeNet Android TV Browser** – visoko-zmogljiv spletni brskalnik, optimiziran za **Philips 50PUS8507/12 UHD Android TV 11** (MediaTek) in **JBL Bar 300** zvočni sistem.

---

## 📡 Povezana Strojna Oprema & ADB Okolje

- **Ciljna naprava**: Philips 50PUS8507/12 (UHD 4K LED Android TV 11)
- **Stalni ADB IP**: `192.168.0.77:5555` (Vedno povezana in avtenticirana)
- **Preverjanje povezave**:
  ```bash
  adb devices
  ```
- **Zajem posnetka zaslona v živo**:
  ```bash
  adb -s 192.168.0.77:5555 exec-out screencap -p > /tmp/tv_screen.png
  ```
- **Nadzor Logcat napak**:
  ```bash
  adb -s 192.168.0.77:5555 logcat -d -s Chromium:V,chromium:V,TvBrowser:V,AndroidRuntime:E
  ```

---

## ⚡ Samostojno Prevajanje & Gradnja (Build & Deploy)

V repozitoriju je na voljo popolnoma avtomatiziran samostojni prevajalnik (brez potrebe po Gradle / Android Studio):
```bash
./build_and_install.sh
```
Ta skripta izvede:
1. `aapt2` prevajanje XML virov (`res/`, `assets/`, `AndroidManifest.xml`).
2. `kotlinc` prevajanje vseh Kotlin datotek (`src/main/kotlin/com/example/tvbrowser/*.kt`).
3. Google `d8` prevajanje v Dalvik DEX bajtno kodo.
4. `uber-apk-signer` V3 podpisovanje APK paketa.
5. Samodejno namestitev prek ADB (`adb -s 192.168.0.77:5555 install -r ...`) in zagon aplikacije.

---

## 📂 Ključne Datoteke v Projektu

1. **`src/main/kotlin/com/example/tvbrowser/MainActivity.kt`**:
   - Glavna dejavnost, obdelava D-Pad tipk, navigacijska orodna vrstica, upravljanje zaznamkov, prenosov in zavihkov.
2. **`src/main/kotlin/com/example/tvbrowser/ChromiumEngineView.kt`**:
   - WebView jedro z `UserAgentMode.TV` (Desktop Chrome 122.0), `textZoom = 110`, `setSupportZoom(true)`, prestrezanjem oglasov in varnostnih groženj.
3. **`src/main/kotlin/com/example/tvbrowser/UserScriptManager.kt`**:
   - Kozmetični CSS filtri, Freenet JS Bridge, D-Pad krmiljenje, SmartTube YouTube AdBlocker & SponsorBlock integracija.
4. **`src/main/kotlin/com/example/tvbrowser/AdBlockEngine.kt`**:
   - Radix Domain Suffix Trie za blokado oglasov in sledilcev.
5. **`res/layout/activity_main.xml`**:
   - Postavitev zaslona z orodno vrstico in `contentContainer` (`android:layout_alignParentBottom="true"`).

---

## 🎯 Navodila za Skupno Delo

1. Preglej izvorno kodo za morebitne izboljšave ali hrošče pri navigaciji z daljincem (D-Pad), predvajanju videa ali izrisu spletnih strani.
2. Po vsaki spremembi zaženi `./build_and_install.sh` za takojšnjo namestitev na televizor.
3. Preveri rezultat z zajemom zaslona ali logcatom.
