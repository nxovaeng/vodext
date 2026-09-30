import json

with open('/home/wym/.gemini/antigravity-ide/brain/1a15c4af-8862-4b3c-98d0-ad7398d67a75/scratch/html5_2001.json', 'r') as f:
    data = json.load(f)

for p in data.get('live_policy', []) + data.get('live_policy_ext', []):
    chs = [c.get('channel') for c in p.get('channels', []) if 'cctv' in c.get('channel', '')]
    if chs:
        print(f"Group: {p.get('channel_group')}, Channels: {chs}")
        manifest = p.get('cn_manifest', {})
        for mtype, urls in manifest.items():
            if 'hls' in mtype and 'pic' not in mtype:
                print(f"    {mtype}: {urls[0] if urls else ''}")
