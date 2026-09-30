import urllib.request, json

with open('/home/wym/.gemini/antigravity-ide/brain/1a15c4af-8862-4b3c-98d0-ad7398d67a75/scratch/html5_2001.json', 'r') as f:
    data = json.load(f)

wstv_chs = []
for p in data.get('live_policy', []):
    if p.get('channel_group') == 'wstv':
        wstv_chs = [c.get('channel') for c in p.get('channels', [])]

print(f"Total wstv channels: {len(wstv_chs)}")

alive = []
for c in wstv_chs:
    master_url = f"https://wstvwbcdtxy.liveplay.myqcloud.com/wstvwbcd/cdrm{c}_1/index.m3u8"
    try:
        req = urllib.request.Request(master_url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=2) as resp:
            content = resp.read().decode('utf-8')
            # find sub m3u8
            lines = [l.strip() for l in content.splitlines() if l.strip().endswith('.m3u8')]
            if lines:
                sub_url = f"https://wstvwbcdtxy.liveplay.myqcloud.com{lines[0]}"
                # check sub m3u8
                req_sub = urllib.request.Request(sub_url, headers={'User-Agent': 'Mozilla/5.0'})
                with urllib.request.urlopen(req_sub, timeout=2) as resp_sub:
                    sub_content = resp_sub.read().decode('utf-8')
                    if '#EXTINF' in sub_content:
                        alive.append((c, True, 'OK'))
                    else:
                        alive.append((c, False, 'No TS chunks'))
            else:
                alive.append((c, False, 'No sub-playlist'))
    except Exception as e:
        alive.append((c, False, str(e)))

print("=== 卫视存活状态 ===")
for c, ok, msg in alive:
    status = "✅ 正常" if ok else "❌ 失效"
    print(f"{c:15} -> {status} ({msg})")
