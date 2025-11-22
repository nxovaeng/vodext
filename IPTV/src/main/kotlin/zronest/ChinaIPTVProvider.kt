package nxovaeng

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.newExtractorLink

/**
 * 中国IPTV直播源提供者
 * 数据源: https://github.com/vbskycn/iptv (自动更新，支持IPv4/IPv6)
 */
class ChinaIPTVProvider : MainAPI() {
    override var mainUrl = "https://live.zbds.top"
    override var name = "中国IPTV"
    override var lang = "zh"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    // M3U 播放列表 URL
    private val playlistUrl = "$mainUrl/tv/iptv4.m3u"

    override val mainPage = mainPageOf(
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

    /**
     * 解析 M3U 播放列表
     */
    private suspend fun parseM3U(): List<Channel> {
        val currentTime = System.currentTimeMillis()

        // 检查缓存
        if (cachedChannels != null && (currentTime - cacheTime) < cacheExpiry) {
            return cachedChannels!!
        }

        val channels = mutableListOf<Channel>()

        try {
            val m3uContent = app.get(
                playlistUrl,
                headers = mapOf("User-Agent" to USER_AGENT)
            ).text
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
                    // 这是 URL 行
                    channels.add(
                        Channel(
                            name = currentName,
                            url = line,
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

    /**
     * 备用频道列表（防止在线源失效）
     */
    private fun getBackupChannels(): List<Channel> {
        return listOf(
            // 央视频道
            Channel(
                "CCTV-1 综合",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226231/index.m3u8",
                "央视频道"
            ),
            Channel(
                "CCTV-2 财经",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226195/index.m3u8",
                "央视频道"
            ),
            Channel(
                "CCTV-3 综艺",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226397/index.m3u8",
                "央视频道"
            ),
            Channel(
                "CCTV-4 中文国际",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226191/index.m3u8",
                "央视频道"
            ),
            Channel(
                "CCTV-5 体育",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226395/index.m3u8",
                "央视频道"
            ),
            Channel(
                "CCTV-6 电影",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226393/index.m3u8",
                "央视频道"
            ),
            Channel(
                "CCTV-7 国防军事",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226192/index.m3u8",
                "央视频道"
            ),
            Channel(
                "CCTV-8 电视剧",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226391/index.m3u8",
                "央视频道"
            ),
            Channel(
                "CCTV-9 纪录",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226197/index.m3u8",
                "央视频道"
            ),
            Channel(
                "CCTV-10 科教",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226189/index.m3u8",
                "央视频道"
            ),
            Channel(
                "CCTV-11 戏曲",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226240/index.m3u8",
                "央视频道"
            ),
            Channel(
                "CCTV-12 社会与法",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226190/index.m3u8",
                "央视频道"
            ),
            Channel(
                "CCTV-13 新闻",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226233/index.m3u8",
                "央视频道"
            ),
            Channel(
                "CCTV-14 少儿",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226193/index.m3u8",
                "央视频道"
            ),
            Channel(
                "CCTV-15 音乐",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221225785/index.m3u8",
                "央视频道"
            ),

            // 卫视频道
            Channel(
                "湖南卫视",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226211/index.m3u8",
                "卫视频道"
            ),
            Channel(
                "浙江卫视",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226199/index.m3u8",
                "卫视频道"
            ),
            Channel(
                "江苏卫视",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226200/index.m3u8",
                "卫视频道"
            ),
            Channel(
                "东方卫视",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226217/index.m3u8",
                "卫视频道"
            ),
            Channel(
                "北京卫视",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226222/index.m3u8",
                "卫视频道"
            ),
            Channel(
                "深圳卫视",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226205/index.m3u8",
                "卫视频道"
            ),
            Channel(
                "广东卫视",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226216/index.m3u8",
                "卫视频道"
            ),
            Channel(
                "安徽卫视",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226203/index.m3u8",
                "卫视频道"
            ),
            Channel(
                "天津卫视",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226204/index.m3u8",
                "卫视频道"
            ),
            Channel(
                "重庆卫视",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226202/index.m3u8",
                "卫视频道"
            ),
            Channel(
                "山东卫视",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226209/index.m3u8",
                "卫视频道"
            ),
            Channel(
                "黑龙江卫视",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226215/index.m3u8",
                "卫视频道"
            ),
            Channel(
                "河北卫视",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221225750/index.m3u8",
                "卫视频道"
            ),
            Channel(
                "辽宁卫视",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226201/index.m3u8",
                "卫视频道"
            ),
            Channel(
                "湖北卫视",
                "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226206/index.m3u8",
                "卫视频道"
            )
        )
    }

    /**
     * 根据分类过滤频道
     */
    private fun filterChannelsByCategory(channels: List<Channel>, category: String): List<Channel> {
        return when (category) {
            "cctv" -> channels.filter {
                it.name.startsWith("CCTV") || it.group.contains("央视", ignoreCase = true)
            }

            "satellite" -> channels.filter {
                it.name.contains("卫视") || it.group.contains("卫视", ignoreCase = true)
            }

            "local" -> channels.filter {
                it.group.contains("地方", ignoreCase = true) ||
                        it.group.contains("本地", ignoreCase = true)
            }

            "special" -> channels.filter {
                !it.name.startsWith("CCTV") &&
                        !it.name.contains("卫视") &&
                        !it.group.contains("地方", ignoreCase = true)
            }

            else -> channels
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val channels = parseM3U()
        val filteredChannels = filterChannelsByCategory(channels, request.data)

        val searchResults = filteredChannels.map { channel ->
            newMovieSearchResponse(
                channel.name,
                channel.url,
                TvType.Live
            ) {
                this.posterUrl = channel.logo.ifEmpty {
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

        return channels.filter {
            it.name.contains(query, ignoreCase = true)
        }.map { channel ->
            newMovieSearchResponse(
                channel.name,
                channel.url,
                TvType.Live
            ) {
                this.posterUrl = channel.logo.ifEmpty {
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
            return newMovieLoadResponse(
                "直播频道",
                url,
                TvType.Live,
                url
            ) {
                this.posterUrl = "https://www.google.com/s2/favicons?domain=tv.cctv.com&sz=128"
            }
        }
        
        // 获取同组的所有频道
        val groupChannels = if (currentChannel.group.isNotEmpty()) {
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
        val episodes = groupChannels.mapIndexed { index, channel ->
            newEpisode(channel.url) {
                this.name = channel.name
                this.episode = index + 1
                this.posterUrl = channel.logo.ifEmpty {
                    "https://www.google.com/s2/favicons?domain=tv.cctv.com&sz=128"
                }
            }
        }
        
        // 确定分组名称
        val groupName = currentChannel.group.ifEmpty {
            when {
                currentChannel.name.startsWith("CCTV") -> "央视频道"
                currentChannel.name.contains("卫视") -> "卫视频道"
                else -> "直播频道"
            }
        }
        
        return newTvSeriesLoadResponse(
            currentChannel.name,
            url,
            TvType.Live,
            episodes
        ) {
            this.posterUrl = currentChannel.logo.ifEmpty {
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
            M3u8Helper.generateM3u8(
                this.name,
                data,
                referer = mainUrl
            ).forEach(callback)
        } else {
            // 直接返回链接
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    data
                ) {
                }
            )
        }

        return true
    }
}
