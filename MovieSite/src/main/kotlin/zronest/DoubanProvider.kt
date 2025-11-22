package zronest

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**豆瓣热播 - 直接解析 HTML */
class DoubanProvider : MainAPI() {
    override var mainUrl = "https://movie.douban.com"
    override var name = "豆瓣热播"
    override var lang = "zh"
    override val hasMainPage = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    var searchUrl = "https://search.douban.com"

    override val mainPage = mainPageOf(
        "$mainUrl/" to "热门电影",
        "$mainUrl/tv/" to "热门剧集"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data).document
        
        val home = if (request.data.contains("/tv/")) {
            // 解析电视剧页面
            document.select("div.slide-page a").mapNotNull {
                it.toTvSearchResult()
            }
        } else {
            // 解析电影页面 - 正在热映
            val nowPlaying = document.select("div.screening-bd ul li").mapNotNull {
                it.toMovieSearchResult()
            }
            
            // 解析电影页面 - 热门推荐
            val popular = document.select("div.ui-slide-content a").mapNotNull {
                it.toPopularMovieSearchResult()
            }
            
            nowPlaying + popular
        }
        
        return newHomePageResponse(
            listOf(HomePageList(request.name, home.take(20))),
            hasNext = false
        )
    }

    private fun Element.toMovieSearchResult(): SearchResponse? {
        val title = this.selectFirst("a")?.attr("title") ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val posterUrl = this.selectFirst("img")?.attr("src")
        
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toPopularMovieSearchResult(): SearchResponse? {
        val title = this.selectFirst("span")?.text()?.trim() ?: return null
        val href = fixUrl(this.attr("href"))
        val posterUrl = this.selectFirst("img")?.attr("src")
        
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toTvSearchResult(): SearchResponse? {
        val title = this.selectFirst("span")?.text()?.trim() ?: return null
        val href = fixUrl(this.attr("href"))
        val posterUrl = this.selectFirst("img")?.attr("src")
        
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$searchUrl/movie/subject_search?search_text=$encodedQuery"
        val document = app.get(url).document
        
        return document.select("div.item-root").mapNotNull {
            val title = it.selectFirst("a.title-text")?.text()?.trim() ?: return@mapNotNull null
            val href = fixUrl(it.selectFirst("a.cover-link")?.attr("href") ?: return@mapNotNull null)
            val posterUrl = it.selectFirst("img")?.attr("src")
            
            // 判断是电影还是电视剧
            val typeText = it.selectFirst("span.subject-cast")?.text() ?: ""
            val type = if (typeText.contains("集数", ignoreCase = true) || 
                          typeText.contains("季", ignoreCase = true)) {
                TvType.TvSeries
            } else {
                TvType.Movie
            }
            
            if (type == TvType.Movie) {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = posterUrl
                }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        
        val title = document.selectFirst("h1 span")?.text()?.trim() 
            ?: document.selectFirst("h1")?.text()?.trim() 
            ?: return null
        
        val poster = document.selectFirst("div#mainpic img")?.attr("src")
            ?: document.selectFirst("img[rel=v:image]")?.attr("src")
        
        val year = document.selectFirst("span.year")?.text()?.trim()
            ?.replace(Regex("[()]"), "")?.toIntOrNull()
        
        val description = document.selectFirst("span[property=v:summary]")?.text()?.trim()
            ?: document.selectFirst("div.related-info span.all.hidden")?.text()?.trim()
        
        val rating = document.selectFirst("strong.rating_num")?.text()?.trim()
        
        val tags = document.select("span[property=v:genre]").map { it.text() }
        
        val actors = document.select("div.celebrities-list a.celebrity").mapNotNull { 
            val name = it.selectFirst("span.name")?.text()?.trim()
            name?.let { ActorData(Actor(it)) }
        }
        
        // 判断类型
        val infoText = document.select("div#info").text()
        val isTvSeries = infoText.contains("集数", ignoreCase = true) || 
                        infoText.contains("季数", ignoreCase = true) ||
                        infoText.contains("单集片长", ignoreCase = true)
        
        // 聚合搜索其他源
        val providers = listOf(
            DadaquProvider(),
        )
        
        val searchResults = providers.flatMap { provider ->
            runCatching { provider.search(title) }.getOrElse { emptyList() }
        }
        
        if (searchResults.isEmpty()) {
            // 如果没有找到播放源，返回基本信息
            return if (isTvSeries) {
                newTvSeriesLoadResponse(title, url, TvType.TvSeries, emptyList()) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    this.actors = actors
                    this.score = rating?.toDoubleOrNull()?.let { Score.from10(it.toInt()) }
                }
            } else {
                newMovieLoadResponse(title, url, TvType.Movie, "") {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    this.actors = actors
                    this.score = rating?.toDoubleOrNull()?.let { Score.from10(it.toInt()) }
                }
            }
        }
        
        // 按相似度排序
        val sorted = searchResults.sortedByDescending { similarity(title, it.name) }.take(5)
        
        // 构造候选播放源列表
        val episodes = sorted.map { result ->
            newEpisode(result.url) {
                name = "${result.name} [${result.apiName}]"
                posterUrl = result.posterUrl
            }
        }
        
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster ?: sorted.firstOrNull()?.posterUrl
            this.year = year
            this.plot = description ?: "聚合搜索结果，选择一个播放源进入"
            this.tags = tags
            this.actors = actors
            this.score = rating?.toDoubleOrNull()?.let { Score.from10(it.toInt()) }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // 由于是聚合其他源，这里直接加载对应源的链接
        loadExtractor(data, subtitleCallback, callback)
        return true
    }

    // 相似度计算函数
    private fun similarity(a: String, b: String): Double {
        val distance = levenshtein(a, b)
        val maxLen = maxOf(a.length, b.length)
        return if (maxLen == 0) 1.0 else 1.0 - (distance.toDouble() / maxLen)
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }
}