import urllib.request

ts_url = "https://ldocctvwbcdbd.a.bdydns.com/ldocctvwbcd/cdrmldcctv6_1/cdrmldcctv6_1_1800-1790763419.ts"
headers = {'User-Agent': 'Mozilla/5.0'}
try:
    req = urllib.request.Request(ts_url, headers=headers)
    with urllib.request.urlopen(req, timeout=5) as resp:
        data = resp.read(500000)
    print("Downloaded bytes:", len(data))
    
    # Parse MPEG-TS packets (188 bytes)
    pmt_pid = None
    video_pid = None
    stream_type = None

    for i in range(0, len(data) - 188, 188):
        pkt = data[i:i+188]
        if pkt[0] != 0x47:
            continue
        pid = ((pkt[1] & 0x1f) << 8) | pkt[2]
        payload_start = (pkt[1] & 0x40) != 0
        adapt_field_ctrl = (pkt[3] & 0x30) >> 4
        
        offset = 4
        if adapt_field_ctrl in (2, 3):
            adapt_len = pkt[4]
            offset += 1 + adapt_len
        if offset >= 188:
            continue
            
        payload = pkt[offset:]
        
        # PAT
        if pid == 0:
            if payload_start:
                pointer = payload[0]
                pat_data = payload[1+pointer:]
                # table id 0, section length
                sec_len = ((pat_data[1] & 0x0f) << 8) | pat_data[2]
                # program entries start at offset 8
                idx = 8
                while idx < sec_len + 3 - 4:
                    prog_num = (pat_data[idx] << 8) | pat_data[idx+1]
                    prog_pid = ((pat_data[idx+2] & 0x1f) << 8) | pat_data[idx+3]
                    if prog_num != 0:
                        pmt_pid = prog_pid
                        print(f"PAT found: prog_num={prog_num}, pmt_pid=0x{pmt_pid:x}")
                        break
                    idx += 4
        # PMT
        elif pid == pmt_pid:
            if payload_start:
                pointer = payload[0]
                pmt_data = payload[1+pointer:]
                sec_len = ((pmt_data[1] & 0x0f) << 8) | pmt_data[2]
                pcr_pid = ((pmt_data[8] & 0x1f) << 8) | pmt_data[9]
                prog_info_len = ((pmt_data[10] & 0x0f) << 8) | pmt_data[11]
                idx = 12 + prog_info_len
                while idx < sec_len + 3 - 4:
                    stype = pmt_data[idx]
                    elem_pid = ((pmt_data[idx+1] & 0x1f) << 8) | pmt_data[idx+2]
                    es_info_len = ((pmt_data[idx+3] & 0x0f) << 8) | pmt_data[idx+4]
                    print(f"PMT entry: stream_type=0x{stype:02x}, pid=0x{elem_pid:x}")
                    if stype in [0x1b, 0x24, 0x02, 0xd1, 0x42]: # H.264 is 0x1b, H.265 is 0x24, AVS+ is 0xd1, AVS2 is 0xd2 or 0x42
                        video_pid = elem_pid
                        stream_type = stype
                    idx += 5 + es_info_len
            if video_pid:
                break
    
    type_map = {0x1b: "H.264/AVC", 0x24: "H.265/HEVC", 0xd1: "AVS+", 0xd2: "AVS2", 0x02: "MPEG-2 Video"}
    print(f"Identified Video: PID=0x{video_pid:x}, Type=0x{stream_type:02x} ({type_map.get(stream_type, 'Unknown')})")

except Exception as e:
    print("Error:", e)
