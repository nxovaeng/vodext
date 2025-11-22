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
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLEncoder

/**
 * Base provider for MacCMS 风格站点（页面渲染站点，通常有列表页和详情页）
 * 该基类实现常见的 list/search/load 模式，子类只需覆盖选择器或路径
 */
open class PipishiProvider : MainAPI() {
    override var mainUrl = "https://www.pipishi.cc"
    override var name = "皮皮师"

    override val hasMainPage = true
    override var lang = "zh"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    override val mainPage = mainPageOf(
        "label/hot.html" to "热榜",
        "type/1.html" to "电影",
        "type/2.html" to "剧集",
        "type/4.html" to "动漫"
    )

    protected open val listSelector = "div.module-items.module-poster-items-base"
    protected open val searchSelector = "div.module-items.module-card-items"
    protected open val cardSelector = "div.module-card-item-info"
    protected open val itemTitleSelector = "a"
    protected open val itemPosterSelector = "img"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl = "$mainUrl/${request.data}"
        val doc = app.get(
            pageUrl,
            referer = mainUrl
        ).document
        var tvType = TvType.TvSeries
        if (request.name.contains("电影")) {
            tvType = TvType.Movie
        }
        val items = doc.select(listSelector).flatMap { it.select(itemTitleSelector) }.mapNotNull { el ->
            val href = fixUrl(el.attr("href"))
            val title = el.attr("title").ifEmpty { el.text().trim() }
            val poster = fixUrlNull(el.selectFirst(itemPosterSelector)?.attr("src"))
            newMovieSearchResponse(title, href, tvType) { this.posterUrl = poster }
        }
        return newHomePageResponse(listOf(HomePageList(request.name, items)), hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // 尝试站点自带搜索表单
        val searchUrl = "$mainUrl/search/${URLEncoder.encode(query, "UTF-8")}-------------.html"

        try {
            val doc = app.get(
                searchUrl,
                referer = mainUrl
            ).document
            val results = doc.select(searchSelector).flatMap {
                it.select(cardSelector)
            }.mapNotNull { info ->
                val title = info.select("div.module-card-item-title strong").text()
                val href = fixUrl(info.select(itemTitleSelector).text())
                newMovieSearchResponse(title, href, TvType.Movie)
            }
        }
        catch (e: Exception) {
            // ignore
        }
        return emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(
            url,
            referer = mainUrl
        ).document
        val playLink = doc.select("div.module-info-footer a.main-btn").attr("href")
        val title = doc.select("div.module-info-heading h1").attr("title")
        val poster = fixUrlNull(doc.selectFirst("div.module-item-pic img")?.attr("src"))
        val plot = doc.selectFirst("div.module-info-introduction-content p")?.text()?.trim()

        // 尝试找剧集列表
        val episodeEls = doc.select(".play-list li a, .stui-content__playlist a, .playfrom a")

        val episodes = episodeEls.mapIndexed { idx, el ->
            val href = fixUrl(el.attr("href"))
            val name = el.text().ifEmpty { "第 ${idx + 1} 集" }
            newEpisode(href) {
                this.name = name
                this.episode = idx + 1
            }
        }
        return newTvSeriesLoadResponse(title, playLink, TvType.TvSeries, episodes) {
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
        val doc = app.get(
            data,
            referer = mainUrl
        ).document
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
