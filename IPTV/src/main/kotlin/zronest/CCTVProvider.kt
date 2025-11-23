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

    /** CCTV 官方直播流地址 来源：CCTV 官网和公开的稳定源 */
    private fun getCCTVChannels(): List<Channel> {
        return listOf(
                // 央视主要频道 - 使用官方 CDN
                Channel(
                        "CCTV-1 综合",
                        "https://live.cctv.cn/m/view/index.shtml?channelid=CHANNEL100001",
                        "CCTV",
                        "https://p1.img.cctvpic.com/photoAlbum/vms/standard/img/2022/10/12/VIDEBj6DvHhXW0SfTyf9Fqjp221012.png"
                ),
                Channel(
                        "CCTV-2 财经",
                        "https://live.cctv.cn/m/view/index.shtml?channelid=CHANNEL100002",
                        "CCTV",
                        "https://p1.img.cctvpic.com/photoAlbum/vms/standard/img/2022/10/12/VIDEquL7rO6XSvzBNFWf8wr1221012.png"
                ),
                Channel(
                        "CCTV-3 综艺",
                        "https://live.cctv.cn/m/view/index.shtml?channelid=CHANNEL100003",
                        "CCTV",
                        "https://p1.img.cctvpic.com/photoAlbum/vms/standard/img/2022/10/12/VIDELLfKSGaMPvF5NDKLxv6P221012.png"
                ),
                Channel(
                        "CCTV-4 中文国际",
                        "https://live.cctv.cn/m/view/index.shtml?channelid=CHANNEL100004",
                        "CCTV",
                        "https://p1.img.cctvpic.com/photoAlbum/vms/standard/img/2022/10/12/VIDEhdS9zBCfvGbOQKBQUJmz221012.png"
                ),
                Channel(
                        "CCTV-5 体育",
                        "https://live.cctv.cn/m/view/index.shtml?channelid=CHANNEL100005",
                        "CCTV",
                        "https://p1.img.cctvpic.com/photoAlbum/vms/standard/img/2022/10/12/VIDEfVbBs9o9fJnYqvF8yx1O221012.png"
                ),
                Channel(
                        "CCTV-5+ 体育赛事",
                        "https://live.cctv.cn/m/view/index.shtml?channelid=CHANNEL100016",
                        "CCTV",
                        "https://p1.img.cctvpic.com/photoAlbum/vms/standard/img/2022/10/12/VIDE4Ky36h3I0qLnSa4BVLYd221012.png"
                ),
                Channel(
                        "CCTV-6 电影",
                        "https://live.cctv.cn/m/view/index.shtml?channelid=CHANNEL100006",
                        "CCTV",
                        "https://p1.img.cctvpic.com/photoAlbum/vms/standard/img/2022/10/12/VIDEiKl6EjuzkEcmZS9QR7Tg221012.png"
                ),
                Channel(
                        "CCTV-7 国防军事",
                        "https://live.cctv.cn/m/view/index.shtml?channelid=CHANNEL100007",
                        "CCTV",
                        "https://p1.img.cctvpic.com/photoAlbum/vms/standard/img/2022/10/12/VIDEWJf6NRHKxW0B8l5DVzqE221012.png"
                ),
                Channel(
                        "CCTV-8 电视剧",
                        "https://live.cctv.cn/m/view/index.shtml?channelid=CHANNEL100008",
                        "CCTV",
                        "https://p1.img.cctvpic.com/photoAlbum/vms/standard/img/2022/10/12/VIDEl9yXz3Kt5ZPi60HJQPfY221012.png"
                ),
                Channel(
                        "CCTV-9 纪录",
                        "https://live.cctv.cn/m/view/index.shtml?channelid=CHANNEL100009",
                        "CCTV",
                        "https://p1.img.cctvpic.com/photoAlbum/vms/standard/img/2022/10/12/VIDEvIo3i49ORiOdmjsEoTEg221012.png"
                ),
                Channel(
                        "CCTV-10 科教",
                        "https://live.cctv.cn/m/view/index.shtml?channelid=CHANNEL100010",
                        "CCTV",
                        "https://p1.img.cctvpic.com/photoAlbum/vms/standard/img/2022/10/12/VIDE2tqdnXZv3gPXqVEYwXsX221012.png"
                ),
                Channel(
                        "CCTV-11 戏曲",
                        "https://live.cctv.cn/m/view/index.shtml?channelid=CHANNEL100011",
                        "CCTV",
                        "https://p1.img.cctvpic.com/photoAlbum/vms/standard/img/2022/10/12/VIDEo8VD4LVe4JqEPrE6xdZE221012.png"
                ),
                Channel(
                        "CCTV-12 社会与法",
                        "https://live.cctv.cn/m/view/index.shtml?channelid=CHANNEL100012",
                        "CCTV",
                        "https://p1.img.cctvpic.com/photoAlbum/vms/standard/img/2022/10/12/VIDEOZ11z18nH6YpgN7HLV4c221012.png"
                ),
                Channel(
                        "CCTV-13 新闻",
                        "https://live.cctv.cn/m/view/index.shtml?channelid=CHANNEL100013",
                        "CCTV",
                        "https://p1.img.cctvpic.com/photoAlbum/vms/standard/img/2022/10/12/VIDEQKWwTR3xC2MmsFATJUIB221012.png"
                ),
                Channel(
                        "CCTV-14 少儿",
                        "https://live.cctv.cn/m/view/index.shtml?channelid=CHANNEL100014",
                        "CCTV",
                        "https://p1.img.cctvpic.com/photoAlbum/vms/standard/img/2022/10/12/VIDEfhv0VhpuYRQDPJlmCRid221012.png"
                ),
                Channel(
                        "CCTV-15 音乐",
                        "https://live.cctv.cn/m/view/index.shtml?channelid=CHANNEL100015",
                        "CCTV",
                        "https://p1.img.cctvpic.com/photoAlbum/vms/standard/img/2022/10/12/VIDEQuwpQgcSYUcOmONp8kWr221012.png"
                ),
                Channel(
                        "CCTV-16 奥林匹克",
                        "https://live.cctv.cn/m/view/index.shtml?channelid=CHANNEL100018",
                        "CCTV",
                        "https://p1.img.cctvpic.com/photoAlbum/vms/standard/img/2022/10/12/VIDEphhtEEMNwsDCdO5WRmqN221012.png"
                ),
                Channel(
                        "CCTV-17 农业农村",
                        "https://live.cctv.cn/m/view/index.shtml?channelid=CHANNEL100017",
                        "CCTV",
                        "https://p1.img.cctvpic.com/photoAlbum/vms/standard/img/2022/10/12/VIDElZvnmO2fofT2NpOSNZVg221012.png"
                ),

                // 4K 超高清
                Channel(
                        "CCTV-4K 超高清",
                        "http://27.222.3.214/liveali-tp4k.cctv.cn/live/4K10M.stream/1.m3u8",
                        "CCTV",
                        "https://p1.img.cctvpic.com/photoAlbum/vms/standard/img/2022/10/12/VIDErmLKMgq0eHB65tgMMUC9221012.png"
                ),
        )
    }

    data class Channel(val name: String, val url: String, val group: String, val logo: String = "")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val allChannels = getCCTVChannels()

        val filteredChannels =
                when (request.data) {
                    "cctv" -> allChannels.filter { it.group == "CCTV" }
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
        // 如果是 CCTV 官网链接，需要提取真实的 M3U8 地址
        if (data.contains("live.cctv.cn")) {
            // 从页面提取 M3U8 地址
            val pageContent = app.get(data).text

            // CCTV 使用 JavaScript 动态加载流地址
            // 提取 guid 参数
            val guidRegex = Regex("\"guid\"\\s*:\\s*\"([^\"]+)\"")
            val guid = guidRegex.find(pageContent)?.groupValues?.get(1)

            if (guid != null) {
                // 构建 M3U8 地址
                val m3u8Url = "https://live.cctv.cn/hls/$guid.m3u8"

                M3u8Helper.generateM3u8(
                                this.name,
                                m3u8Url,
                                referer = mainUrl,
                                headers =
                                        mapOf(
                                                "User-Agent" to
                                                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                                        )
                        )
                        .forEach(callback)
            }
        } else if (data.contains(".m3u8")) {
            // 直接的 M3U8 链接
            M3u8Helper.generateM3u8(this.name, data, referer = mainUrl).forEach(callback)
        }

        return true
    }

    companion object {
        private const val USER_AGENT =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
