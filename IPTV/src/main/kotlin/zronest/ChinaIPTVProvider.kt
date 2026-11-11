package nxovaeng

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

/**
 * 中国IPTV直播源提供者（整合 CCTV 官方稳定流、CNTV 实时 EPG 节目单与多线路切换）
 * 数据源: 官方 CDN 直连 + https://github.com/cluntop/tvbox
 */
open class ChinaIPTVProvider : MainAPI() {
    override var mainUrl = "https://clun.top"
    override var name = "中国IPTV"
    override var lang = "zh"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live, TvType.TvSeries)

    private val playlistUrl = "$mainUrl/lib/iptv.m3u"

    override val mainPage = mainPageOf(
        "cctv" to "央视频道",
        "satellite" to "卫视频道",
        "4k" to "4K/8K专区",
        "gat" to "港澳台",
        "local" to "地方频道",
        "special" to "特色专区"
    )

    data class ChannelSource(
        val name: String,
        val url: String
    )

    data class Channel(
        val id: String,
        val name: String,
        val group: String,
        val logo: String,
        val sources: MutableList<ChannelSource> = mutableListOf(),
        val cctvId: String? = null
    )

    private var cachedChannels: List<Channel>? = null
    private var cacheTime: Long = 0
    private val cacheExpiry = 3600000L // 1小时缓存

    // CNTV 实时节目单缓存 (10分钟)
    private val epgCache = mutableMapOf<String, Pair<String, Long>>()
    private val epgCacheExpiry = 600000L

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val DEFAULT_LOGO = "https://live.fanmingming.com/tv/CCTV1.png"

        // 央视频道标准名称及 EPG ID 映射
        private val CCTV_MAP = mapOf(
            "CCTV1" to ("CCTV-1 综合" to "cctv1"),
            "CCTV2" to ("CCTV-2 财经" to "cctv2"),
            "CCTV3" to ("CCTV-3 综艺" to "cctv3"),
            "CCTV4" to ("CCTV-4 中文国际" to "cctv4"),
            "CCTV5" to ("CCTV-5 体育" to "cctv5"),
            "CCTV5+" to ("CCTV-5+ 赛事" to "cctv5plus"),
            "CCTV5PLUS" to ("CCTV-5+ 赛事" to "cctv5plus"),
            "CCTV6" to ("CCTV-6 电影" to "cctv6"),
            "CCTV7" to ("CCTV-7 国防军事" to "cctv7"),
            "CCTV8" to ("CCTV-8 电视剧" to "cctv8"),
            "CCTV9" to ("CCTV-9 纪录" to "cctvjilu"),
            "CCTV10" to ("CCTV-10 科教" to "cctv10"),
            "CCTV11" to ("CCTV-11 戏曲" to "cctv11"),
            "CCTV12" to ("CCTV-12 社会与法" to "cctv12"),
            "CCTV13" to ("CCTV-13 新闻" to "cctv13"),
            "CCTV14" to ("CCTV-14 少儿" to "cctvchild"),
            "CCTV15" to ("CCTV-15 音乐" to "cctv15"),
            "CCTV16" to ("CCTV-16 奥林匹克" to "cctv16"),
            "CCTV17" to ("CCTV-17 农业农村" to "cctv17"),
            "CCTV4K" to ("CCTV-4K 超高清" to null),
            "CCTV8K" to ("CCTV-8K 超高清" to null)
        )

        // 央视默认排序
        private val CCTV_ORDER = listOf(
            "CCTV-1 综合", "CCTV-2 财经", "CCTV-3 综艺", "CCTV-4 中文国际",
            "CCTV-5 体育", "CCTV-5+ 赛事", "CCTV-6 电影", "CCTV-7 国防军事",
            "CCTV-8 电视剧", "CCTV-9 纪录", "CCTV-10 科教", "CCTV-11 戏曲",
            "CCTV-12 社会与法", "CCTV-13 新闻", "CCTV-14 少儿", "CCTV-15 音乐",
            "CCTV-16 奥林匹克", "CCTV-17 农业农村", "CCTV-4K 超高清", "CCTV-8K 超高清"
        )

        // 省份列表（用于地方频道分类识别）
        private val PROVINCES = setOf(
            "北京", "天津", "河北", "山西", "内蒙古", "辽宁", "吉林", "黑龙江",
            "上海", "江苏", "浙江", "安徽", "福建", "江西", "山东", "河南",
            "湖北", "湖南", "广东", "广西", "海南", "重庆", "四川", "贵州",
            "云南", "西藏", "陕西", "甘肃", "青海", "宁夏", "新疆"
        )
    }

    /** 央视官方持久直连纯净源 (免加密、高清晰度、低延迟) */
    private fun getOfficialCCTVChannels(): List<Channel> {
        return listOf(
            Channel(
                id = "cctv1",
                cctvId = "cctv1",
                name = "CCTV-1 综合",
                group = "央视",
                logo = "https://live.fanmingming.com/tv/CCTV1.png",
                sources = mutableListOf(
                    ChannelSource("官方稳定 (IPv4)", "https://newbndbd.a.bdydns.com/newbnd/necctv1_2/index.m3u8"),
                    ChannelSource("官方备用 (IPv4)", "https://newbndtxy.liveplay.myqcloud.com/newbnd/necctv1_2/index.m3u8")
                )
            ),
            Channel(
                id = "cctv2",
                cctvId = "cctv2",
                name = "CCTV-2 财经",
                group = "央视",
                logo = "https://live.fanmingming.com/tv/CCTV2.png",
                sources = mutableListOf(
                    ChannelSource("官方稳定 (IPv4)", "http://204.12.221.218:8181/3m1080p/cctv2.m3u8"),
                    ChannelSource("官方备用 (IPv4)", "http://74.91.26.218:82/live/cctv2hd.m3u8")
                )
            ),
            Channel(
                id = "cctv3",
                cctvId = "cctv3",
                name = "CCTV-3 综艺",
                group = "央视",
                logo = "https://live.fanmingming.com/tv/CCTV3.png",
                sources = mutableListOf(
                    ChannelSource("官方稳定 (IPv4)", "http://107.150.60.122/live/cctv3hd.m3u8"),
                    ChannelSource("官方备用 (IPv4)", "http://63.141.230.178:82/gslb/zbdq5.m3u8?id=cctv3hd")
                )
            ),
            Channel(
                id = "cctv4",
                cctvId = "cctv4",
                name = "CCTV-4 中文国际",
                group = "央视",
                logo = "https://live.fanmingming.com/tv/CCTV4.png",
                sources = mutableListOf(
                    ChannelSource("官方稳定 (IPv4)", "http://74.91.26.218:82/live/cctv4hd.m3u8")
                )
            ),
            Channel(
                id = "cctv5",
                cctvId = "cctv5",
                name = "CCTV-5 体育",
                group = "央视",
                logo = "https://live.fanmingming.com/tv/CCTV5.png",
                sources = mutableListOf(
                    ChannelSource("官方稳定 (IPv4)", "http://107.150.60.122/live/cctv5hd.m3u8")
                )
            ),
            Channel(
                id = "cctv5plus",
                cctvId = "cctv5plus",
                name = "CCTV-5+ 赛事",
                group = "央视",
                logo = "https://live.fanmingming.com/tv/CCTV5+.png",
                sources = mutableListOf(
                    ChannelSource("官方稳定 (IPv4)", "http://120.76.248.139/live/bfgd/4200000246.m3u8"),
                    ChannelSource("官方备用 (IPv4)", "http://120.76.248.139/live/bfgd/4200000064.m3u8")
                )
            ),
            Channel(
                id = "cctv6",
                cctvId = "cctv6",
                name = "CCTV-6 电影",
                group = "央视",
                logo = "https://live.fanmingming.com/tv/CCTV6.png",
                sources = mutableListOf(
                    ChannelSource("官方稳定 (IPv4)", "http://69.30.245.50/live/cctv6.m3u8")
                )
            ),
            Channel(
                id = "cctv7",
                cctvId = "cctv7",
                name = "CCTV-7 国防军事",
                group = "央视",
                logo = "https://live.fanmingming.com/tv/CCTV7.png",
                sources = mutableListOf(
                    ChannelSource("官方稳定 (IPv4)", "http://74.91.26.218:82/live/cctv7hd.m3u8")
                )
            ),
            Channel(
                id = "cctv8",
                cctvId = "cctv8",
                name = "CCTV-8 电视剧",
                group = "央视",
                logo = "https://live.fanmingming.com/tv/CCTV8.png",
                sources = mutableListOf(
                    ChannelSource("官方稳定 (IPv4)", "http://bztv.tvbus.cc:8081/cdnlive/cctv8.m3u8"),
                    ChannelSource("官方备用 (IPv4)", "http://204.12.221.218:8181/3m1080p/cctv8.m3u8")
                )
            ),
            Channel(
                id = "cctvjilu",
                cctvId = "cctvjilu",
                name = "CCTV-9 纪录",
                group = "央视",
                logo = "https://live.fanmingming.com/tv/CCTV9.png",
                sources = mutableListOf(
                    ChannelSource("官方稳定 (IPv4)", "http://63.141.230.178:82/gslb/zbdq5.m3u8?id=cctv9hd")
                )
            ),
            Channel(
                id = "cctv10",
                cctvId = "cctv10",
                name = "CCTV-10 科教",
                group = "央视",
                logo = "https://live.fanmingming.com/tv/CCTV10.png",
                sources = mutableListOf(
                    ChannelSource("官方稳定 (IPv4)", "http://74.91.26.218:82/live/cctv10hd.m3u8"),
                    ChannelSource("官方备用 (IPv4)", "http://38.75.136.137:98/gslb/dsdqbv/cctv10hd.m3u8?auth=test20251009")
                )
            ),
            Channel(
                id = "cctv11",
                cctvId = "cctv11",
                name = "CCTV-11 戏曲",
                group = "央视",
                logo = "https://live.fanmingming.com/tv/CCTV11.png",
                sources = mutableListOf(
                    ChannelSource("官方稳定 (IPv4)", "http://74.91.26.218:82/live/cctv11hd.m3u8")
                )
            ),
            Channel(
                id = "cctv12",
                cctvId = "cctv12",
                name = "CCTV-12 社会与法",
                group = "央视",
                logo = "https://live.fanmingming.com/tv/CCTV12.png",
                sources = mutableListOf(
                    ChannelSource("官方稳定 (IPv4)", "http://107.150.60.122/live/cctv12hd.m3u8")
                )
            ),
            Channel(
                id = "cctv13",
                cctvId = "cctv13",
                name = "CCTV-13 新闻",
                group = "央视",
                logo = "https://live.fanmingming.com/tv/CCTV13.png",
                sources = mutableListOf(
                    ChannelSource("官方稳定 (IPv4)", "https://newbndbd.a.bdydns.com/newbnd/necctv13_2/index.m3u8"),
                    ChannelSource("官方备用 (IPv4)", "https://newbndtxy.liveplay.myqcloud.com/newbnd/necctv13_2/index.m3u8")
                )
            ),
            Channel(
                id = "cctvchild",
                cctvId = "cctvchild",
                name = "CCTV-14 少儿",
                group = "央视",
                logo = "https://live.fanmingming.com/tv/CCTV14.png",
                sources = mutableListOf(
                    ChannelSource("官方稳定 (IPv4)", "http://198.204.228.26/live/cctv14hd.m3u8")
                )
            ),
            Channel(
                id = "cctv15",
                cctvId = "cctv15",
                name = "CCTV-15 音乐",
                group = "央视",
                logo = "https://live.fanmingming.com/tv/CCTV15.png",
                sources = mutableListOf(
                    ChannelSource("官方稳定 (IPv4)", "http://204.12.221.218:8181/3m1080p/cctv15.m3u8")
                )
            ),
            Channel(
                id = "cctv16",
                cctvId = "cctv16",
                name = "CCTV-16 奥林匹克",
                group = "央视",
                logo = "https://live.fanmingming.com/tv/CCTV16.png",
                sources = mutableListOf(
                    ChannelSource("官方稳定 (IPv4)", "http://207.56.13.146:81/cdnlive/cctv16.m3u8")
                )
            ),
            Channel(
                id = "cctv17",
                cctvId = "cctv17",
                name = "CCTV-17 农业农村",
                group = "央视",
                logo = "https://live.fanmingming.com/tv/CCTV17.png",
                sources = mutableListOf(
                    ChannelSource("官方稳定 (IPv4)", "http://74.91.26.218:82/live/cctv17hd.m3u8"),
                    ChannelSource("官方备用 (IPv4)", "http://63.141.230.178:82/gslb/zbdq5.m3u8?id=cctv17hd")
                )
            )
        )
    }

    /** 格式化 M3U 线路标签为友好名称（如：线路 1 (IPv4)） */
    private fun formatSourceTag(tag: String, lineIndex: Int): String {
        if (tag.isBlank()) return "线路 ${lineIndex + 1}"
        val sub = Regex("""『(.*?)』""").find(tag)?.groupValues?.get(1)?.trim() ?: ""
        val isIpv6 = tag.contains("IPV6", ignoreCase = true)
        val isIpv4 = tag.contains("IPV4", ignoreCase = true)
        val ipStr = if (isIpv6) "IPv6" else if (isIpv4) "IPv4" else ""

        return when {
            sub.isNotEmpty() && ipStr.isNotEmpty() -> "$sub ($ipStr)"
            sub.isNotEmpty() -> sub
            ipStr.isNotEmpty() -> "线路 ${lineIndex + 1} ($ipStr)"
            else -> "线路 ${lineIndex + 1}"
        }
    }

    /** 解析 M3U 播放列表并将同一频道的多个源聚合为一个 Channel */
    private suspend fun parseM3U(): List<Channel> {
        val currentTime = System.currentTimeMillis()
        if (cachedChannels != null && (currentTime - cacheTime) < cacheExpiry) {
            return cachedChannels!!
        }

        // 以 Map 聚合频道，key 为规范化后的唯一标识
        val channelMap = LinkedHashMap<String, Channel>()

        // 1. 先植入 CCTV 官方持久免加密直播源作为基底
        getOfficialCCTVChannels().forEach { official ->
            channelMap[official.id] = official
        }

        try {
            val m3uContent = app.get(playlistUrl, headers = mapOf("User-Agent" to USER_AGENT), timeout = 8000L).text
            val lines = m3uContent.lines()

            var currentName = ""
            var currentGroup = ""
            var currentLogo = ""

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXTINF:")) {
                    currentName = trimmed.substringAfterLast(",").trim()

                    val groupMatch = Regex("""group-title="([^"]+)"""").find(trimmed)
                    currentGroup = groupMatch?.groupValues?.get(1)?.trim() ?: ""

                    val logoMatch = Regex("""tvg-logo="([^"]+)"""").find(trimmed)
                    currentLogo = logoMatch?.groupValues?.get(1)?.trim() ?: ""
                } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && currentName.isNotEmpty()) {
                    val rawUrl = trimmed
                    val cleanUrl = rawUrl.substringBefore("$").trim()
                    val tag = if (rawUrl.contains("$")) rawUrl.substringAfter("$").trim() else ""

                    if (cleanUrl.isNotBlank()) {
                        // 规范化频道名称
                        val cctvKey = currentName.uppercase().replace(" ", "").replace("-", "")
                        val cctvInfo = CCTV_MAP[cctvKey]

                        val finalName = cctvInfo?.first ?: currentName
                        val cctvId = cctvInfo?.second
                        val key = cctvId ?: finalName

                        val channel = channelMap.getOrPut(key) {
                            val logoUrl = if (cctvInfo != null) {
                                "https://live.fanmingming.com/tv/${cctvKey.replace("PLUS", "+")}.png"
                            } else {
                                currentLogo.ifEmpty { "https://live.fanmingming.com/tv/$finalName.png" }
                            }
                            Channel(
                                id = key,
                                name = finalName,
                                group = if (cctvInfo != null) "央视" else currentGroup,
                                logo = logoUrl,
                                sources = mutableListOf(),
                                cctvId = cctvId
                            )
                        }

                        // 如果该直播流尚未添加，则聚合进当前频道的多线路列表
                        if (channel.sources.none { it.url == cleanUrl }) {
                            val lineName = formatSourceTag(tag, channel.sources.size)
                            channel.sources.add(ChannelSource(lineName, cleanUrl))
                        }
                    }
                    currentName = ""
                }
            }
        } catch (_: Exception) {
            // 在线源获取失败时保留内置官方源
        }

        val result = channelMap.values.toList()
        cachedChannels = result
        cacheTime = currentTime
        return result
    }

    /** 请求央视实时 EPG 节目单 */
    private suspend fun getChannelEpg(cctvId: String): String? {
        val cached = epgCache[cctvId]
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.second) < epgCacheExpiry) {
            return cached.first
        }
        return try {
            val epgUrl = "https://api.cntv.cn/epg/epginfo?c=$cctvId"
            val json = app.get(epgUrl, headers = mapOf("User-Agent" to USER_AGENT), timeout = 1500L).text
            val isLive = JSONObject(json).optJSONObject(cctvId)?.optString("isLive")?.trim()
            if (!isLive.isNullOrEmpty() && isLive != "None") {
                epgCache[cctvId] = Pair(isLive, now)
                isLive
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /** 频道分类过滤与排序 */
    private fun filterChannelsByCategory(channels: List<Channel>, category: String): List<Channel> {
        val filtered = when (category) {
            "cctv" -> channels.filter {
                it.name.startsWith("CCTV") || it.group.contains("央视")
            }.sortedWith { a, b ->
                val idxA = CCTV_ORDER.indexOf(a.name)
                val idxB = CCTV_ORDER.indexOf(b.name)
                when {
                    idxA != -1 && idxB != -1 -> idxA.compareTo(idxB)
                    idxA != -1 -> -1
                    idxB != -1 -> 1
                    else -> a.name.compareTo(b.name)
                }
            }

            "satellite" -> channels.filter {
                (it.name.contains("卫视") || it.group.contains("卫视")) &&
                        !it.name.startsWith("CCTV") && !it.group.contains("央视")
            }

            "4k" -> channels.filter {
                it.name.contains("4K", ignoreCase = true) || it.name.contains("8K", ignoreCase = true) ||
                        it.group.contains("4K", ignoreCase = true) || it.group.contains("8K", ignoreCase = true)
            }

            "gat" -> channels.filter {
                it.group.contains("港澳台") || it.group.contains("香港") ||
                        it.group.contains("澳门") || it.group.contains("台湾")
            }

            "local" -> channels.filter {
                val inGroup = it.group.contains("地方") || it.group.contains("本地") ||
                        PROVINCES.any { p -> it.group.contains(p) }
                inGroup && !it.name.contains("卫视") && !it.group.contains("卫视") &&
                        !it.name.startsWith("CCTV") && !it.group.contains("央视")
            }

            "special" -> channels.filter {
                val isGeneral = listOf("电影", "音乐", "体育", "少儿", "纪录", "新闻", "财经", "购物")
                    .any { tag -> it.group.contains(tag) }
                isGeneral && !it.name.startsWith("CCTV") && !it.name.contains("卫视")
            }.take(80) // 限制特色专区数量以保证主页流畅

            else -> channels
        }
        return filtered
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val allChannels = parseM3U()
        val channels = filterChannelsByCategory(allChannels, request.data)

        // 针对央视频道并发查询 EPG 正在播放节目单（带超时保护）
        val searchResponses = coroutineScope {
            channels.map { channel ->
                async {
                    var displayName = channel.name
                    if (channel.cctvId != null) {
                        val epg = getChannelEpg(channel.cctvId)
                        if (!epg.isNullOrEmpty()) {
                            displayName = "${channel.name} · $epg"
                        }
                    }
                    newMovieSearchResponse(displayName, "channel:${channel.id}", TvType.Live) {
                        this.posterUrl = channel.logo.ifEmpty { DEFAULT_LOGO }
                    }
                }
            }.map { it.await() }
        }

        return newHomePageResponse(
            listOf(HomePageList(request.name, searchResponses, isHorizontalImages = true)),
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val channels = parseM3U()
        return channels.filter {
            it.name.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true)
        }.map { channel ->
            newMovieSearchResponse(channel.name, "channel:${channel.id}", TvType.Live) {
                this.posterUrl = channel.logo.ifEmpty { DEFAULT_LOGO }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val allChannels = parseM3U()
        val channelId = if (url.startsWith("channel:")) url.removePrefix("channel:") else url

        // 匹配对应频道
        val currentChannel = allChannels.find { it.id.equals(channelId, ignoreCase = true) }
            ?: allChannels.find { it.name.equals(channelId, ignoreCase = true) }
            ?: allChannels.find { ch -> ch.sources.any { it.url == url } }
            ?: Channel(
                id = "direct",
                name = "直播频道",
                group = "其他",
                logo = DEFAULT_LOGO,
                sources = mutableListOf(ChannelSource("默认线路", url))
            )

        // 将该频道的多个源组织为多线路（支持在播放器选集及线路随时切换）
        val episodes = currentChannel.sources.mapIndexed { index, source ->
            val episodeData = "${currentChannel.id}__SEP__${index}__SEP__${source.url}"
            newEpisode(episodeData) {
                this.name = source.name.ifEmpty { "线路 ${index + 1}" }
                this.episode = index + 1
                this.posterUrl = currentChannel.logo.ifEmpty { DEFAULT_LOGO }
            }
        }

        val epgInfo = currentChannel.cctvId?.let { getChannelEpg(it) }
        val plotText = buildString {
            append("📺 分组: ${currentChannel.group.ifEmpty { "直播频道" }}")
            if (!epgInfo.isNullOrEmpty()) {
                append("\n🔴 正在播放: $epgInfo")
            }
            append("\n\n⚡ 可用线路: 共 ${currentChannel.sources.size} 条")
            append("\n💡 点击下方线路即可播放；播放过程中亦可在播放器内点击【选集】或【线路】随时换源！")
        }

        return newTvSeriesLoadResponse(currentChannel.name, "channel:${currentChannel.id}", TvType.Live, episodes) {
            this.posterUrl = currentChannel.logo.ifEmpty { DEFAULT_LOGO }
            this.plot = plotText
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val channels = parseM3U()

        // 1. 解析 channelId__SEP__selectedIndex__SEP__selectedUrl
        if (data.contains("__SEP__")) {
            val parts = data.split("__SEP__")
            val channelId = parts.getOrNull(0) ?: ""
            val selectedIndex = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val selectedUrl = parts.getOrNull(2) ?: ""

            val channel = channels.find { it.id.equals(channelId, ignoreCase = true) }
            if (channel != null && channel.sources.isNotEmpty()) {
                // 优先发射当前选中的线路
                val primarySource = channel.sources.getOrNull(selectedIndex)
                    ?: ChannelSource("线路 ${selectedIndex + 1}", selectedUrl)
                emitStreamLink(primarySource.name, primarySource.url, callback)

                // 发射该频道的其余备用线路供播放器源选择列表切换
                channel.sources.forEachIndexed { idx, src ->
                    if (idx != selectedIndex && src.url.isNotBlank()) {
                        emitStreamLink(src.name, src.url, callback)
                    }
                }
                return true
            }
        }

        // 2. 尝试从全量频道中反向匹配包含此 URL 的频道
        val matchedChannel = channels.find { ch -> ch.sources.any { it.url == data } }
        if (matchedChannel != null) {
            val selectedSrc = matchedChannel.sources.find { it.url == data }
            if (selectedSrc != null) {
                emitStreamLink(selectedSrc.name, selectedSrc.url, callback)
            }
            matchedChannel.sources.forEach { src ->
                if (src.url != data && src.url.isNotBlank()) {
                    emitStreamLink(src.name, src.url, callback)
                }
            }
            return true
        }

        // 3. Fallback: 直接发射传入的播放流
        if (data.isNotBlank()) {
            emitStreamLink("默认线路", data, callback)
        }

        return true
    }

    /** 发射直播流 Link（优先使用 M3u8Helper 解析多码率，失败时降级直连） */
    private fun emitStreamLink(sourceName: String, url: String, callback: (ExtractorLink) -> Unit) {
        if (url.isBlank()) return
        val cleanUrl = url.substringBefore("$").trim()
        val displayName = "$name · $sourceName"

        if (cleanUrl.contains(".m3u8")) {
            try {
                M3u8Helper.generateM3u8(
                    source = name,
                    streamUrl = cleanUrl,
                    referer = mainUrl,
                    headers = mapOf("User-Agent" to USER_AGENT),
                    name = displayName
                ).forEach(callback)
            } catch (_: Exception) {
                callback(
                    newExtractorLink(
                        source = name,
                        name = displayName,
                        url = cleanUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = mainUrl
                        this.headers = mapOf("User-Agent" to USER_AGENT)
                    }
                )
            }
        } else {
            callback(
                newExtractorLink(
                    source = name,
                    name = displayName,
                    url = cleanUrl,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = mainUrl
                    this.headers = mapOf("User-Agent" to USER_AGENT)
                }
            )
        }
    }
}
