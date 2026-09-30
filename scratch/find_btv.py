import json

with open('/home/wym/.gemini/antigravity-ide/brain/1a15c4af-8862-4b3c-98d0-ad7398d67a75/scratch/html5_2001.json', 'r') as f:
    data = json.load(f)

for p in data.get('live_policy', []) + data.get('live_policy_ext', []):
    chs = [c.get('channel') for c in p.get('channels', [])]
    if 'btv1' in chs:
        print("Found btv1 in policy group:", p.get('channel_group'))
        print("cn_manifest:", json.dumps(p.get('cn_manifest'), indent=2))
        print("fn_manifest:", json.dumps(p.get('fn_manifest'), indent=2))
