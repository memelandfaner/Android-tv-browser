#!/usr/bin/env python3
import subprocess
import time
import os
import json
import datetime

TV_IP = "192.168.0.77:5555"
OUTPUT_DIR = "/home/janez/Namizje/Neimenovana mapa/Android-tv-browser/benchmark_results"
os.makedirs(OUTPUT_DIR, exist_ok=True)

TEST_PAGES = [
    ("01_google_search", "https://www.google.com", "Google Iskalnik (Desktop 4K)"),
    ("02_youtube_watch", "https://www.youtube.com/watch?v=kJQP7kiw5Fk", "YouTube Video Predvajanje (Despacito 4K)"),
    ("03_24ur_portal", "https://www.24ur.com", "24ur Novičarski Portal"),
    ("04_streamnexus_catalog", "https://hydrahd.ws", "StreamNexus Filmski Katalog"),
    ("05_rtvslo_portal", "https://www.rtvslo.si", "RTV Slovenija 365"),
    ("06_github_desktop", "https://github.com/memelandfaner/Android-tv-browser", "GitHub Repozitorij"),
    ("07_wikipedia_sl", "https://sl.wikipedia.org/wiki/Slovenija", "Wikipedija Slovenija"),
    ("08_tmdb_movies", "https://www.themoviedb.org/movie", "TMDB Filmska Baza"),
    ("09_speedtest", "https://fast.com", "Fast.com Merilnik Hitrosti"),
    ("10_youtube_live", "https://www.youtube.com/watch?v=jfKfPfyJRdk", "Lofi Hip Hop Radio (Live Stream)")
]

def run_adb(args, timeout=10):
    cmd = ["adb", "-s", TV_IP] + args
    try:
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        return res.stdout.strip()
    except Exception as e:
        return f"Error: {e}"

def capture_screenshot(filename):
    path = os.path.join(OUTPUT_DIR, filename)
    with open(path, "wb") as f:
        subprocess.run(["adb", "-s", TV_IP, "exec-out", "screencap", "-p"], stdout=f, stderr=subprocess.DEVNULL)
    return path

print("================================================================================")
print(f"🚀 [10-MINUTNI TV BROWSER ENDURANCE & OPTIMIZATION BENCHMARK] Zagon na {TV_IP}...")
print(f"🕒 Začetek: {datetime.datetime.now().strftime('%H:%M:%S')}")
print("================================================================================\n")

# Poveži se in prebudi televizor
run_adb(["connect", TV_IP])
run_adb(["shell", "input", "keyevent", "224"]) # WAKEUP
time.sleep(0.5)

# Počisti logcat
run_adb(["logcat", "-c"])

start_total_time = time.time()
TARGET_DURATION = 600 # 10 minut = 600 sekund

metrics = []
cycle_count = 0

while (time.time() - start_total_time) < TARGET_DURATION:
    cycle_count += 1
    print(f"\n🔄 === CIKEL {cycle_count} (Pretečeni čas: {int(time.time() - start_total_time)}s / {TARGET_DURATION}s) ===")
    
    for idx, (slug, url, title) in enumerate(TEST_PAGES, start=1):
        if (time.time() - start_total_time) >= TARGET_DURATION:
            break
            
        elapsed_now = int(time.time() - start_total_time)
        print(f"[{idx:02d}/10] 🌐 [{elapsed_now:03d}s] Odpiram: {title} ({url})")
        
        # 1. Zaženi stran v brskalniku
        run_adb(["shell", "am", "start", "-n", "com.example.tvbrowser/.MainActivity", "-d", url])
        time.sleep(3.0) # Čas nalaganja
        
        # 2. Izvedi D-Pad navigacijo (drsenje dol in gor)
        for _ in range(3):
            run_adb(["shell", "input", "keyevent", "20"]) # DPAD_DOWN
            time.sleep(0.4)
            
        time.sleep(1.0)
        
        for _ in range(2):
            run_adb(["shell", "input", "keyevent", "19"]) # DPAD_UP
            time.sleep(0.3)
            
        # 3. Preizkusi virtualni kurzor (rumeni gumb)
        run_adb(["shell", "input", "keyevent", "KEYCODE_PROG_YELLOW"])
        time.sleep(0.5)
        # Premakni kazalec
        run_adb(["shell", "input", "keyevent", "22"]) # DPAD_RIGHT
        run_adb(["shell", "input", "keyevent", "20"]) # DPAD_DOWN
        time.sleep(0.5)
        run_adb(["shell", "input", "keyevent", "KEYCODE_PROG_YELLOW"]) # Izklopi kurzor
        time.sleep(0.5)

        # 4. Zajem posnetka zaslona
        shot_name = f"c{cycle_count}_{slug}.png"
        shot_path = capture_screenshot(shot_name)
        
        # 5. Preberi porabo pomnilnika (RAM)
        mem_info = run_adb(["shell", "dumpsys", "meminfo", "com.example.tvbrowser"])
        total_pss = "N/A"
        for line in mem_info.splitlines():
            if "TOTAL PSS:" in line or "TOTAL:" in line:
                parts = line.split()
                if len(parts) >= 2:
                    total_pss = f"{parts[1]} KB"
                    break

        # 6. Preberi logcat za morebitne opozorila/napake
        logs = run_adb(["logcat", "-d", "-s", "Chromium:V,chromium:V,TvBrowser:V,AndroidRuntime:E"])
        recent_logs = logs[-500:] if logs else ""
        has_error = "FATAL EXCEPTION" in recent_logs or "NullPointerException" in recent_logs
        
        status_str = "⚠️ OPOZORILO" if has_error else "✅ ODLIČNO"
        print(f"      📸 Posnetek: {shot_name} | RAM PSS: {total_pss} | Status: {status_str}")
        
        metrics.append({
            "cycle": cycle_count,
            "page": title,
            "url": url,
            "timestamp": datetime.datetime.now().strftime("%H:%M:%S"),
            "ram_pss": total_pss,
            "status": status_str,
            "has_error": has_error
        })
        
        time.sleep(2.0)

print("\n" + "="*80)
print(f"🎉 10-MINUTNI TEST ZAKLJUČEN! Čas izvajanja: {int(time.time() - start_total_time)} sekund.")
print(f"📊 Število izvedenih testnih ciklov: {cycle_count}")
print("="*80)

summary_file = os.path.join(OUTPUT_DIR, "10min_benchmark_summary.json")
with open(summary_file, "w") as f:
    json.dump(metrics, f, indent=2)

print(f"📁 Poročilo shranjeno v: {summary_file}")
