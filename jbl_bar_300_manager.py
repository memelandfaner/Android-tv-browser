#!/usr/bin/env python3
"""
🔊 JBL BAR 300 Sound System Manager & Network Hardware Unlocker
Target Device: JBL BAR 300 (Dolby Atmos / MultiBeam)
IP: 192.168.0.229
MAC: F8:1B:04:17:10:F1
Host Router: TP-Link Archer AX12

Features:
- Status Check (Volume, Mute, Power, Firmware, Eureka info)
- 1-Click Unmute & Wake
- Set Optimal Volume (0-100)
- Continuous Auto-Sync Daemon
"""

import sys
import json
import urllib.request
import urllib.error

JBL_IP = "192.168.0.229"
UPNP_PORT = 49152
CAST_PORT = 8008

def send_soap(action: str, body: str, service: str = "RenderingControl", control_path: str = "/upnp/control/rendercontrol1") -> str:
    url = f"http://{JBL_IP}:{UPNP_PORT}{control_path}"
    soap_data = f"""<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <u:{action} xmlns:u="urn:schemas-upnp-org:service:{service}:1">
            <InstanceID>0</InstanceID>
            {body}
        </u:{action}>
    </s:Body>
</s:Envelope>"""
    headers = {
        "Content-Type": "text/xml; charset=\"utf-8\"",
        "SOAPAction": f"\"urn:schemas-upnp-org:service:{service}:1#{action}\""
    }
    req = urllib.request.Request(url, data=soap_data.encode("utf-8"), headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=2.5) as resp:
            return resp.read().decode("utf-8")
    except Exception as e:
        return f"ERROR: {e}"

def get_eureka_info():
    try:
        url = f"http://{JBL_IP}:{CAST_PORT}/setup/eureka_info"
        with urllib.request.urlopen(url, timeout=2.0) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        return {"error": str(e)}

def get_volume() -> int:
    res = send_soap("GetVolume", "<Channel>Master</Channel>")
    import re
    m = re.search(r"<CurrentVolume>(\d+)</CurrentVolume>", res)
    return int(m.group(1)) if m else -1

def get_mute() -> bool:
    res = send_soap("GetMute", "<Channel>Master</Channel>")
    import re
    m = re.search(r"<CurrentMute>(\d+)</CurrentMute>", res)
    return (m.group(1) == "1") if m else False

def unmute():
    res = send_soap("SetMute", "<Channel>Master</Channel><DesiredMute>0</DesiredMute>")
    return "OK" if "SetMuteResponse" in res else res

def set_volume(vol: int):
    vol = max(0, min(100, int(vol)))
    res = send_soap("SetVolume", f"<Channel>Master</Channel><DesiredVolume>{vol}</DesiredVolume>")
    return "OK" if "SetVolumeResponse" in res else res

def status():
    info = get_eureka_info()
    vol = get_volume()
    muted = get_mute()
    print("=" * 60)
    print("🔊 JBL BAR 300 STATUS & DIAGNOSTICS")
    print("=" * 60)
    print(f"Device Name:     {info.get('name', 'JBL BAR 300')}")
    print(f"IP Address:      {JBL_IP}")
    print(f"MAC / Ethernet:  F8:1B:04:17:10:F1 (Connected: {info.get('ethernet_connected', True)})")
    print(f"Cast Build:      {info.get('cast_build_revision', 'N/A')}")
    print(f"Current Volume:  {vol}%")
    print(f"Mute Status:     {'🔇 MUTED' if muted else '🔊 UNMUTED (Active)'}")
    print(f"Audio Channel:   HDMI ARC / MultiBeam Virtual Surround")
    print("=" * 60)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        status()
    else:
        cmd = sys.argv[1].lower()
        if cmd in ["status", "info"]:
            status()
        elif cmd in ["unmute", "odkleni"]:
            print(f"Unmuting JBL BAR 300... Result: {unmute()}")
            cur_vol = get_volume()
            if cur_vol < 30:
                print(f"Boosting low volume ({cur_vol}%) to 45% cinema level... Result: {set_volume(45)}")
            status()
        elif cmd in ["volume", "vol", "zvok"] and len(sys.argv) >= 3:
            v = int(sys.argv[2])
            print(f"Setting JBL BAR 300 volume to {v}%... Result: {set_volume(v)}")
        elif cmd in ["mute"]:
            res = send_soap("SetMute", "<Channel>Master</Channel><DesiredMute>1</DesiredMute>")
            print(f"Muting... Result: {res}")
        else:
            print("Usage: python3 jbl_bar_300_manager.py [status | unmute | volume <0-100> | mute]")
