import urllib.request, re

channel_masters = {
    # 核心总台 (Group: ldncctv) -> 支持 1080P (_pd), 720P (_td)
    "CCTV-1": "https://ldncctvwbcdtxy.liveplay.myqcloud.com/ldncctvwbcd/cdrmldcctv1_1/index.m3u8",
    "CCTV-13": "https://ldncctvwbcdtxy.liveplay.myqcloud.com/ldncctvwbcd/cdrmldcctv13_1/index.m3u8",
    
    # 体育与赛事 (Group: ldcctv) -> 包含 5, 5+, 16
    "CCTV-5": "https://ldcctvwbcdtxy.liveplay.myqcloud.com/ldcctvwbcd/cdrmldcctv5_1/index.m3u8",
    "CCTV-5+": "https://ldcctvwbcdtxy.liveplay.myqcloud.com/ldcctvwbcd/cdrmldcctv5plus_1/index.m3u8",
    "CCTV-16": "https://ldcctvwbcdtxy.liveplay.myqcloud.com/ldcctvwbcd/cdrmldcctv16_1/index.m3u8",

    # 影视/综艺 (Group: ldocctv bd) -> 3, 6, 8
    "CCTV-3": "https://ldocctvwbcdbd.a.bdydns.com/ldocctvwbcd/cdrmldcctv3_1/index.m3u8",
    "CCTV-6": "https://ldocctvwbcdbd.a.bdydns.com/ldocctvwbcd/cdrmldcctv6_1/index.m3u8",
    "CCTV-8": "https://ldocctvwbcdbd.a.bdydns.com/ldocctvwbcd/cdrmldcctv8_1/index.m3u8",

    # 其他央视 (Group: ldocctv txy)
    "CCTV-2": "https://ldocctvwbcdtxy.liveplay.myqcloud.com/ldocctvwbcd/cdrmldcctv2_1/index.m3u8",
    "CCTV-4": "https://ldocctvwbcdtxy.liveplay.myqcloud.com/ldocctvwbcd/cdrmldcctv4_1/index.m3u8",
    "CCTV-7": "https://ldocctvwbcdtxy.liveplay.myqcloud.com/ldocctvwbcd/cdrmldcctv7_1/index.m3u8",
    "CCTV-9": "https://ldocctvwbcdtxy.liveplay.myqcloud.com/ldocctvwbcd/cdrmldcctv9_1/index.m3u8",
    "CCTV-10": "https://ldocctvwbcdtxy.liveplay.myqcloud.com/ldocctvwbcd/cdrmldcctv10_1/index.m3u8",
    "CCTV-11": "https://ldocctvwbcdtxy.liveplay.myqcloud.com/ldocctvwbcd/cdrmldcctv11_1/index.m3u8",
    "CCTV-12": "https://ldocctvwbcdtxy.liveplay.myqcloud.com/ldocctvwbcd/cdrmldcctv12_1/index.m3u8",
    "CCTV-14": "https://ldocctvwbcdtxy.liveplay.myqcloud.com/ldocctvwbcd/cdrmldcctv14_1/index.m3u8",
    "CCTV-15": "https://ldocctvwbcdtxy.liveplay.myqcloud.com/ldocctvwbcd/cdrmldcctv15_1/index.m3u8",
    "CCTV-17": "https://ldocctvwbcdtxy.liveplay.myqcloud.com/ldocctvwbcd/cdrmldcctv17_1/index.m3u8",

    # 卫视 (Group: wstvwbcd)
    "湖南卫视": "https://wstvwbcdtxy.liveplay.myqcloud.com/wstvwbcd/cdrmhunan_1/index.m3u8",
    "东方卫视": "https://wstvwbcdtxy.liveplay.myqcloud.com/wstvwbcd/cdrmdongfang_1/index.m3u8",
    "浙江卫视": "https://wstvwbcdtxy.liveplay.myqcloud.com/wstvwbcd/cdrmzhejiang_1/index.m3u8",
    "江苏卫视": "https://wstvwbcdtxy.liveplay.myqcloud.com/wstvwbcd/cdrmjiangsu_1/index.m3u8",
    "北京卫视": "https://wstvwbcdtxy.liveplay.myqcloud.com/wstvwbcd/cdrmbtv1_1/index.m3u8",
}

headers = {'User-Agent': 'Mozilla/5.0'}
for name, url in channel_masters.items():
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req, timeout=3) as resp:
            content = resp.read().decode('utf-8', errors='ignore')
            res_matches = re.findall(r'RESOLUTION=([0-9x]+)', content)
            print(f"{name:10} -> 可用分辨率: {', '.join(res_matches) if res_matches else '单码率流'}")
    except Exception as e:
        print(f"{name:10} -> 错误: {e}")
