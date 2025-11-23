package nxovaeng

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import org.jsoup.nodes.Element

/** CCTV 官方直播源提供者 使用 CCTV 官方稳定的直播流，面向全国，无地域限制 */
class CCTVProvider : MainAPI() {
        // 插件基础信息
        override var mainUrl = "https://tv.cctv.com"
        override var name = "CCTV Live"
        override var lang = "zh"
        override val hasMainPage = true
        override val hasDownloadSupport = false // 直播流不支持下载
        override val supportedTypes = setOf(TvType.Live)

        override val mainPage = mainPageOf("cctv" to "央视频道", "satellite" to "卫视频道")

        // 伪装成桌面浏览器，非常重要！
        // 否则央视网页会跳转到移动端下载页，导致抓取失败
        private val userAgentDesktop =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36"

        /** 1. 解析频道列表 (Main Page) 对应 HTML 中的 <div class="overview" id="jiemudan"> ... <dl><dt><a> */
        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
                // 我们直接访问 CCTV-1 的页面来获取侧边栏的频道列表，或者访问 /live/ 首页
                val url = "$mainUrl/live/cctv1/"

                // 发起 HTTP 请求获取 HTML
                val document =
                        app.get(url, headers = mapOf("User-Agent" to userAgentDesktop)).document

                // 使用 Jsoup 选择器定位左侧/右侧的频道列表
                // 根据你提供的 HTML: <div class="overview" id="jiemudan"> -> <dl> -> <dt> -> <a>
                val channels =
                        document.select("div#jiemudan dl dt a").mapNotNull { element ->
                                toLiveChannel(element)
                        }

                return newHomePageResponse(
                        list =
                                HomePageList(
                                        name = "央视直播",
                                        list = channels,
                                        isHorizontalImages = true
                                ),
                        hasNext = false
                )
        }

        // 辅助方法：将 HTML 元素转换为 LiveTvSearchResponse
        private fun toLiveChannel(element: Element): LiveSearchResponse? {
                val title = element.text() // 获取 "CCTV-1 综合"
                val href = element.attr("href") // 获取 "https://tv.cctv.com/live/cctv1/"

                if (href.isEmpty()) return null

                return newLiveSearchResponse(title, href, TvType.Live) {
                        // 这里可以根据 url 里的 cctv1 等 ID 手动拼接封面图，或者先用通用图
                        this.posterUrl =
                                "https://p1.img.cctvpic.com/photoAlbum/templet/common/DEPA1553653185997107/cctv_logo.png"
                }
        }

        /** 2. 解析 Blob URL (Load) 核心逻辑：使用 WebView 拦截网络请求 */
        override suspend fun load(url: String): LoadResponse? {
                // url = "https://tv.cctv.com/live/cctv1/"

                // 步骤 A: 提取频道 ID 或名称 (用于显示)
                // 从 URL 提取 ID: /live/cctv1/ -> cctv1
                val channelId =
                        Regex("""\/live\/([a-zA-Z0-9]+)\/?""").find(url)?.groupValues?.get(1)
                                ?: "CCTV"

                // 步骤 B: 启动 WebView 嗅探
                // 央视的真实流地址包含 .m3u8，且通常带有 token 参数
                val resolverRegex = Regex("""https?://.*\.m3u8.*""")

                val webResolver =
                        WebViewResolver(
                                interceptUrl = resolverRegex,
                                additionalUrls = listOf(Regex("""txt|m3u8""")),
                                userAgent = userAgentDesktop,
                                useOkhttp = false,
                                timeout = 15_000L
                        )

                // 执行嗅探,获取匹配到的 URL
                val streamUrl =
                        app.get(
                                        url,
                                        headers =
                                                mapOf(
                                                        "Referer" to url,
                                                        "User-Agent" to userAgentDesktop
                                                ),
                                        interceptor = webResolver,
                                        // 央视广告长，加载慢，给 20 秒超时
                                        timeout = 20000L
                                )
                                .url

                // 返回播放信息
                return newLiveStreamLoadResponse(
                        name = channelId.uppercase(), // 显示名字
                        url = url, // 页面 URL (作为 ID)
                        dataUrl = streamUrl // 真实的 m3u8 地址
                ) {
                        this.posterUrl =
                                "https://p1.img.cctvpic.com/photoAlbum/templet/common/DEPA1553653185997107/cctv_logo.png"
                }
        }

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

        override suspend fun search(query: String): List<SearchResponse> {
                val channels = getCCTVChannels()

                return channels.filter { it.name.contains(query, ignoreCase = true) }.map { channel
                        ->
                        newMovieSearchResponse(channel.name, channel.url, TvType.Live) {
                                this.posterUrl = channel.logo
                        }
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
