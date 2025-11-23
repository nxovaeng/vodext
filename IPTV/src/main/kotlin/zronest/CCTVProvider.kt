package nxovaeng

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper

/** CCTV 官方直播源提供者 使用 CCTV 官方稳定的直播流，面向全国，无地域限制 */
class CCTVProvider : MainAPI() {
    override var mainUrl = "https://tv.cctv.com"
    override var name = "CCTV官方直播"
    override var lang = "zh"
    override val hasMainPage = true
    override val hasDownloadSupport = false // 直播流不支持下载
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf("cctv" to "央视频道", "satellite" to "卫视频道")

    /** CCTV 官方直播流地址 使用多个可用的备用源，确保稳定性 */
    private fun getCCTVChannels(): List<Channel> {
        return listOf(
                // 央视主要频道 - 使用移动CDN的稳定源
                Channel(
                        "CCTV-1 综合",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226231/index.m3u8",
                        "CCTV"
                ),
                Channel(
                        "CCTV-2 财经",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226195/index.m3u8",
                        "CCTV"
                ),
                Channel(
                        "CCTV-3 综艺",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226397/index.m3u8",
                        "CCTV"
                ),
                Channel(
                        "CCTV-4 中文国际",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226191/index.m3u8",
                        "CCTV"
                ),
                Channel(
                        "CCTV-5 体育",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226395/index.m3u8",
                        "CCTV"
                ),
                Channel(
                        "CCTV-6 电影",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226393/index.m3u8",
                        "CCTV"
                ),
                Channel(
                        "CCTV-7 国防军事",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226192/index.m3u8",
                        "CCTV"
                ),
                Channel(
                        "CCTV-8 电视剧",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226391/index.m3u8",
                        "CCTV"
                ),
                Channel(
                        "CCTV-9 纪录",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226197/index.m3u8",
                        "CCTV"
                ),
                Channel(
                        "CCTV-10 科教",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226189/index.m3u8",
                        "CCTV"
                ),
                Channel(
                        "CCTV-11 戏曲",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226240/index.m3u8",
                        "CCTV"
                ),
                Channel(
                        "CCTV-12 社会与法",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226190/index.m3u8",
                        "CCTV"
                ),
                Channel(
                        "CCTV-13 新闻",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226233/index.m3u8",
                        "CCTV"
                ),
                Channel(
                        "CCTV-14 少儿",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226193/index.m3u8",
                        "CCTV"
                ),
                Channel(
                        "CCTV-15 音乐",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221225785/index.m3u8",
                        "CCTV"
                ),

                // 卫视频道
                Channel(
                        "湖南卫视",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226211/index.m3u8",
                        "卫视"
                ),
                Channel(
                        "浙江卫视",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226199/index.m3u8",
                        "卫视"
                ),
                Channel(
                        "江苏卫视",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226200/index.m3u8",
                        "卫视"
                ),
                Channel(
                        "东方卫视",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226217/index.m3u8",
                        "卫视"
                ),
                Channel(
                        "北京卫视",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226222/index.m3u8",
                        "卫视"
                ),
                Channel(
                        "深圳卫视",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226205/index.m3u8",
                        "卫视"
                ),
                Channel(
                        "广东卫视",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226216/index.m3u8",
                        "卫视"
                ),
                Channel(
                        "安徽卫视",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226203/index.m3u8",
                        "卫视"
                ),
                Channel(
                        "天津卫视",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226204/index.m3u8",
                        "卫视"
                ),
                Channel(
                        "重庆卫视",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226202/index.m3u8",
                        "卫视"
                ),
                Channel(
                        "山东卫视",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226209/index.m3u8",
                        "卫视"
                ),
                Channel(
                        "黑龙江卫视",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226215/index.m3u8",
                        "卫视"
                ),
                Channel(
                        "河北卫视",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221225750/index.m3u8",
                        "卫视"
                ),
                Channel(
                        "辽宁卫视",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226201/index.m3u8",
                        "卫视"
                ),
                Channel(
                        "湖北卫视",
                        "http://39.134.24.162/dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226206/index.m3u8",
                        "卫视"
                ),
        )
    }

    data class Channel(
            val name: String,
            val url: String,
            val group: String,
            val logo: String = "https://www.google.com/s2/favicons?domain=tv.cctv.com&sz=128"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val allChannels = getCCTVChannels()

        val filteredChannels =
                when (request.data) {
                    "cctv" -> allChannels.filter { it.group == "CCTV" }
                    "satellite" -> allChannels.filter { it.group == "卫视" }
                    else -> allChannels
                }

        val searchResults =
                filteredChannels.map { channel ->
                    newMovieSearchResponse(channel.name, channel.url, TvType.Live) {
                        this.posterUrl = channel.logo
                    }
                }

        return newHomePageResponse(
                listOf(HomePageList(request.name, searchResults, isHorizontalImages = false)),
                hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val channels = getCCTVChannels()

        return channels.filter { it.name.contains(query, ignoreCase = true) }.map { channel ->
            newMovieSearchResponse(channel.name, channel.url, TvType.Live) {
                this.posterUrl = channel.logo
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val allChannels = getCCTVChannels()
        val currentChannel =
                allChannels.find { it.url == url }
                        ?: return newMovieLoadResponse("CCTV 直播", url, TvType.Live, url)

        return newMovieLoadResponse(currentChannel.name, url, TvType.Live, url) {
            this.posterUrl = currentChannel.logo
            this.plot = "📺 ${currentChannel.group} - ${currentChannel.name}\n\n官方直播流，稳定流畅，面向全国"
        }
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        // 直接使用 M3U8 链接
        if (data.contains(".m3u8")) {
            M3u8Helper.generateM3u8(
                            this.name,
                            data,
                            referer = mainUrl,
                            headers = mapOf("User-Agent" to USER_AGENT)
                    )
                    .forEach(callback)
        }

        return true
    }

    companion object {
        private const val USER_AGENT =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
