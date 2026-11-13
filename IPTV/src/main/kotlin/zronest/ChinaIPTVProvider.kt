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
 * 中国IPTV直播源提供者
 * 点击频道直接全屏播放（Movie/Live 模式，杜绝跳转剧集列表中间页）；
 * 同一频道内聚合多条可用线路，可在播放器内自由切换；整合 CCTV 官方稳定流与实时 EPG。
 */
open class ChinaIPTVProvider : MainAPI() {
    override var mainUrl = "https://clun.top"
    override var name = "中国IPTV"
    override var lang = "zh"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live, TvType.Movie, TvType.TvSeries)

    private val playlistUrl = "$mainUrl/lib/iptv.txt"

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
    ) {
        val primaryUrl: String get() = sources.firstOrNull()?.url ?: ""
    }

    private var cachedChannels: List<Channel>? = null
    private var cacheTime: Long = 0
    private val cacheExpiry = 3600000L // 1小时缓存

    // CNTV 实时节目单缓存 (10分钟)
    private val epgCache = mutableMapOf<String, Pair<String, Long>>()
    private val epgCacheExpiry = 600000L

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

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
            "CCTV4K" to ("CCTV-4K 超高清" to "cctv4k"),
            "CCTV8K" to ("CCTV-8K 超高清" to "cctv8k")
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

    /** 内置高稳定性备用基础频道（防止在线列表临时不可用时无频道可播） */
    private fun getBuiltinChannels(): List<Channel> {
        return listOf(
            // 4K 基础频道
            Channel(
                id = "东方卫视4K",
                name = "东方卫视4K",
                group = "4K",
                logo = "https://live.fanmingming.com/tv/东方卫视.png",
                sources = mutableListOf(
                    ChannelSource("线路 1 (IPv4)", "https://bp-resource-dfl.bestv.cn/148/3/video.m3u8")
                )
            ),
            Channel(
                id = "cctv4k",
                name = "CCTV-4K 超高清",
                group = "4K",
                logo = "https://live.fanmingming.com/tv/CCTV4K.png",
                sources = mutableListOf(
                    ChannelSource("官方高清", "https://live-play-hls.cctvnews.cctv.com/CCTVChannel/channel_cctv4k_mbd.m3u8?auth_key=1794554100-1-b59134a2ead43d7f783f943c8f535e7bb80c5169575b2e8efcd5b3ddea21b41c-74cc7f4667fb67e1664b21b6f685b5dc&yid=b59134a2ead43d7f783f943c8f535e7bb80c5169575b2e8efcd5b3ddea21b41c"),
                    ChannelSource("线路 1 (IPv4)", "http://198.204.240.250:82/live/cctv4k.m3u8")
                ),
                cctvId = "cctv4k"
            ),
            // CCTV 1-17 官方持久免加密直播源
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
            ),
            // 卫视基础频道
            Channel(
                id = "湖南卫视",
                name = "湖南卫视",
                group = "卫视",
                logo = "https://live.fanmingming.com/tv/湖南卫视.png",
                sources = mutableListOf(
                    ChannelSource("线路 1 (IPv4)", "http://120.40.39.246:352/newlive/live/hls/15/live.m3u8")
                )
            ),
            Channel(
                id = "浙江卫视",
                name = "浙江卫视",
                group = "卫视",
                logo = "https://live.fanmingming.com/tv/浙江卫视.png",
                sources = mutableListOf(
                    ChannelSource("线路 1 (IPv4)", "http://120.40.39.246:352/newlive/live/hls/16/live.m3u8")
                )
            )
        )
    }

    /** 格式化 M3U 线路标签为友好名称 */
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

    /** 解析播放列表（支持 txt 和 m3u 两种格式自适应）并将同一频道的多个源聚合为一个 Channel */
    private suspend fun parseChannels(): List<Channel> {
        val currentTime = System.currentTimeMillis()
        if (cachedChannels != null && (currentTime - cacheTime) < cacheExpiry) {
            return cachedChannels!!
        }

        val channelMap = LinkedHashMap<String, Channel>()

        // 1. 预先植入官方/稳定基础源作为基底
        getBuiltinChannels().forEach { official ->
            channelMap[official.id] = official
        }

        try {
            val content = app.get(playlistUrl, headers = mapOf("User-Agent" to USER_AGENT), timeout = 8000L).text
            if (content.contains("#EXTM3U")) {
                parseM3UContent(content, channelMap)
            } else {
                parseTxtContent(content, channelMap)
            }
        } catch (_: Exception) {
            // 在线源获取失败时平滑降级使用内置源
        }

        val result = channelMap.values.toList()
        cachedChannels = result
        cacheTime = currentTime
        return result
    }

    private suspend fun parseM3U(): List<Channel> = parseChannels()

    /** 解析 TVBox txt 格式直播源（第一列为频道名或 #genre#，第二列以 $ 分割真实流与线路标识） */
    private fun parseTxtContent(content: String, channelMap: LinkedHashMap<String, Channel>) {
        var currentGroup = "其他"

        for (line in content.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.contains("#genre#")) {
                currentGroup = trimmed.substringBefore(",").trim().ifEmpty { "其他" }
                continue
            }

            val commaIndex = trimmed.indexOf(',')
            if (commaIndex <= 0) continue

            val channelName = trimmed.substring(0, commaIndex).trim()
            val secondCol = trimmed.substring(commaIndex + 1).trim()
            if (channelName.isEmpty() || secondCol.isEmpty()) continue

            // 第二列在 "$" 分割：左侧为流地址（完整保留 ?auth_key 等认证参数），右侧为线路标签
            val cleanUrl = secondCol.substringBefore("$").trim()
            val tag = if (secondCol.contains("$")) secondCol.substringAfter("$").trim() else ""

            if (cleanUrl.isNotBlank() && cleanUrl.startsWith("http")) {
                val cctvKey = channelName.uppercase().replace(" ", "").replace("-", "")
                val cctvInfo = CCTV_MAP[cctvKey]

                val finalName = cctvInfo?.first ?: channelName
                val cctvId = cctvInfo?.second
                val key = cctvId ?: finalName

                val channel = channelMap.getOrPut(key) {
                    val logoUrl = when {
                        cctvInfo != null -> "https://live.fanmingming.com/tv/${cctvKey.replace("PLUS", "+")}.png"
                        else -> "https://live.fanmingming.com/tv/$finalName.png"
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

                // 聚合线路（去重）
                if (channel.sources.none { it.url == cleanUrl }) {
                    val lineName = formatSourceTag(tag, channel.sources.size)
                    channel.sources.add(ChannelSource(lineName, cleanUrl))
                }
            }
        }
    }

    /** 解析标准 M3U 播放列表格式 */
    private fun parseM3UContent(content: String, channelMap: LinkedHashMap<String, Channel>) {
        val lines = content.lines()
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
                // 剥离 $ 后的标签获得纯净直播流 URL（完整保留 ?auth_key 等认证参数）
                val cleanUrl = rawUrl.substringBefore("$").trim()
                val tag = if (rawUrl.contains("$")) rawUrl.substringAfter("$").trim() else ""

                if (cleanUrl.isNotBlank() && cleanUrl.startsWith("http")) {
                    val cctvKey = currentName.uppercase().replace(" ", "").replace("-", "")
                    val cctvInfo = CCTV_MAP[cctvKey]

                    val finalName = cctvInfo?.first ?: currentName
                    val cctvId = cctvInfo?.second
                    val key = cctvId ?: finalName

                    val channel = channelMap.getOrPut(key) {
                        val logoUrl = when {
                            cctvInfo != null -> "https://live.fanmingming.com/tv/${cctvKey.replace("PLUS", "+")}.png"
                            currentLogo.isNotBlank() -> currentLogo
                            else -> "https://live.fanmingming.com/tv/$finalName.png"
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

                    if (channel.sources.none { it.url == cleanUrl }) {
                        val lineName = formatSourceTag(tag, channel.sources.size)
                        channel.sources.add(ChannelSource(lineName, cleanUrl))
                    }
                }
                currentName = ""
            }
        }
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
            val json = app.get(epgUrl, headers = mapOf("User-Agent" to USER_AGENT), timeout = 1000L).text
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
            }.take(80)

            else -> channels
        }
        return filtered
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val allChannels = parseM3U()
        val channels = filterChannelsByCategory(allChannels, request.data)

        // 频道卡片以真实直播流地址为 url，点击直接调用 load 极速起播
        val searchResponses = coroutineScope {
            channels.mapNotNull { channel ->
                val streamUrl = channel.primaryUrl
                if (streamUrl.isBlank()) return@mapNotNull null

                async {
                    var displayName = channel.name
                    // 仅对主 CCTV 频道快速拉取 EPG
                    if (channel.cctvId != null && CCTV_ORDER.contains(channel.name)) {
                        val epg = getChannelEpg(channel.cctvId)
                        if (!epg.isNullOrEmpty()) {
                            displayName = "${channel.name} · $epg"
                        }
                    }
                    newMovieSearchResponse(displayName, streamUrl, TvType.Live) {
                        this.posterUrl = channel.logo
                    }
                }
            }.map { it.await() }
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = searchResponses,
                isHorizontalImages = true
            ),
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val channels = parseM3U()
        return channels.filter {
            it.name.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true)
        }.mapNotNull { channel ->
            val streamUrl = channel.primaryUrl
            if (streamUrl.isBlank()) return@mapNotNull null
            newMovieSearchResponse(channel.name, streamUrl, TvType.Live) {
                this.posterUrl = channel.logo
            }
        }
    }

    /** 点击频道直接返回 Movie/Live 播放响应，直连播放器，绝不跳转“剧集列表”中间页 */
    override suspend fun load(url: String): LoadResponse {
        val cleanUrl = url.substringBefore("$").trim()
        val allChannels = parseM3U()

        // 优先在频道全部线路中查找匹配，其次按名称或 ID 匹配，兼容历史缓存的 channel:xxx 格式
        val currentChannel = allChannels.firstOrNull { ch ->
            ch.sources.any { it.url.substringBefore("$").trim() == cleanUrl } ||
            (cleanUrl.contains("channel:") && ch.id.equals(cleanUrl.substringAfter("channel:").trim(), ignoreCase = true)) ||
            ch.id.equals(cleanUrl, ignoreCase = true) ||
            ch.name.equals(cleanUrl, ignoreCase = true)
        } ?: Channel(
            id = cleanUrl,
            name = "直播频道",
            group = "其他",
            logo = "https://live.fanmingming.com/tv/CCTV1.png",
            sources = mutableListOf(ChannelSource("默认线路", cleanUrl))
        )

        val epgInfo = currentChannel.cctvId?.let { getChannelEpg(it) }
        val plotText = buildString {
            append("📺 频道: ${currentChannel.name}")
            if (!epgInfo.isNullOrEmpty()) {
                append("\n🔴 正在播放: $epgInfo")
            }
            append("\n⚡ 可用线路: 共 ${currentChannel.sources.size} 条")
            append("\n💡 播放器内点击【线路】可随时切换备用线路。")
        }

        val playUrl = currentChannel.primaryUrl.ifEmpty { cleanUrl }

        return newMovieLoadResponse(
            name = currentChannel.name,
            url = playUrl,
            type = TvType.Live,
            dataUrl = playUrl
        ) {
            this.posterUrl = currentChannel.logo
            this.plot = plotText
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // 提取纯净 URL，去除任何额外后缀
        val cleanUrl = if (data.contains("__SEP__")) {
            data.substringAfterLast("__SEP__").substringBefore("$").trim()
        } else {
            data.substringBefore("$").trim()
        }

        if (cleanUrl.isBlank()) return false

        val allChannels = parseM3U()
        val currentChannel = allChannels.firstOrNull { ch ->
            ch.sources.any { it.url.substringBefore("$").trim() == cleanUrl } ||
            (cleanUrl.contains("channel:") && ch.id.equals(cleanUrl.substringAfter("channel:").trim(), ignoreCase = true)) ||
            ch.id.equals(cleanUrl, ignoreCase = true) ||
            ch.name.equals(cleanUrl, ignoreCase = true)
        }

        val targetUrl = if (cleanUrl.startsWith("http") && !cleanUrl.contains("channel:")) {
            cleanUrl
        } else {
            currentChannel?.primaryUrl ?: cleanUrl
        }

        if (targetUrl.isBlank() || !targetUrl.startsWith("http")) return false

        // 1. 发射当前选中主线路
        val currentSourceName = currentChannel?.sources?.find {
            it.url.substringBefore("$").trim() == targetUrl
        }?.name ?: "默认线路"

        emitStreamLink(currentSourceName, targetUrl, callback)

        // 2. 从内存已缓存的频道列表中提取同频道的其余备用线路（免网络开销，直接以 ExtractorLink 注入播放器线路菜单）
        currentChannel?.sources?.forEach { src ->
            val otherUrl = src.url.substringBefore("$").trim()
            if (otherUrl != targetUrl && otherUrl.isNotBlank() && otherUrl.startsWith("http")) {
                try {
                    callback(
                        newExtractorLink(
                            source = this.name,
                            name = "${this.name} · ${src.name}",
                            url = otherUrl,
                            type = if (otherUrl.contains(".m3u8", ignoreCase = true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.referer = ""
                        }
                    )
                } catch (_: Exception) {
                }
            }
        }

        return true
    }

    /** 发射直播流 Link（优先 M3u8Helper 解析，若无多码率或解析失败则保底以 ExtractorLink 直连发射） */
    private suspend fun emitStreamLink(sourceName: String, url: String, callback: (ExtractorLink) -> Unit) {
        if (url.isBlank()) return
        val cleanUrl = url.substringBefore("$").trim()
        val isM3u8 = cleanUrl.contains(".m3u8", ignoreCase = true)
        val linkName = "${this.name} · $sourceName"

        var emitted = false
        if (isM3u8) {
            try {
                val links = M3u8Helper.generateM3u8(
                    source = name,
                    streamUrl = cleanUrl,
                    referer = "",
                    name = linkName
                )
                if (links.isNotEmpty()) {
                    links.forEach(callback)
                    emitted = true
                }
            } catch (_: Exception) {
            }
        }

        if (!emitted) {
            try {
                callback(
                    newExtractorLink(
                        source = name,
                        name = linkName,
                        url = cleanUrl,
                        type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = ""
                    }
                )
            } catch (_: Exception) {
            }
        }
    }
}
