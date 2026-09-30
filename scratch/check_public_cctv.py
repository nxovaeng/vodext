import urllib.request, re

def check_m3u(url):
    print(f"\nChecking {url}...")
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            content = resp.read().decode('utf-8', errors='ignore')
    except Exception as e:
        print(f"Error fetching {url}: {e}")
        return

    lines = content.splitlines()
    cctv_entries = []
    current_name = None
    for line in lines:
        line = line.strip()
        if line.startswith("#EXTINF"):
            m = re.search(r',(.+)$', line)
            if m and 'CCTV' in m.group(1):
                current_name = m.group(1).strip()
        elif line.startswith("http") and current_name:
            cctv_entries.append((current_name, line))
            current_name = None

    print(f"Found {len(cctv_entries)} CCTV channels.")
    for name, stream_url in cctv_entries[:10]:
        try:
            req2 = urllib.request.Request(stream_url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req2, timeout=2) as resp2:
                head = resp2.read(100).decode('utf-8', errors='ignore')
                is_m3u8 = '#EXTM3U' in head
                print(f"  [{resp2.status}] {name:15} -> {'OK' if is_m3u8 else 'Bad'} ({stream_url[:50]}...)")
        except Exception as e:
            print(f"  [FAIL] {name:15} -> {e}")

check_m3u('https://raw.githubusercontent.com/vbskycn/iptv/master/tv/iptv4.m3u')
check_m3u('https://raw.githubusercontent.com/fanmingming/live/main/tv/m3u/ipv6.m3u')
