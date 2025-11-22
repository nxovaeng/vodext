# 中国 IPTV 直播扩展

CloudStream 3 的中国 IPTV 直播源扩展，支持央视、卫视等频道。

## 功能特点

✅ **多频道分类**
- 央视频道 (CCTV-1 至 CCTV-15)
- 卫视频道 (湖南、浙江、江苏、东方等)
- 地方频道
- 特色频道

✅ **自动更新**
- 使用 GitHub 上的公开 IPTV 源
- 每小时自动刷新频道列表
- 内置备用频道列表

✅ **完整功能**
- 频道搜索
- 分类浏览
- M3U8 直播流播放
- 频道 Logo 显示

## 数据源

主要数据源: [vbskycn/iptv](https://github.com/vbskycn/iptv)
- 自动更新，每 6 小时刷新
- 支持 IPv4 和 IPv6
- 包含数千个高清频道

备用数据源:
- [hujingguang/ChinaIPTV](https://github.com/hujingguang/ChinaIPTV)
- [BurningC4/Chinese-IPTV](https://github.com/BurningC4/Chinese-IPTV)

## 使用方法

### 安装
1. 在 CloudStream 3 中添加此仓库
2. 安装 "中国IPTV" 扩展
3. 重启应用

### 浏览频道
1. 打开扩展
2. 选择分类：
   - 央视频道
   - 卫视频道
   - 地方频道
   - 特色频道
3. 点击频道开始播放

### 搜索频道
在搜索框输入频道名称，如 "CCTV-1" 或 "湖南卫视"

## 频道列表

### 央视频道 (CCTV)
- CCTV-1 综合
- CCTV-2 财经
- CCTV-3 综艺
- CCTV-4 中文国际
- CCTV-5 体育
- CCTV-6 电影
- CCTV-7 国防军事
- CCTV-8 电视剧
- CCTV-9 纪录
- CCTV-10 科教
- CCTV-11 戏曲
- CCTV-12 社会与法
- CCTV-13 新闻
- CCTV-14 少儿
- CCTV-15 音乐

### 卫视频道
- 湖南卫视
- 浙江卫视
- 江苏卫视
- 东方卫视
- 北京卫视
- 深圳卫视
- 广东卫视
- 安徽卫视
- 天津卫视
- 重庆卫视
- 山东卫视
- 黑龙江卫视
- 河北卫视
- 辽宁卫视
- 湖北卫视

## 技术实现

### M3U 播放列表解析
```kotlin
// 自动解析 M3U 格式
#EXTINF:-1 tvg-logo="logo_url" group-title="分组",频道名称
http://example.com/channel.m3u8
```

### 缓存机制
- 频道列表缓存 1 小时
- 减少网络请求
- 提高加载速度

### 备用机制
- 在线源失效时自动切换到内置频道
- 确保基本频道始终可用

## 注意事项

⚠️ **网络要求**
- 部分频道可能需要中国大陆 IP
- 海外用户可能需要 VPN

⚠️ **播放问题**
- 某些频道可能暂时不可用
- 建议尝试不同的播放源
- 使用最新版本的 CloudStream

⚠️ **版权声明**
- 本扩展仅聚合公开的 IPTV 源
- 不提供任何视频内容
- 请遵守当地法律法规

## 更新日志

### v1.0.0 (2025-11-22)
- ✅ 初始版本发布
- ✅ 支持央视和卫视频道
- ✅ M3U 播放列表解析
- ✅ 频道搜索功能
- ✅ 自动缓存机制

## 贡献

欢迎提交 Issue 和 Pull Request！

## 许可证

MIT License

## 相关链接

- [CloudStream 3](https://github.com/recloudstream/cloudstream)
- [IPTV 源列表](https://github.com/iptv-org/iptv)
- [中国 IPTV 源](https://github.com/vbskycn/iptv)
