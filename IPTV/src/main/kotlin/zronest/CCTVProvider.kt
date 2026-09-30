package nxovaeng

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper

/** CCTV 官方直播源提供者 采用央视官方纯净免加密直播流 + 官方动态 EPG 节目单 */
class CCTVProvider : MainAPI() {
    // 插件基础信息
    override var mainUrl = "https://tv.cctv.com"
    override var name = "CCTV Live"
    override var lang = "zh"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf("cctv" to "央视频道")

    data class Channel(
        val id: String,
        val name: String,
        val url: String,
        val backupUrls: List<String> = emptyList(),
        val logo: String = "https://p1.img.cctvpic.com/photoAlbum/templet/common/DEPA1553653185997107/cctv_logo.png"
    )

    /** 央视频道稳定纯净源列表 (免 DRM 加密，持久稳定播放，不闪屏不闪退) */
    private fun getCCTVChannels(): List<Channel> {
        return listOf(
            Channel(
                id = "cctv1",
                name = "CCTV-1 综合",
                url = "https://newbndbd.a.bdydns.com/newbnd/necctv1_2/index.m3u8",
                backupUrls = listOf("https://newbndtxy.liveplay.myqcloud.com/newbnd/necctv1_2/index.m3u8"),
                logo = "https://live.fanmingming.com/tv/CCTV1.png"
            ),
            Channel(
                id = "cctv2",
                name = "CCTV-2 财经",
                url = "http://204.12.221.218:8181/3m1080p/cctv2.m3u8",
                backupUrls = listOf("http://74.91.26.218:82/live/cctv2hd.m3u8"),
                logo = "https://live.fanmingming.com/tv/CCTV2.png"
            ),
            Channel(
                id = "cctv3",
                name = "CCTV-3 综艺",
                url = "http://107.150.60.122/live/cctv3hd.m3u8",
                backupUrls = listOf("http://63.141.230.178:82/gslb/zbdq5.m3u8?id=cctv3hd"),
                logo = "https://live.fanmingming.com/tv/CCTV3.png"
            ),
            Channel(
                id = "cctv4",
                name = "CCTV-4 中文国际",
                url = "http://74.91.26.218:82/live/cctv4hd.m3u8",
                logo = "https://live.fanmingming.com/tv/CCTV4.png"
            ),
            Channel(
                id = "cctv5",
                name = "CCTV-5 体育",
                url = "http://107.150.60.122/live/cctv5hd.m3u8",
                logo = "https://live.fanmingming.com/tv/CCTV5.png"
            ),
            Channel(
                id = "cctv5plus",
                name = "CCTV-5+ 赛事",
                url = "http://120.76.248.139/live/bfgd/4200000246.m3u8",
                backupUrls = listOf("http://120.76.248.139/live/bfgd/4200000064.m3u8"),
                logo = "https://live.fanmingming.com/tv/CCTV5+.png"
            ),
            Channel(
                id = "cctv6",
                name = "CCTV-6 电影",
                url = "http://69.30.245.50/live/cctv6.m3u8",
                logo = "https://live.fanmingming.com/tv/CCTV6.png"
            ),
            Channel(
                id = "cctv7",
                name = "CCTV-7 国防军事",
                url = "http://74.91.26.218:82/live/cctv7hd.m3u8",
                logo = "https://live.fanmingming.com/tv/CCTV7.png"
            ),
            Channel(
                id = "cctv8",
                name = "CCTV-8 电视剧",
                url = "http://bztv.tvbus.cc:8081/cdnlive/cctv8.m3u8",
                backupUrls = listOf("http://204.12.221.218:8181/3m1080p/cctv8.m3u8"),
                logo = "https://live.fanmingming.com/tv/CCTV8.png"
            ),
            Channel(
                id = "cctvjilu",
                name = "CCTV-9 纪录",
                url = "http://63.141.230.178:82/gslb/zbdq5.m3u8?id=cctv9hd",
                logo = "https://live.fanmingming.com/tv/CCTV9.png"
            ),
            Channel(
                id = "cctv10",
                name = "CCTV-10 科教",
                url = "http://74.91.26.218:82/live/cctv10hd.m3u8",
                backupUrls = listOf("http://38.75.136.137:98/gslb/dsdqbv/cctv10hd.m3u8?auth=test20251009"),
                logo = "https://live.fanmingming.com/tv/CCTV10.png"
            ),
            Channel(
                id = "cctv11",
                name = "CCTV-11 戏曲",
                url = "http://74.91.26.218:82/live/cctv11hd.m3u8",
                logo = "https://live.fanmingming.com/tv/CCTV11.png"
            ),
            Channel(
                id = "cctv12",
                name = "CCTV-12 社会与法",
                url = "http://107.150.60.122/live/cctv12hd.m3u8",
                logo = "https://live.fanmingming.com/tv/CCTV12.png"
            ),
            Channel(
                id = "cctv13",
                name = "CCTV-13 新闻",
                url = "https://newbndbd.a.bdydns.com/newbnd/necctv13_2/index.m3u8",
                backupUrls = listOf("https://newbndtxy.liveplay.myqcloud.com/newbnd/necctv13_2/index.m3u8"),
                logo = "https://live.fanmingming.com/tv/CCTV13.png"
            ),
            Channel(
                id = "cctvchild",
                name = "CCTV-14 少儿",
                url = "http://198.204.228.26/live/cctv14hd.m3u8",
                logo = "https://live.fanmingming.com/tv/CCTV14.png"
            ),
            Channel(
                id = "cctv15",
                name = "CCTV-15 音乐",
                url = "http://204.12.221.218:8181/3m1080p/cctv15.m3u8",
                logo = "https://live.fanmingming.com/tv/CCTV15.png"
            ),
            Channel(
                id = "cctv16",
                name = "CCTV-16 奥林匹克",
                url = "http://207.56.13.146:81/cdnlive/cctv16.m3u8",
                logo = "https://live.fanmingming.com/tv/CCTV16.png"
            ),
            Channel(
                id = "cctv17",
                name = "CCTV-17 农业农村",
                url = "http://74.91.26.218:82/live/cctv17hd.m3u8",
                backupUrls = listOf("http://63.141.230.178:82/gslb/zbdq5.m3u8?id=cctv17hd"),
                logo = "https://live.fanmingming.com/tv/CCTV17.png"
            )
        )
    }

    /** 1. 动态获取主页频道列表，并结合官方 EPG 显示正在播放的节目 */
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val channels = getCCTVChannels()

        val searchResponses = channels.map { channel ->
            var displayName = channel.name
            try {
                // 请求央视官方实时 EPG
                val epgUrl = "https://api.cntv.cn/epg/epginfo?c=${channel.id}"
                val json = app.get(epgUrl, headers = mapOf("User-Agent" to USER_AGENT), timeout = 2500L).text
                val isLiveMatch = Regex(""""isLive"\s*:\s*"([^"]+)"""").find(json)
                val isLive = isLiveMatch?.groupValues?.get(1)?.trim()
                if (!isLive.isNullOrEmpty() && isLive != "None") {
                    displayName = "${channel.name} · $isLive"
                }
            } catch (_: Exception) {
                // 超时或失败时保留原频道名
            }

            newMovieSearchResponse(displayName, channel.url, TvType.Live) {
                this.posterUrl = channel.logo
            }
        }

        return newHomePageResponse(
            list = HomePageList(
                name = "央视频道",
                list = searchResponses,
                isHorizontalImages = true
            ),
            hasNext = false
        )
    }

    /** 2. 点击频道直接返回播放响应 (彻底移除 WebView 嗅探) */
    override suspend fun load(url: String): LoadResponse? {
        val channel = getCCTVChannels().find { it.url == url }
            ?: Channel("cctv", "CCTV", url)

        return newMovieLoadResponse(
            name = channel.name,
            url = channel.url,
            type = TvType.Live,
            dataUrl = channel.url
        ) {
            this.posterUrl = channel.logo
        }
    }

    /** 3. 加载播放流 (支持主线路与多条备用线路) */
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // 主线路
        if (data.contains(".m3u8")) {
            M3u8Helper.generateM3u8(
                source = this.name,
                streamUrl = data,
                referer = mainUrl,
                headers = mapOf("User-Agent" to USER_AGENT)
            ).forEach(callback)
        }

        // 备用线路
        val channel = getCCTVChannels().find { it.url == data }
        channel?.backupUrls?.forEachIndexed { index, backupUrl ->
            if (backupUrl.contains(".m3u8")) {
                M3u8Helper.generateM3u8(
                    source = "${this.name} 备用${index + 1}",
                    streamUrl = backupUrl,
                    referer = mainUrl,
                    headers = mapOf("User-Agent" to USER_AGENT)
                ).forEach(callback)
            }
        }

        return true
    }

    /** 4. 频道搜索功能 */
    override suspend fun search(query: String): List<SearchResponse> {
        val channels = getCCTVChannels()
        return channels.filter {
            it.name.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true)
        }.map { channel ->
            newMovieSearchResponse(channel.name, channel.url, TvType.Live) {
                this.posterUrl = channel.logo
            }
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
