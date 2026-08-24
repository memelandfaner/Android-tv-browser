# 🌐 Android TV Browser (Profesionalni TV Brskalnik za Philips 4K & JBL 300)

Najboljši, najnaprednejši in posebej za **Android TV** (Philips 50PUS8507/12 UHD MediaTek, Android TV 11) prilagojeni spletni brskalnik, optimiziran za upravljanje z daljinskim upravljalnikom (D-Pad), igralnimi krmilniki, z integriranim Google & YouTube iskalnikom, več zavihki ter podporo za vrhunski kinematografski zvok **JBL 300**.

---

## 📸 Predogled Delovanja na Televizorju v Živo

| 🌟 Čist Vitki Vmesnik & Google Iskalnik | 🔍 Optimizirano YouTube Iskanje |
| :---: | :---: |
| ![Clean Home](https://raw.githubusercontent.com/memelandfaner/Android-tv-browser/main/res/drawable/app_banner.png) | ![YouTube](https://raw.githubusercontent.com/memelandfaner/Android-tv-browser/main/res/drawable/app_icon.png) |

---

## ✨ Ključne Funkcionalnosti in Zmogljivosti

### 1. 🔍 Vgrajen Google & YouTube Iskalnik (Pametni Omnibox)
- **Vgrajeno iskanje**: Zgornja vrstica deluje kot neposredno iskalno polje. Vpis poljubne besede ali poizvedbe takoj sproži Google iskanje (ali YouTube iskanje, ko ste na YouTubu).
- **Čist prikaz brez dolgih povezav**: V vrstici se prikazujejo čista imena domen ali iskalni nizi namesto zapletenih sledilnih povezav.
- **Samodejno zapiranje tipkovnice**: Ob potrditvi iskanja ali nalaganju zadetkov se navidezna tipkovnica samodejno hipno zapre, fokus pa se prenese na zadetke iskanja, tako da je 100 % vsebine takoj vidne čez celoten zaslon.

### 2. 📑 Večstranski Zavihki (Multi-Tab Engine)
- **Hitro dodajanje (`➕`)**: Odpiranje novih zavihkov z enim samim klikom.
- **Tekoče preklapljanje**: Hipen preklop med odprtimi spletnimi stranmi.
- **Enostavno zapiranje (`✕`)**: Rdeči gumb za takojšen izbris zavihka in sprostitev delovnega pomnilnika (RAM).

### 3. 🖱️ Virtualni Kurzor & D-Pad Krmiljenje (Dual-Mode)
- **Osvetljen kazalec (Focus Halo)**: Plavajoči natančni kurzor z živim turkiznim obročem za enostavno klikanje drobnih spletnih gumbov in menijev.
- **Hitri preklop z daljincem**: Pritisnite **🟡 Rumeni gumb** za vklop ali izklop virtualnega kazalca.
- **Avtomatsko drsenje robov**: Ko se kazalec približa robovom zaslona, se spletna stran samodejno gladko pomika.

### 4. 🛡️ Vgrajeni Varnostni AdBlocker & Anti-Redirect (Top-Frame Lock)
- **Blokada 1500+ oglasnih & stavnih domen**: Samodejno prestrezanje in blokiranje oglasov (20Bet, 1xBet, PopAds, Adsterra, Exoclick, Monetag, Google DoubleClick itd.).
- **Top-Frame Lock**: Blokira zlonamerne poskuse ugrabitve glavnega okna s pojavnimi okni (*popunders*).
- **Zaščita pred sesutjem**: Nevtralizira `disable-devtool.js` skripte.

### 5. ⭐ Dinamični Zaznamki (SQLite Baza Podatkov)
- **1-Klik dodajanje**: Pritisnite ikono **⭐** v zgornji vrstici za takojšnjo shranitev trenutne strani med zaznamke.
- **Prednastavljeni TV portali**:
  - 📺 **YouTube**
  - 🐙 **GitHub**
  - 🍿 **The Movie Database (TMDB)**
  - 🎬 **StreamNexus HD**

### 6. ⬇️ Upravljalnik Prenosov & APK Nameščevalec (Download Manager)
- **Nativni prenosi v ozadju**: Prenos APK datotek, filmov, glasbe in slik prek sistemskega `DownloadManagerja`.
- **1-Klik Namestitev APK**: S klikom na preneseno `.apk` datoteko v panelu prenosov se samodejno odpre sistemski namestitveni program.

### 7. 🔊 Celozaslonski Video & Kinematografski Zvok za JBL 300
- **Čist celozaslonski način**: Samodejno skrivanje vseh orodnih vrstic ob vklopu celozaslonskega načina videa (`WebChromeClient`).
- **SkiaGL GPU pospeševanje**: Gladkih 60 FPS pri predvajanju 1080p / 4K video tokov.
- **Odklenjen zvok**: Trajno odklepanje zvočnega toka `STREAM_MUSIC` za močne nizke tone in jasne filmske dialoge.

---

## 🎮 Tipke Daljinskega Upravljalnika

| Gumb na daljincu / krmilniku | Funkcija |
| :--- | :--- |
| **🟢 Zeleni gumb (PROG_GREEN / MENU)** | Hipni skok v iskalno polje (označi celoten tekst za nov vnos) |
| **🟡 Rumeni gumb (PROG_YELLOW / 185)** | Vklop / Izklop virtualnega kurzorja |
| **🔴 Rdeči gumb (PROG_RED / 183)** | Glasovno iskanje z mikrofonom |
| **🔵 Modri gumb (PROG_BLUE / 186)** | Hitro odpiranje plošče z zaznamki |
| **D-Pad Gor / Dol / Levo / Desno** | Navigacija med elementi ali premikanje kurzorja |
| **Sredinska tipka (OK / Enter)** | Potrditev izbire / Klik / Odpiranje povezave |
| **◀ Tipka Nazaj (BACK)** | Nazaj v zgodovini ali zaprtje panelov / videa |
| **⭐ Zvezdica** | Shrani trenutno stran med zaznamke |
| **🏠 Domov** | Odpre začetno Google stran |

---

## 📲 Namestitev na Televizor

### Možnost A: Brezžična namestitev prek ADB (1-Klik Skripta)
Če imate računalnik v istem Wi-Fi omrežju kot televizor:
```bash
./build_and_install.sh
```

### Možnost B: Namestitev z USB ključkom ali programom Downloader
1. Prenesite že zgrajeno podpisano APK datoteko:
   👉 **[`Release/Artifacts/tv-browser-release.apk`](file:///home/janez/Namizje/Neimenovana%20mapa/Android-tv-browser/Release/Artifacts/tv-browser-release.apk)**
2. Kopirajte APK na USB ključek ali prenesite prek aplikacije *Downloader* na TV-ju.
3. Odprite APK in potrdite namestitev.

---

## 🛠️ Struktura Projekta

```
Android-tv-browser/
├── AndroidManifest.xml          # D-Pad deklaracija, Leanback pasica in dovoljenja
├── build_and_install.sh         # Samostojni AAPT2 + kotlinc + D8 + uapksigner graditelj
├── Release/
│   └── Artifacts/
│       └── tv-browser-release.apk # Končna podpisana Release APK datoteka
├── res/
│   ├── drawable/                # 4K holografska ikona, Leanback pasica, gumbi
│   ├── layout/                  # activity_main.xml (vitka navigacija, paneli)
│   └── values/                  # barve, slogi in stringi
└── src/main/kotlin/com/example/tvbrowser/
    ├── MainActivity.kt          # Glavna TV aktivnost, upravljanje panelov in D-Pada
    ├── ChromiumEngineView.kt    # Chromium WebView jedro z AdBlockom in prisilnim zvokom
    ├── TvFocusManager.kt        # D-Pad in barvne bližnjice na daljincu
    ├── VirtualCursorOverlay.kt  # Plavajoči virtualni kazalec s Focus Halo efektom
    ├── BrowserRepository.kt     # SQLite podatkovna baza za zaznamke in zgodovino
    ├── BrowserViewModel.kt      # Upravljanje stanja zavihkov in URL usmerjanje
    └── DownloadHandler.kt       # Nativni DownloadManager in APK nameščevalec
```

---

## 📜 Licenca
Odprtokodno pod licenco MIT.
