package nxovaeng

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.newExtractorLink

/** 中国IPTV直播源提供者 数据源: https://github.com/vbskycn/iptv (自动更新，支持IPv4/IPv6) */
class ChinaIPTVProvider : MainAPI() {
    override var mainUrl = "https://live.zbds.top"
    override var name = "中国IPTV"
    override var lang = "zh"
    override val hasMainPage = true
    override val hasDownloadSupport = false // 直播流不支持下载
    override val supportedTypes = setOf(TvType.Live)

    // M3U 播放列表 URL
    private val playlistUrl = "$mainUrl/tv/iptv4.m3u"

    override val mainPage =
            mainPageOf(
                    "cctv" to "央视频道",
                    "satellite" to "卫视频道",
                    "local" to "地方频道",
                    "special" to "特色频道"
            )

    // 频道数据类
    data class Channel(
            val name: String,
            val url: String,
            val group: String = "",
            val logo: String = ""
    )

    // 缓存的频道列表
    private var cachedChannels: List<Channel>? = null
    private var cacheTime: Long = 0
    private val cacheExpiry = 3600000L // 1小时缓存

    /** 解析 M3U 播放列表 */
    private suspend fun parseM3U(): List<Channel> {
        val currentTime = System.currentTimeMillis()

        // 检查缓存
        if (cachedChannels != null && (currentTime - cacheTime) < cacheExpiry) {
            return cachedChannels!!
        }

        val channels = mutableListOf<Channel>()

        try {
            val m3uContent = app.get(playlistUrl, headers = mapOf("User-Agent" to USER_AGENT)).text
            val lines = m3uContent.lines()

            var currentName = ""
            var currentGroup = ""
            var currentLogo = ""

            for (i in lines.indices) {
                val line = lines[i].trim()

                if (line.startsWith("#EXTINF:")) {
                    // 解析频道信息
                    // 格式: #EXTINF:-1 tvg-logo="logo_url" group-title="分组",频道名称

                    // 提取频道名称
                    currentName = line.substringAfterLast(",").trim()

                    // 提取分组
                    val groupMatch = Regex("""group-title="([^"]+)"""").find(line)
                    currentGroup = groupMatch?.groupValues?.get(1) ?: ""

                    // 提取 logo
                    val logoMatch = Regex("""tvg-logo="([^"]+)"""").find(line)
                    currentLogo = logoMatch?.groupValues?.get(1) ?: ""
                } else if (line.isNotEmpty() && !line.startsWith("#") && currentName.isNotEmpty()) {
                    // 优先匹配央视稳定持久源，防止第三方 M3U 中的临时酒店源几分钟后断流
                    val normalizedKey = currentName.uppercase().replace(" ", "").replace("-", "")
                    val resolvedUrl = stableCCTVMap[normalizedKey]
                            ?: stableCCTVMap[currentName]
                            ?: line

                    channels.add(
                            Channel(
                                    name = currentName,
                                    url = resolvedUrl,
                                    group = currentGroup,
                                    logo = currentLogo
                            )
                    )
                    currentName = ""
                }
            }
        } catch (e: Exception) {
            // 如果无法获取在线列表，使用内置的备用频道列表
            channels.addAll(getBackupChannels())
        }

        cachedChannels = channels
        cacheTime = currentTime
        return channels
    }

    /** 央视官方持久源与稳定直播源映射表 (避免第三方源频繁断流) */
    private val stableCCTVMap = mapOf(
            "CCTV1" to "https://newbndbd.a.bdydns.com/newbnd/necctv1_2/index.m3u8",
            "CCTV1综合" to "https://newbndbd.a.bdydns.com/newbnd/necctv1_2/index.m3u8",
            "CCTV2" to "http://74.91.26.218:82/live/cctv2hd.m3u8",
            "CCTV2财经" to "http://74.91.26.218:82/live/cctv2hd.m3u8",
            "CCTV3" to "http://107.150.60.122/live/cctv3hd.m3u8",
            "CCTV3综艺" to "http://107.150.60.122/live/cctv3hd.m3u8",
            "CCTV4" to "http://74.91.26.218:82/live/cctv4hd.m3u8",
            "CCTV4中文国际" to "http://74.91.26.218:82/live/cctv4hd.m3u8",
            "CCTV5" to "http://107.150.60.122/live/cctv5hd.m3u8",
            "CCTV5体育" to "http://107.150.60.122/live/cctv5hd.m3u8",
            "CCTV5+" to "http://120.76.248.139/live/bfgd/4200000246.m3u8",
            "CCTV5PLUS" to "http://120.76.248.139/live/bfgd/4200000246.m3u8",
            "CCTV5+赛事" to "http://120.76.248.139/live/bfgd/4200000246.m3u8",
            "CCTV6" to "http://69.30.245.50/live/cctv6.m3u8",
            "CCTV6电影" to "http://69.30.245.50/live/cctv6.m3u8",
            "CCTV7" to "http://74.91.26.218:82/live/cctv7hd.m3u8",
            "CCTV7国防军事" to "http://74.91.26.218:82/live/cctv7hd.m3u8",
            "CCTV8" to "http://bztv.tvbus.cc:8081/cdnlive/cctv8.m3u8",
            "CCTV8电视剧" to "http://bztv.tvbus.cc:8081/cdnlive/cctv8.m3u8",
            "CCTV9" to "http://63.141.230.178:82/gslb/zbdq5.m3u8?id=cctv9hd",
            "CCTV9纪录" to "http://63.141.230.178:82/gslb/zbdq5.m3u8?id=cctv9hd",
            "CCTV10" to "http://74.91.26.218:82/live/cctv10hd.m3u8",
            "CCTV10科教" to "http://74.91.26.218:82/live/cctv10hd.m3u8",
            "CCTV11" to "http://74.91.26.218:82/live/cctv11hd.m3u8",
            "CCTV11戏曲" to "http://74.91.26.218:82/live/cctv11hd.m3u8",
            "CCTV12" to "http://107.150.60.122/live/cctv12hd.m3u8",
            "CCTV12社会与法" to "http://107.150.60.122/live/cctv12hd.m3u8",
            "CCTV13" to "https://newbndbd.a.bdydns.com/newbnd/necctv13_2/index.m3u8",
            "CCTV13新闻" to "https://newbndbd.a.bdydns.com/newbnd/necctv13_2/index.m3u8",
            "CCTV14" to "http://198.204.228.26/live/cctv14hd.m3u8",
            "CCTV14少儿" to "http://198.204.228.26/live/cctv14hd.m3u8",
            "CCTV15" to "http://204.12.221.218:8181/3m1080p/cctv15.m3u8",
            "CCTV15音乐" to "http://204.12.221.218:8181/3m1080p/cctv15.m3u8",
            "CCTV16" to "http://207.56.13.146:81/cdnlive/cctv16.m3u8",
            "CCTV16奥林匹克" to "http://207.56.13.146:81/cdnlive/cctv16.m3u8",
            "CCTV17" to "http://74.91.26.218:82/live/cctv17hd.m3u8",
            "CCTV17农业农村" to "http://74.91.26.218:82/live/cctv17hd.m3u8"
    )

    /** 备用频道列表（防止在线源失效） */
    private fun getBackupChannels(): List<Channel> {
        return listOf(
                // 央视频道（全部采用官方持久直连流 + 稳定广播流，杜绝几分钟断链）
                Channel(
                        "CCTV-1 综合",
                        "https://newbndbd.a.bdydns.com/newbnd/necctv1_2/index.m3u8",
                        "央视频道",
                        "https://live.fanmingming.com/tv/CCTV1.png"
                ),
                Channel(
                        "CCTV-2 财经",
                        "http://74.91.26.218:82/live/cctv2hd.m3u8",
                        "央视频道",
                        "https://live.fanmingming.com/tv/CCTV2.png"
                ),
                Channel(
                        "CCTV-3 综艺",
                        "http://107.150.60.122/live/cctv3hd.m3u8",
                        "央视频道",
                        "https://live.fanmingming.com/tv/CCTV3.png"
                ),
                Channel(
                        "CCTV-4 中文国际",
                        "http://74.91.26.218:82/live/cctv4hd.m3u8",
                        "央视频道",
                        "https://live.fanmingming.com/tv/CCTV4.png"
                ),
                Channel(
                        "CCTV-5 体育",
                        "http://107.150.60.122/live/cctv5hd.m3u8",
                        "央视频道",
                        "https://live.fanmingming.com/tv/CCTV5.png"
                ),
                Channel(
                        "CCTV-5+ 赛事",
                        "http://120.76.248.139/live/bfgd/4200000246.m3u8",
                        "央视频道",
                        "https://live.fanmingming.com/tv/CCTV5+.png"
                ),
                Channel(
                        "CCTV-6 电影",
                        "http://69.30.245.50/live/cctv6.m3u8",
                        "央视频道",
                        "https://live.fanmingming.com/tv/CCTV6.png"
                ),
                Channel(
                        "CCTV-7 国防军事",
                        "http://74.91.26.218:82/live/cctv7hd.m3u8",
                        "央视频道",
                        "https://live.fanmingming.com/tv/CCTV7.png"
                ),
                Channel(
                        "CCTV-8 电视剧",
                        "http://bztv.tvbus.cc:8081/cdnlive/cctv8.m3u8",
                        "央视频道",
                        "https://live.fanmingming.com/tv/CCTV8.png"
                ),
                Channel(
                        "CCTV-9 纪录",
                        "http://63.141.230.178:82/gslb/zbdq5.m3u8?id=cctv9hd",
                        "央视频道",
                        "https://live.fanmingming.com/tv/CCTV9.png"
                ),
                Channel(
                        "CCTV-10 科教",
                        "http://74.91.26.218:82/live/cctv10hd.m3u8",
                        "央视频道",
                        "https://live.fanmingming.com/tv/CCTV10.png"
                ),
                Channel(
                        "CCTV-11 戏曲",
                        "http://74.91.26.218:82/live/cctv11hd.m3u8",
                        "央视频道",
                        "https://live.fanmingming.com/tv/CCTV11.png"
                ),
                Channel(
                        "CCTV-12 社会与法",
                        "http://107.150.60.122/live/cctv12hd.m3u8",
                        "央视频道",
                        "https://live.fanmingming.com/tv/CCTV12.png"
                ),
                Channel(
                        "CCTV-13 新闻",
                        "https://newbndbd.a.bdydns.com/newbnd/necctv13_2/index.m3u8",
                        "央视频道",
                        "https://live.fanmingming.com/tv/CCTV13.png"
                ),
                Channel(
                        "CCTV-14 少儿",
                        "http://198.204.228.26/live/cctv14hd.m3u8",
                        "央视频道",
                        "https://live.fanmingming.com/tv/CCTV14.png"
                ),
                Channel(
                        "CCTV-15 音乐",
                        "http://204.12.221.218:8181/3m1080p/cctv15.m3u8",
                        "央视频道",
                        "https://live.fanmingming.com/tv/CCTV15.png"
                ),
                Channel(
                        "CCTV-16 奥林匹克",
                        "http://207.56.13.146:81/cdnlive/cctv16.m3u8",
                        "央视频道",
                        "https://live.fanmingming.com/tv/CCTV16.png"
                ),
                Channel(
                        "CCTV-17 农业农村",
                        "http://74.91.26.218:82/live/cctv17hd.m3u8",
                        "央视频道",
                        "https://live.fanmingming.com/tv/CCTV17.png"
                )
        )
    }

    /** 根据分类过滤频道 */
    private fun filterChannelsByCategory(channels: List<Channel>, category: String): List<Channel> {
        return when (category) {
            "cctv" ->
                    channels.filter {
                        it.name.startsWith("CCTV") || it.group.contains("央视", ignoreCase = true)
                    }
            "satellite" ->
                    channels.filter {
                        it.name.contains("卫视") || it.group.contains("卫视", ignoreCase = true)
                    }
            "local" ->
                    channels.filter {
                        it.group.contains("地方", ignoreCase = true) ||
                                it.group.contains("本地", ignoreCase = true)
                    }
            "special" ->
                    channels.filter {
                        !it.name.startsWith("CCTV") &&
                                !it.name.contains("卫视") &&
                                !it.group.contains("地方", ignoreCase = true)
                    }
            else -> channels
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val channels = parseM3U()
        val filteredChannels = filterChannelsByCategory(channels, request.data)

        val searchResults =
                filteredChannels.map { channel ->
                    newMovieSearchResponse(channel.name, channel.url, TvType.Live) {
                        this.posterUrl =
                                channel.logo.ifEmpty {
                                    "https://www.google.com/s2/favicons?domain=tv.cctv.com&sz=128"
                                }
                    }
                }

        return newHomePageResponse(
                listOf(HomePageList(request.name, searchResults, isHorizontalImages = true)),
                hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val channels = parseM3U()

        return channels.filter { it.name.contains(query, ignoreCase = true) }.map { channel ->
            newMovieSearchResponse(channel.name, channel.url, TvType.Live) {
                this.posterUrl =
                        channel.logo.ifEmpty {
                            "https://www.google.com/s2/favicons?domain=tv.cctv.com&sz=128"
                        }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        // URL 就是直播流地址，我们需要找到它所属的频道组
        val allChannels = parseM3U()

        // 根据 URL 找到当前频道
        val currentChannel = allChannels.find { it.url == url }

        if (currentChannel == null) {
            // 如果找不到，返回单个频道
            return newMovieLoadResponse("直播频道", url, TvType.Live, url) {
                this.posterUrl = "https://www.google.com/s2/favicons?domain=tv.cctv.com&sz=128"
            }
        }

        // 获取同组的所有频道
        val groupChannels =
                if (currentChannel.group.isNotEmpty()) {
                    allChannels.filter { it.group == currentChannel.group }
                } else {
                    // 如果没有分组信息，尝试智能分组
                    when {
                        currentChannel.name.startsWith("CCTV") ->
                                allChannels.filter { it.name.startsWith("CCTV") }
                        currentChannel.name.contains("卫视") ->
                                allChannels.filter { it.name.contains("卫视") }
                        else -> listOf(currentChannel)
                    }
                }

        // 将同组频道转换为剧集列表
        val episodes =
                groupChannels.mapIndexed { index, channel ->
                    newEpisode(channel.url) {
                        this.name = channel.name
                        this.episode = index + 1
                        this.posterUrl =
                                channel.logo.ifEmpty {
                                    "https://www.google.com/s2/favicons?domain=tv.cctv.com&sz=128"
                                }
                    }
                }

        // 确定分组名称
        val groupName =
                currentChannel.group.ifEmpty {
                    when {
                        currentChannel.name.startsWith("CCTV") -> "央视频道"
                        currentChannel.name.contains("卫视") -> "卫视频道"
                        else -> "直播频道"
                    }
                }

        return newTvSeriesLoadResponse(currentChannel.name, url, TvType.TvSeries, episodes) {
            this.posterUrl =
                    currentChannel.logo.ifEmpty {
                        "https://www.google.com/s2/favicons?domain=tv.cctv.com&sz=128"
                    }
            this.plot = "📺 $groupName - 共 ${episodes.size} 个频道\n\n点击下方频道列表快速换台"
        }
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        // data 就是 M3U8 URL
        if (data.contains(".m3u8")) {
            M3u8Helper.generateM3u8(this.name, data, referer = mainUrl).forEach(callback)
        } else {
            // 直接返回链接
            callback.invoke(newExtractorLink(this.name, this.name, data) {})
        }

        return true
    }
}
