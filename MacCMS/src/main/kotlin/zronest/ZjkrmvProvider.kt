package nxovaeng

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * 追剧狂人影视站点 - https://www.zjkrmv.com
 * 备用域名: www.zjkrmv.vip
 */
class ZjkrmvProvider : MainAPI() {
    override var mainUrl = "https://www.zjkrmv.com"
    override var name = "追剧狂人"
    override var lang = "zh"
    override val hasMainPage = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "$mainUrl/vodshow/1-----------.html" to "最近更新",
        "$mainUrl/vodtype/1.html" to "电影",
        "$mainUrl/vodtype/2.html" to "电视剧",
        "$mainUrl/vodtype/3.html" to "综艺",
        "$mainUrl/vodtype/4.html" to "动漫"
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
        
        val home = document.select("div.module-items a.module-item-title, div.module-items a[href*='/voddetail/']").mapNotNull {
            it.toSearchResult()
        }
        
        return newHomePageResponse(
            listOf(HomePageList(request.name, home)),
            hasNext = home.isNotEmpty()
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.attr("title").ifEmpty { this.text().trim() }
        if (title.isEmpty()) return null
        
        val href = fixUrl(this.attr("href"))
        
        // 查找海报图片 - 可能在父元素或兄弟元素中
        val posterUrl = this.parent()?.selectFirst("img.lazy, img[data-original], img.lazyload")?.attr("data-original")
            ?: this.parent()?.selectFirst("img")?.attr("src")
        
        // 根据 URL 判断类型
        val type = when {
            href.contains("/vodtype/1") -> TvType.Movie
            href.contains("/vodtype/2") -> TvType.TvSeries
            href.contains("/vodtype/3") -> TvType.AsianDrama
            href.contains("/vodtype/4") -> TvType.Anime
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

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$mainUrl/index.php/vod/search.html?wd=$encodedQuery"
        
        val document = app.get(searchUrl).document
        
        return document.select("div.module-items a.module-item-title, div.module-card-items a[href*='/voddetail/']").mapNotNull {
            val title = it.attr("title").ifEmpty { it.text().trim() }
            if (title.isEmpty()) return@mapNotNull null
            
            val href = fixUrl(it.attr("href"))
            val posterUrl = it.parent()?.selectFirst("img.lazy, img[data-original]")?.attr("data-original")
                ?: it.parent()?.selectFirst("img")?.attr("src")
            
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        
        val title = document.selectFirst("h1")?.text()?.trim() 
            ?: document.selectFirst("div.module-info-heading h1")?.text()?.trim()
            ?: return null
        
        val poster = document.selectFirst("div.module-item-pic img, img.lazy")?.attr("data-original")
            ?: document.selectFirst("div.module-item-pic img")?.attr("src")
        
        val plot = document.selectFirst("div.module-info-introduction-content")?.text()?.trim()
            ?: document.selectFirst("div.content_desc")?.text()?.trim()
        
        // 提取年份
        val year = document.select("div.module-info-item").find { 
            it.text().contains("年份")
        }?.text()?.substringAfter("：")?.substringAfter("年份")?.trim()?.toIntOrNull()
        
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
        
        // 提取所有播放源和剧集
        val playLists = document.select("div.module-play-list")
        
        if (playLists.isEmpty()) {
            // 没有播放源，返回基本信息
            return newMovieLoadResponse(title, url, TvType.Movie, "") {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
                this.actors = actors
            }
        }
        
        val episodes = mutableListOf<Episode>()
        
        playLists.forEachIndexed { sourceIndex, playList ->
            val sourceName = playList.selectFirst("div.module-play-list-title")?.text()?.trim() 
                ?: "播放源${sourceIndex + 1}"
            
            val episodeLinks = playList.select("div.module-play-list-content a, a[href*='/vodplay/']")
            
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
                this.plot = plot
                this.tags = tags
                this.actors = actors
            }
        } else {
            newTvSeriesLoadResponse(title, url, type, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
                this.actors = actors
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
                            videoUrl
                        ) {
                            this.referer = data
                        }
                    )
                    return true
                }
            }
        }
        
        return true
    }
}