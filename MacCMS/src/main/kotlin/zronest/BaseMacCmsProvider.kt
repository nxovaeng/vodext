package zronest

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

/**
 * Base provider for MacCMS 风格站点（页面渲染站点，通常有列表页和详情页）
 * 该基类实现常见的 list/search/load 模式，子类只需覆盖选择器或路径
 */
abstract class BaseMacCmsProvider : MainAPI() {
    override val hasMainPage = true
    override var lang = "zh"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    protected open val listSelector = "div.site-list, .vodlist, .stui-vodlist"
    protected open val itemTitleSelector = "a"
    protected open val itemPosterSelector = "img"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl = if (page <= 1) mainUrl else "$mainUrl/page/$page"
        val doc = app.get(pageUrl).document
        val items = doc.select(listSelector).flatMap { it.select(itemTitleSelector) }.mapNotNull { el ->
            val href = fixUrl(el.attr("href"))
            val title = el.attr("title").ifEmpty { el.text().trim() }
            val poster = fixUrlNull(el.selectFirst(itemPosterSelector)?.attr("src"))
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
        }
        return newHomePageResponse(listOf(HomePageList(request.name, items)))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // 尝试站点自带搜索表单
        val searchUrls = listOf(
            "$mainUrl/search.php?wd=${java.net.URLEncoder.encode(query, "UTF-8")}",
            "$mainUrl/index.php?m=vod-search&wd=${java.net.URLEncoder.encode(query, "UTF-8")}" 
        )
        for (u in searchUrls) {
            try {
                val doc = app.get(u).document
                val results = doc.select(listSelector).flatMap { it.select(itemTitleSelector) }.mapNotNull { el ->
                    val href = fixUrl(el.attr("href"))
                    val title = el.attr("title").ifEmpty { el.text().trim() }
                    newMovieSearchResponse(title, href, TvType.Movie)
                }
                if (results.isNotEmpty()) return results
            } catch (e: Exception) {
                // ignore
            }
        }
        return emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1, .title, .vodh h1")?.text()?.trim() ?: ""
        val poster = fixUrlNull(doc.selectFirst("meta[property=og:image], .vodimg img")?.attr("content")
            ?: doc.selectFirst(itemPosterSelector)?.attr("src"))
        val plot = doc.selectFirst(".vodplayinfo, .vodcontent, .playinfo")?.text()?.trim()

        // 尝试找剧集列表
        val episodeEls = doc.select(".play-list li a, .stui-content__playlist a, .playfrom a")
        if (episodeEls.isNotEmpty()) {
            val episodes = episodeEls.mapIndexed { idx, el ->
                val href = fixUrl(el.attr("href"))
                val name = el.text().ifEmpty { "第 ${idx + 1} 集" }
                newEpisode(href) {
                    this.name = name
                    this.episode = idx + 1
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
            }
        }

        // fallback: 尝试从页面上找到第一个播放链接
        val firstPlay = doc.selectFirst("iframe")?.attr("src") ?: doc.selectFirst("video source[src]")?.attr("src")
        return newMovieLoadResponse(title, url, TvType.Movie, firstPlay ?: url) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // data is episode url -> load and extract
        val doc = app.get(data).document
        // try iframe src
        val iframe = doc.selectFirst("iframe")?.attr("src")
        if (!iframe.isNullOrEmpty()) {
            loadExtractor(iframe, referer = data, subtitleCallback = subtitleCallback, callback = callback)
            return true
        }

        // try direct video link
        val video = doc.selectFirst("video source[src]")?.attr("src")
        if (!video.isNullOrEmpty()) {
            callback.invoke(newExtractorLink(name, name, url = video, type = INFER_TYPE) { this.referer = data })
            return true
        }

        return false
    }
}
