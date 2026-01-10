package zronest

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLEncoder
import org.jsoup.nodes.Element

/** 达达趣 - https://www.dadaqu.pro/ */
class DadaquProvider : MainAPI() {
    override var mainUrl = "https://www.dadaqu.pro"
    override var name = "达达趣"
    override var lang = "zh"
    override val hasMainPage = true
    override val supportedTypes =
            setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

    override val mainPage =
            mainPageOf(
                    "$mainUrl/type/1.html" to "电影",
                    "$mainUrl/type/2.html" to "电视剧",
                    "$mainUrl/type/3.html" to "综艺",
                    "$mainUrl/type/4.html" to "动漫"
            )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl =
                if (page == 1) {
                    request.data
                } else {
                    request.data.replace(".html", "-$page.html")
                }

        val document = app.get(pageUrl, referer = mainUrl).document

        val home = document.select("a.module-poster-item").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
                listOf(HomePageList(request.name, home)),
                hasNext = home.isNotEmpty()
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        // 使用 title 属性或 img 的 alt 属性获取真正的标题
        // 而不是 div 文本（那个会是状态标签如"完结"、"正片"）
        val title =
                this.attr("title").ifEmpty { this.selectFirst("img")?.attr("alt") }?.trim()
                        ?: return null

        val href = fixUrl(this.attr("href"))
        val posterUrl =
                fixUrlNull(
                        this.selectFirst("img")?.attr("data-original")
                                ?: this.selectFirst("img")?.attr("src")
                )

        // 根据 URL 判断类型
        val type =
                when {
                    href.contains("/type/1") -> TvType.Movie
                    href.contains("/type/2") -> TvType.TvSeries
                    href.contains("/type/3") -> TvType.AsianDrama
                    href.contains("/type/4") -> TvType.Anime
                    else -> TvType.Movie
                }

        return if (type == TvType.Movie) {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        } else {
            newTvSeriesSearchResponse(title, href, type) { this.posterUrl = posterUrl }
        }
    }

    private fun Element.toCardSearchResult(): SearchResponse? {
        val title = this.selectFirst("strong")?.text()?.trim() ?: return null
        val href = fixUrl(this.attr("href"))
        val posterUrl =
                fixUrlNull(
                        this.selectFirst("img")?.attr("data-original")
                                ?: this.selectFirst("img")?.attr("src")
                )

        // 从详情页判断类型，这里先默认为电影
        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$mainUrl/search/${encodedQuery}-------------.html"

        val document = app.get(searchUrl, referer = mainUrl).document

        return document.select("a.module-card-item-poster").mapNotNull { it.toCardSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, referer = mainUrl).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null

        val poster =
                fixUrlNull(
                        document.selectFirst("div.module-info-poster img")?.attr("data-original")
                                ?: document.selectFirst("div.module-info-poster img")?.attr("src")
                )

        val description =
                document.selectFirst("div.module-info-introduction-content")?.text()?.trim()

        // 提取年份
        val year =
                document.select("div.module-info-item")
                        .find { it.text().contains("年份") }
                        ?.selectFirst("div.module-info-item-content")
                        ?.text()
                        ?.trim()
                        ?.toIntOrNull()

        // 提取标签
        val tags =
                document.select("div.module-info-item")
                        .find { it.text().contains("类型") }
                        ?.select("a")
                        ?.map { it.text() }
                        ?: emptyList()

        // 提取演员
        val actors =
                document.select("div.module-info-item")
                        .find { it.text().contains("主演") }
                        ?.select("a")
                        ?.mapNotNull {
                            val name = it.text().trim()
                            if (name.isNotEmpty()) ActorData(Actor(name)) else null
                        }
                        ?: emptyList()

        // 提取评分
        val ratingText = document.selectFirst("div.module-info-item")?.text()
        val rating =
                Regex("评分：([\\d.]+)").find(ratingText ?: "")?.groupValues?.get(1)?.toDoubleOrNull()

        // 提取播放源和剧集
        val playLists = document.select("div.module-play-list")

        if (playLists.isEmpty()) {
            // 没有播放源，返回基本信息
            return newMovieLoadResponse(title, url, TvType.Movie, "") {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.actors = actors
                this.score = rating?.let { Score.from10(it.toInt()) }
            }
        }

        val episodes = mutableListOf<Episode>()

        playLists.forEachIndexed { sourceIndex, playList ->
            val sourceName =
                    playList.selectFirst("div.module-play-list-title")?.text()?.trim()
                            ?: "播放源${sourceIndex + 1}"

            val episodeLinks = playList.select("div.module-play-list-content a")

            episodeLinks.forEachIndexed { epIndex, epLink ->
                val epTitle = epLink.text().trim()
                val epUrl = fixUrl(epLink.attr("href"))

                episodes.add(
                        newEpisode(epUrl) {
                            // 使用实际的剧集名称，而不是播放源名称
                            this.name =
                                    if (episodeLinks.size == 1 && epTitle.isNotEmpty()) {
                                        epTitle // 单集时直接用链接文本（如"正片"、"HD"等）
                                    } else if (epTitle.isNotEmpty()) {
                                        "$epTitle ($sourceName)" // 多集时显示 "第01集 (高清线路)"
                                    } else {
                                        sourceName
                                    }
                            this.episode = epIndex + 1
                            this.posterUrl = poster
                        }
                )
            }
        }

        // 判断类型：单集为电影，多集为剧集
        val type =
                if (episodes.size <= 1) TvType.Movie
                else {
                    // 根据标签判断更具体的类型
                    when {
                        tags.any {
                            it.contains("动漫", ignoreCase = true) ||
                                    it.contains("动画", ignoreCase = true)
                        } -> TvType.Anime
                        tags.any { it.contains("综艺", ignoreCase = true) } -> TvType.AsianDrama
                        else -> TvType.TvSeries
                    }
                }

        return if (type == TvType.Movie && episodes.size == 1) {
            newMovieLoadResponse(title, url, TvType.Movie, episodes.first().data) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.actors = actors
                this.score = rating?.let { Score.from10(it.toInt()) }
            }
        } else {
            newTvSeriesLoadResponse(title, url, type, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.actors = actors
                this.score = rating?.let { Score.from10(it.toInt()) }
            }
        }
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get(data, referer = mainUrl)
        val document = response.document

        // Method 1: Direct video element (快速路径)
        val videoUrl = document.selectFirst("video.art-video")?.attr("src")
        if (!videoUrl.isNullOrEmpty() && !videoUrl.startsWith("blob:")) {
            callback.invoke(
                    newExtractorLink(
                            name = this.name,
                            source = this.name,
                            url = videoUrl,
                            type = INFER_TYPE
                    ) { this.referer = data }
            )
            return true
        }

        // Method 2: Rumble 风格 - 从 JavaScript 中正则提取视频 URL
        // 匹配模式: "url": "https://...", file: "...", src: "..." 等
        val pageHtml = response.text
        val videoUrlRegex =
                Regex(
                        """["']?(?:url|file|source|src|video_url)["']?\s*[:=]\s*["'](https?://[^"']+\.(?:mp4|m3u8)[^"']*)["']""",
                        RegexOption.IGNORE_CASE
                )

        val extractedUrl =
                videoUrlRegex
                        .find(pageHtml)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.replace("\\u002F", "/") // 处理 Unicode 转义
                        ?.replace("\\/", "/") // 处理 JSON 转义

        if (!extractedUrl.isNullOrEmpty()) {
            callback.invoke(
                    newExtractorLink(
                            name = this.name,
                            source = this.name,
                            url = extractedUrl,
                            type = INFER_TYPE
                    ) { this.referer = data }
            )
            return true
        }

        // Method 3: WebViewResolver - 拦截实际的视频网络请求
        // 用于处理 Blob URL 和动态生成的 cmecloud.cn 预签名 URL
        val webViewResolver =
                WebViewResolver(interceptUrl = Regex(""".*\.(mp4|m3u8).*"""), timeout = 20_000L)

        val interceptedUrl = app.get(data, referer = mainUrl, interceptor = webViewResolver).url

        if (interceptedUrl.isNotEmpty() &&
                        (interceptedUrl.contains(".mp4") || interceptedUrl.contains(".m3u8"))
        ) {
            callback.invoke(
                    newExtractorLink(
                            name = this.name,
                            source = this.name,
                            url = interceptedUrl,
                            type = INFER_TYPE
                    ) { this.referer = data }
            )
            return true
        }

        return false
    }
}
