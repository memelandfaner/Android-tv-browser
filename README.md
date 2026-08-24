# 🌐 Android TV Browser (TV Brskalnik za Philips 4K & JBL 300)

Najboljši, najhitrejši in posebej prilagojen spletni brskalnik za **Android TV** (Philips 50PUS8507/12 UHD MediaTek, Android TV 11), optimiziran za daljinsko upravljanje (D-Pad), igralne krmilnike ter vrhunski zvočni sistem **JBL 300**.

---

## ✨ Ključne Funkcionalnosti

### 1. 🖱️ Virtualni Kurzor & D-Pad Krmiljenje (Dual-Mode)
- **Osvetljen kazalec (Focus Halo)**: Plavajoči natančni kurzor z živim turkiznim in zlatim obročem za enostavno klikanje drobnih spletnih gumbov in menijev.
- **Hitri preklop z daljincem**: Pritisnite **🟡 Rumeni gumb** ali gumb **Meni / Kurzor** za hipen vklop ali izklop virtualnega kazalca.
- **Avtomatsko drsenje robov (Edge Auto-Scroll)**: Ko se kazalec približa zgornjemu ali spodnjemu robu, se spletna stran samodejno gladko premika.
- **Strojni dotik (Hardware DownTime Protocol)**: Natančno proženje klikov na video predvajalnikih in povezavah.

### 2. 🛡️ Vgrajeni Varnostni AdBlocker & Anti-Redirect (Top-Frame Lock)
- **Blokada 1500+ oglasnih & stavnih domen**: Samodejno prestrezanje in blokiranje oglasov (20Bet, 1xBet, PopAds, Adsterra, Exoclick, Monetag, Google DoubleClick itd.).
- **Top-Frame Lock**: Blokira zlonamerne poskuse ugrabitve glavnega okna s pojavnimi okni (*popunders*).
- **Zaščita pred sesutjem**: Nevtralizira `disable-devtool.js` skripte.

### 3. ⭐ Dinamični Zaznamki (SQLite Baza Podatkov)
- **1-Klik dodajanje**: Pritisnite ikono **⭐** v zgornji vrstici za takojšnjo shranitev trenutne strani med zaznamke.
- **Prednastavljeni TV portali**:
  - 🎬 **StreamNexus HD** (`http://192.168.0.135:3000`)
  - 📺 **YouTube / SmartTube**
  - 🍿 **The Movie Database (TMDB)**
  - 🔍 **Google Iskalnik**
  - 💬 **Reddit TV**
  - 🐙 **GitHub**
  - 🎮 **Twitch TV**
  - 📻 **Radio Garden**
- **Upravljanje**: Dolg pritisk na kartico zaznamka odpre potrditveno okno za varen izbris.

### 4. ⬇️ Upravljalnik Prenosov & APK Nameščevalec (Download Manager)
- **Nativni prenosi v ozadju**: Prenos APK datotek, filmov, glasbe in slik prek sistemskega `DownloadManagerja`.
- **1-Klik Namestitev APK**: S klikom na preneseno `.apk` datoteko v panelu prenosov se samodejno odpre sistemski namestitveni program (`FileProvider` & `ACTION_INSTALL_PACKAGE`).

### 5. 🔊 Celozaslonski Video & Zvok za JBL 300
- **Čist celozaslonski način**: Samodejno skrivanje vseh orodnih vrstic ob vklopu celozaslonskega načina videa (`WebChromeClient`).
- **SkiaGL GPU pospeševanje**: Gladkih 60 FPS pri predvajanju 1080p / 4K video tokov.
- **Odklenjen zvok**: Trajno odklepanje zvočnega toka `STREAM_MUSIC`.

---

## 🎮 Tipke Daljinca in Bližnjice

| Gumb na daljincu / krmilniku | Funkcija |
| :--- | :--- |
| **🟡 Rumeni gumb (PROG_YELLOW / 185)** | Vklop / Izklop virtualnega kurzorja |
| **D-Pad Gor / Dol / Levo / Desno** | Premikanje kurzorja ali preklapljanje med gumbi |
| **Osrednji gumb (OK / Enter / A)** | Klik na element pod kurzorjem |
| **⬅ Nazaj (BACK)** | Nazaj v zgodovini ali zaprtje panelov / videa |
| **⭐ Zvezdica** | Shrani trenutno stran med zaznamke |
| **🏠 Domov** | Odpre začetni zaslon z zaznamki |

---

## 🚀 Gradnja in Namestitev na TV

Za samodejno prevajanje in brezžično namestitev na Philips Android TV (`192.168.0.77:5555`) zaženite:
```bash
./build_and_install.sh
```

Zgrajen podpisani paket se shrani v:
`Release/Artifacts/tv-browser-release.apk`
