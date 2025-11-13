package nxovaeng

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import java.net.URLEncoder
import kotlin.math.min

/**豆瓣热播 */
class DoubanProvider : MainAPI() {
    override var mainUrl = "https://m.douban.com"
    override var name = "豆瓣热播"
    override var lang = "zh"
    override val hasMainPage = true

    override val mainPage =
        mainPageOf(
            "movie_real_time_hotest" to "热播电影",
            "tv_real_time_hotest" to "热播电视剧",
        )

    suspend inline fun <reified T : Any> doubanGet(url: String): T? {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X) " +
                    "AppleWebKit/605.1.15 (KHTML, like Gecko) " +
                    "Version/14.0 Mobile/15E148 Safari/604.1",
            "Referer" to "https://m.douban.com/",
            "Accept" to "application/json, text/plain, */*",
            "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
            "Accept-Encoding" to "gzip, deflate, br",
            "Connection" to "keep-alive"
        )

        return runCatching { app.get(url, headers = headers).parsed<T>() }.getOrNull()
    }

    // 首页：豆瓣热播榜单
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val limit = 20
        val start = (page - 1) * limit
        val url =
            "$mainUrl/rexxar/api/v2/subject_collection/${request.data}/items?start=$start&count=$limit"

        val resp = doubanGet<DoubanHotResponse>(url) ?: DoubanHotResponse()
        val list = if (request.data.contains("movie")) {
            resp.subject_collection_items.map {
                newMovieSearchResponse(it.title, it.url, TvType.Movie) {
                    posterUrl = it.cover?.url
                }
            }
        } else {
            resp.subject_collection_items.map {
                newTvSeriesSearchResponse(it.title, it.url, TvType.TvSeries) {
                    posterUrl = it.cover?.url
                }
            }
        }

        return newHomePageResponse(
            listOf(
                HomePageList(request.name, list),
            ),
            hasNext = true
        )
    }

    // 详情页：聚合搜索 + 多候选结果展示
    override suspend fun load(url: String): LoadResponse? {
        val title = extractTitleFromDoubanUrl(url)

        // 定义要调用的 Provider 列表
        val providers = listOf(
            BfzyProvider(),
            MdzyProvider(),
            BaseVodProvider()
        )

        // 聚合搜索结果
        val results = providers.flatMap { provider ->
            runCatching { provider.search(title) }.getOrElse { emptyList() }
        }

        if (results.isEmpty()) return null

        // 按相似度排序，取前 5 个候选
        val sorted = results.sortedByDescending { similarity(title, it.name) }.take(5)

        // 构造候选播放源列表
        val episodes = sorted.map { result ->
            newEpisode(result.url) {
                name = "${result.name} [${result.apiName}]"
                score = result.score
                posterUrl = result.posterUrl
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            posterUrl = results.first().posterUrl
            plot = "聚合搜索结果，选择一个播放源进入"
        }
    }

    // 搜索：直接用豆瓣搜索接口（可选）
    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$mainUrl/rexxar/api/v2/search?q=$encoded"
        val resp = app.get(url).parsed<DoubanSearchResponse>()

        return resp.items.map {
            newMovieSearchResponse(it.title, it.url, TvType.Movie) {
                posterUrl = it.cover?.url
            }
        }
    }

    // 提取标题（简单示例：直接用 URL 最后部分）
    private fun extractTitleFromDoubanUrl(url: String): String {
        return url.substringAfterLast("/").substringBefore("?")
    }

    // 相似度函数
    private fun similarity(a: String, b: String): Double {
        val distance = levenshtein(a, b)
        val maxLen = maxOf(a.length, b.length)
        return 1.0 - (distance.toDouble() / maxLen)
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }

    data class DoubanHotResponse(
        val subject_collection_items: List<DoubanItem> = emptyList()
    )

    data class DoubanItem(
        val title: String = "",
        val cover: DoubanCover? = null,
        val info: String = "",
        val url: String = ""
    )

    data class DoubanCover(
        val url: String = ""
    )

    data class DoubanSearchResponse(
        val items: List<DoubanItem> = emptyList()
    )
}