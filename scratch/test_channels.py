import urllib.request, re

# 央视频道稳定纯净源列表 (无 DRM 加密，持久稳定播放，不闪退不绿屏)
cctv_channels = {
    # 官方直连持久流 (480P 纯净流，央视官方 CDN，永不断链)
    "CCTV-1 综合": [
        "https://newbndbd.a.bdydns.com/newbnd/necctv1_2/index.m3u8",
        "https://newbndtxy.liveplay.myqcloud.com/newbnd/necctv1_2/index.m3u8"
    ],
    "CCTV-13 新闻": [
        "https://newbndbd.a.bdydns.com/newbnd/necctv13_2/index.m3u8",
        "https://newbndtxy.liveplay.myqcloud.com/newbnd/necctv13_2/index.m3u8"
    ],

    # 其他央视频道公开稳定纯净源 (720P/480P，无 C-DRM，不闪绿屏)
    "CCTV-2 财经": [
        "http://204.12.221.218:8181/3m1080p/cctv2.m3u8",
        "http://74.91.26.218:82/live/cctv2hd.m3u8"
    ],
    "CCTV-3 综艺": [
        "http://107.150.60.122/live/cctv3hd.m3u8",
        "http://63.141.230.178:82/gslb/zbdq5.m3u8?id=cctv3hd"
    ],
    "CCTV-4 中文国际": [
        "http://74.91.26.218:82/live/cctv4hd.m3u8"
    ],
    "CCTV-5 体育": [
        "http://107.150.60.122/live/cctv5hd.m3u8"
    ],
    "CCTV-5+ 赛事": [
        "http://120.76.248.139/live/bfgd/4200000246.m3u8"
    ],
    "CCTV-6 电影": [
        "http://69.30.245.50/live/cctv6.m3u8"
    ],
    "CCTV-7 国防军事": [
        "http://74.91.26.218:82/live/cctv7hd.m3u8"
    ],
    "CCTV-8 电视剧": [
        "http://bztv.tvbus.cc:8081/cdnlive/cctv8.m3u8"
    ],
    "CCTV-9 纪录": [
        "http://63.141.230.178:82/gslb/zbdq5.m3u8?id=cctv9hd"
    ],
    "CCTV-10 科教": [
        "http://74.91.26.218:82/live/cctv10hd.m3u8"
    ],
    "CCTV-11 戏曲": [
        "http://74.91.26.218:82/live/cctv11hd.m3u8"
    ],
    "CCTV-12 社会与法": [
        "http://107.150.60.122/live/cctv12hd.m3u8"
    ],
    "CCTV-14 少儿": [
        "http://198.204.228.26/live/cctv14hd.m3u8"
    ],
    "CCTV-15 音乐": [
        "http://204.12.221.218:8181/3m1080p/cctv15.m3u8"
    ],
    "CCTV-16 奥林匹克": [
        "http://207.56.13.146:81/cdnlive/cctv16.m3u8"
    ],
    "CCTV-17 农业农村": [
        "http://74.91.26.218:82/live/cctv17hd.m3u8",
        "http://63.141.230.178:82/gslb/zbdq5.m3u8?id=cctv17hd"
    ]
}

print("=== 验证央视频道直连可用性 ===")
headers = {'User-Agent': 'Mozilla/5.0'}
for name, urls in cctv_channels.items():
    success = False
    for u in urls:
        try:
            req = urllib.request.Request(u, headers=headers)
            with urllib.request.urlopen(req, timeout=3) as resp:
                content = resp.read(200).decode('utf-8', errors='ignore')
                if '#EXTM3U' in content or resp.status == 200:
                    print(f"✅ {name:14} -> [HTTP {resp.status}] 播放源有效: {u[:45]}...")
                    success = True
                    break
        except Exception:
            continue
    if not success:
        print(f"❌ {name:14} -> 当前线路暂未连通")
