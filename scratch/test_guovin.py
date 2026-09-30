import urllib.request, re

url = 'https://raw.githubusercontent.com/Guovin/iptv-api/gd/output/result.m3u'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req) as resp:
    lines = resp.read().decode('utf-8', errors='ignore').splitlines()

channels = {}
cur_name = None
for l in lines:
    if l.startswith('#EXTINF'):
        m = re.search(r',(.+)$', l)
        if m: cur_name = m.group(1).strip()
    elif l.startswith('http') and cur_name:
        if 'CCTV' in cur_name:
            if cur_name not in channels:
                channels[cur_name] = []
            channels[cur_name].append(l.strip())
        cur_name = None

print("=== 测试各 CCTV 频道首选源可用性及分辨率 ===")
for name in sorted(channels.keys(), key=lambda x: int(re.search(r'\d+', x).group()) if re.search(r'\d+', x) else 99):
    urls = channels[name]
    working_url = None
    working_res = None
    for u in urls:
        try:
            r = urllib.request.Request(u, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(r, timeout=2.5) as res:
                content = res.read(1000).decode('utf-8', errors='ignore')
                if '#EXTM3U' in content:
                    res_m = re.findall(r'RESOLUTION=([0-9x]+)', content)
                    working_res = res_m[0] if res_m else "720P/标清"
                    working_url = u
                    break
        except Exception:
            continue
    if working_url:
        print(f"✅ {name:10} -> 可用! 分辨率: {working_res} ({working_url[:40]}...)")
    else:
        print(f"❌ {name:10} -> 暂无首选响应")
