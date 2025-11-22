package nxovaeng

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**达达趣 - https://www.dadaqu.pro/ */
class DadaquProvider : MainAPI() {
    override var mainUrl = "https://www.dadaqu.pro"
    override var name = "达达趣"
    override var lang = "zh"
    override val hasMainPage = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "$mainUrl/type/1.html" to "电影",
        "$mainUrl/type/2.html" to "电视剧",
        "$mainUrl/type/3.html" to "综艺",
        "$mainUrl/type/4.html" to "动漫"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val pageUrl = if (page == 1) {
            request.data
        } else {
            request.data.replace(".html", "-$page.html")
        }
        
        val document = app.get(pageUrl).document
        
        val home = document.select("a.module-poster-item").mapNotNull {
            it.toSearchResult()
        }
        
        return newHomePageResponse(
            listOf(HomePageList(request.name, home)),
            hasNext = home.isNotEmpty()
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div")?.text()?.trim() ?: return null
        val href = fixUrl(this.attr("href"))
        val posterUrl = this.selectFirst("img")?.attr("data-src")
            ?: this.selectFirst("img")?.attr("src")
        
        // 根据 URL 判断类型
        val type = when {
            href.contains("/type/1") -> TvType.Movie
            href.contains("/type/2") -> TvType.TvSeries
            href.contains("/type/3") -> TvType.AsianDrama
            href.contains("/type/4") -> TvType.Anime
            else -> TvType.Movie
        }
        
        return if (type == TvType.Movie) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        } else {
            newTvSeriesSearchResponse(title, href, type) {
                this.posterUrl = posterUrl
            }
        }
    }

    private fun Element.toCardSearchResult(): SearchResponse? {
        val title = this.selectFirst("strong")?.text()?.trim() ?: return null
        val href = fixUrl(this.attr("href"))
        val posterUrl = this.selectFirst("img")?.attr("data-src")
            ?: this.selectFirst("img")?.attr("src")
        
        // 从详情页判断类型，这里先默认为电影
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$mainUrl/search/${encodedQuery}-------------.html"
        
        val document = app.get(searchUrl).document
        
        return document.select("a.module-card-item-poster").mapNotNull {
            it.toCardSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        
        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        
        val poster = document.selectFirst("div.module-info-poster img")?.attr("data-src")
            ?: document.selectFirst("div.module-info-poster img")?.attr("src")
        
        val description = document.selectFirst("div.module-info-introduction-content")?.text()?.trim()
        
        // 提取年份
        val year = document.select("div.module-info-item").find { 
            it.text().contains("年份")
        }?.selectFirst("div.module-info-item-content")?.text()?.trim()?.toIntOrNull()
        
        // 提取标签
        val tags = document.select("div.module-info-item").find {
            it.text().contains("类型")
        }?.select("a")?.map { it.text() } ?: emptyList()
        
        // 提取演员
        val actors = document.select("div.module-info-item").find {
            it.text().contains("主演")
        }?.select("a")?.mapNotNull { 
            val name = it.text().trim()
            if (name.isNotEmpty()) ActorData(Actor(name)) else null
        } ?: emptyList()
        
        // 提取评分
        val ratingText = document.selectFirst("div.module-info-item")?.text()
        val rating = Regex("评分：([\\d.]+)").find(ratingText ?: "")?.groupValues?.get(1)?.toDoubleOrNull()
        
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
            val sourceName = playList.selectFirst("div.module-play-list-title")?.text()?.trim() 
                ?: "播放源${sourceIndex + 1}"
            
            val episodeLinks = playList.select("div.module-play-list-content a")
            
            episodeLinks.forEachIndexed { epIndex, epLink ->
                val epTitle = epLink.text().trim()
                val epUrl = fixUrl(epLink.attr("href"))
                
                episodes.add(
                    newEpisode(epUrl) {
                        this.name = if (episodeLinks.size == 1) {
                            sourceName
                        } else {
                            "$sourceName - $epTitle"
                        }
                        this.episode = epIndex + 1
                        this.posterUrl = poster
                    }
                )
            }
        }
        
        // 判断类型：单集为电影，多集为剧集
        val type = if (episodes.size <= 1) TvType.Movie else {
            // 根据标签判断更具体的类型
            when {
                tags.any { it.contains("动漫", ignoreCase = true) || it.contains("动画", ignoreCase = true) } -> TvType.Anime
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
        val document = app.get(data).document
        
        // 提取播放器中的 iframe
        val iframeSrc = document.select("iframe").attr("src")
        
        if (iframeSrc.isNotEmpty()) {
            val fullIframeUrl = fixUrl(iframeSrc)
            loadExtractor(fullIframeUrl, subtitleCallback, callback)
        }
        
        // 尝试提取直接的视频链接
        val scriptContent = document.select("script").joinToString("\n") { it.html() }
        
        // 常见的视频链接模式
        val urlPatterns = listOf(
            Regex("\"url\"\\s*:\\s*\"([^\"]+)\""),
            Regex("player_aaaa=\\{[^}]*url:\"([^\"]+)\""),
            Regex("var\\s+url\\s*=\\s*['\"]([^'\"]+)['\"]"),
            Regex("src\\s*:\\s*['\"]([^'\"]+\\.m3u8[^'\"]*)"),
            Regex("src\\s*:\\s*['\"]([^'\"]+\\.mp4[^'\"]*)")
        )
        
        for (pattern in urlPatterns) {
            val match = pattern.find(scriptContent)
            if (match != null) {
                val videoUrl = match.groupValues[1]
                if (videoUrl.startsWith("http")) {
                    callback.invoke(
                        newExtractorLink(
                            this.name,
                            this.name,
                            videoUrl,
                        )
                    )
                    return true
                }
            }
        }
        
        return true
    }
}
