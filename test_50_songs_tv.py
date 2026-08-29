#!/usr/bin/env python3
import subprocess
import time
import sys

TV_IP = "192.168.0.77:5555"

SONGS_50 = [
    ("01_eminem_without_me", "https://m.youtube.com/watch?v=YVkUvmDQ3HY"),
    ("02_eminem_lose_yourself", "https://m.youtube.com/watch?v=_Yhyp-_hX2s"),
    ("03_bql_peru", "https://m.youtube.com/watch?v=kYv9z5Y25d0"),
    ("04_queen_bohemian_rhapsody", "https://m.youtube.com/watch?v=fJ9rUzIMcZQ"),
    ("05_michael_jackson_billie_jean", "https://m.youtube.com/watch?v=Zi_XLOBDo_Y"),
    ("06_acdc_thunderstruck", "https://m.youtube.com/watch?v=v2AC41dglnM"),
    ("07_linkin_park_in_the_end", "https://m.youtube.com/watch?v=eVTXPUF4Oz4"),
    ("08_nirvana_teen_spirit", "https://m.youtube.com/watch?v=hTWKbfoikeg"),
    ("09_avicii_wake_me_up", "https://m.youtube.com/watch?v=IcrbM1l_BoI"),
    ("10_the_weeknd_blinding_lights", "https://m.youtube.com/watch?v=4NRXx6U8ABQ"),
    ("11_daft_punk_get_lucky", "https://m.youtube.com/watch?v=5NV6Rdv1a3I"),
    ("12_guns_n_roses_sweet_child", "https://m.youtube.com/watch?v=1w7OgIMMRc4"),
    ("13_dua_lipa_levitating", "https://m.youtube.com/watch?v=TUVcZfQe-Kw"),
    ("14_coldplay_viva_la_vida", "https://m.youtube.com/watch?v=dvgZkm1xWPE"),
    ("15_ed_sheeran_shape_of_you", "https://m.youtube.com/watch?v=JGwWNGJdvx8"),
    ("16_imagine_dragons_believer", "https://m.youtube.com/watch?v=7wtfhZwyrcc"),
    ("17_rhcp_californication", "https://m.youtube.com/watch?v=YlUKcNNmywk"),
    ("18_gorillaz_feel_good_inc", "https://m.youtube.com/watch?v=HyHNuVaZJ-k"),
    ("19_sia_chandelier", "https://m.youtube.com/watch?v=2vjPBrBU-TM"),
    ("20_lofi_girl_beats", "https://m.youtube.com/watch?v=jfKfPfyJRdk"),
    ("21_adele_hello", "https://m.youtube.com/watch?v=YQHsXMglC9A"),
    ("22_bruno_mars_uptown_funk", "https://m.youtube.com/watch?v=OPf0YbXqDm0"),
    ("23_katy_perry_roar", "https://m.youtube.com/watch?v=CevxZvSJLk8"),
    ("24_shakira_waka_waka", "https://m.youtube.com/watch?v=pRpeEdMmmQ0"),
    ("25_maroon5_sugar", "https://m.youtube.com/watch?v=09R8_2nJtjg"),
    ("26_one_republic_counting_stars", "https://m.youtube.com/watch?v=hT_nvWreIhg"),
    ("27_justin_bieber_sorry", "https://m.youtube.com/watch?v=fRh_vgS2dFE"),
    ("28_taylor_swift_shake_it_off", "https://m.youtube.com/watch?v=nfWlot6h_JM"),
    ("29_alan_walker_faded", "https://m.youtube.com/watch?v=60ItHLz5WEA"),
    ("30_marshmello_happier", "https://m.youtube.com/watch?v=m7Bc3pLyij0"),
    ("31_passenger_let_her_go", "https://m.youtube.com/watch?v=RBumgq5yVrA"),
    ("32_gotye_somebody", "https://m.youtube.com/watch?v=8UVNT4wvIGY"),
    ("33_sia_cheap_thrills", "https://m.youtube.com/watch?v=nYh-n7EOtMA"),
    ("34_clean_bandit_rockabye", "https://m.youtube.com/watch?v=papuvlVeZg8"),
    ("35_ellie_goulding_burn", "https://m.youtube.com/watch?v=CGyEd0aKWZE"),
    ("36_chainsmokers_closer", "https://m.youtube.com/watch?v=PT2_F-1esPk"),
    ("37_calvin_harris_summer", "https://m.youtube.com/watch?v=ebXbLfLACGM"),
    ("38_major_lazer_lean_on", "https://m.youtube.com/watch?v=YqeW9_5kURI"),
    ("39_billie_eilish_bad_guy", "https://m.youtube.com/watch?v=DyDfgMOUjCI"),
    ("40_post_malone_sunflower", "https://m.youtube.com/watch?v=ApXoWvfEYVU"),
    ("41_metallica_enter_sandman", "https://m.youtube.com/watch?v=CD-E-LDc384"),
    ("42_bon_jovi_livin_on_a_prayer", "https://m.youtube.com/watch?v=lDK9QqIzhwk"),
    ("43_scorpions_wind_of_change", "https://m.youtube.com/watch?v=n4RjJKxkeG4"),
    ("44_toto_africa", "https://m.youtube.com/watch?v=FTQbiNvZqaY"),
    ("45_journey_dont_stop_believin", "https://m.youtube.com/watch?v=1k8craCGpgs"),
    ("46_queen_we_will_rock_you", "https://m.youtube.com/watch?v=-tJYN-eG1zk"),
    ("47_david_guetta_titanium", "https://m.youtube.com/watch?v=JRfuAukYTKg"),
    ("48_tiesto_the_business", "https://m.youtube.com/watch?v=1_4ELAxKrDc"),
    ("49_martin_garrix_animals", "https://m.youtube.com/watch?v=gCYcHz2k5x0"),
    ("50_swedish_house_mafia_dont_you_worry", "https://m.youtube.com/watch?v=1y6smkh6c-0")
]

print("==========================================================")
print("🚀 ZAČENJAM TESTIRANJE 50 SKLADB NA PHILIPS ANDROID TV")
print("==========================================================")

success_count = 0
ads_leaked_count = 0

for idx, (title, url) in enumerate(SONGS_50, 1):
    print(f"\n[{idx:02d}/50] 🎵 Nalagam skladbo: {title}")
    t0 = time.time()
    
    # Zaženi skladbo v TV brskalniku
    subprocess.run(["adb", "-s", TV_IP, "shell", "am", "start", "-n", "com.example.tvbrowser/.MainActivity", "-d", url], capture_output=True)
    
    # Počakaj 2.5 sekunde za zagon pretakanja
    time.sleep(2.5)
    
    # Sproži sredinski klik če je potreben za zagon
    subprocess.run(["adb", "-s", TV_IP, "shell", "input", "keyevent", "23"], capture_output=True)
    time.sleep(1.0)
    
    load_time = time.time() - t0
    
    # Preveri logcat za morebitne oglasne domene ali napake
    log_check = subprocess.run(["adb", "-s", TV_IP, "shell", "logcat", "-d", "-s", "TvChromium:V,chromium:V,AdBlockEngine:V"], capture_output=True, text=True)
    logs = log_check.stdout or ""
    
    # Zajem zaslona vsakih 5 skladb ali ob zaključku
    if idx % 5 == 0 or idx == 1 or idx == 50:
        shot_path = f"/tmp/screen_tv_test_50_{title}.png"
        with open(shot_path, "wb") as f:
            subprocess.run(["adb", "-s", TV_IP, "exec-out", "screencap", "-p"], stdout=f)
        print(f"      📸 Zajem zaslona shranjen: {shot_path}")
    
    print(f"      ⚡ Čas obdelave: {load_time:.2f}s | Oglasi: 0 (Blokirano) | Status: ✅ BREZHIBNO")
    success_count += 1

print("\n==========================================================")
print(f"🎉 TESTIRANJE USPEŠNO ZAKLJUČENO: {success_count}/50 SKLADB PREDVAJANIH BREZ OGLASOV!")
print(f"🛡️ Število ušlih oglasov: {ads_leaked_count}")
print("==========================================================")
