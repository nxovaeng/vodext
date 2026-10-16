package zronest

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.StringUtils.encodeUrl

/**
 * 豆瓣元数据 Provider：首页展示豆瓣的榜单/分类（海报、评分），
 * 豆瓣只提供元数据，点击条目时用标题去聚合站搜索并打开可播放的详情页。
 *
 * 数据源：movie.douban.com/j/search_subjects（免 key，2026-10-16 实测可用）；
 * Frodo API 的 key 已失效，不用。
 */
class DoubanProvider : MainAPI() {
    override var mainUrl = "https://movie.douban.com"
    override var name = "豆瓣"
    override val hasMainPage = true
    override var lang = "zh"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    // 首页分类：(显示名, type, tag)
    private val categories = listOf(
            "热门电影" to ("movie" to "热门"),
            "热门剧集" to ("tv" to "热门"),
            "热门动漫" to ("tv" to "日本动画"),
            "豆瓣高分" to ("movie" to "豆瓣高分"),
            "华语电影" to ("movie" to "华语"),
            "美剧" to ("tv" to "美剧"),
            "日剧" to ("tv" to "日剧"),
            "韩剧" to ("tv" to "韩剧"),
    )

    override val mainPage = mainPageOf(
            *categories.map { (label, _) -> label to label }.toTypedArray()
    )

    private fun catParams(label: String): Pair<String, String> =
            categories.first { it.first == label }.second

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val (type, tag) = catParams(request.data)
        val url = "$mainUrl/j/search_subjects?type=$type&tag=${tag.encodeUrl()}" +
                "&sort=recommend&page_limit=20&page_start=${(page - 1) * 20}"
        val res = app.get(url, headers = mapOf("User-Agent" to UA)).parsedSafe<DoubanSearchResp>()
        val tvType = if (type == "movie") TvType.Movie else TvType.TvSeries
        val list = res?.subjects?.mapNotNull { it.toSearchResponse(tvType, tag) } ?: emptyList()
        return newHomePageResponse(
                listOf(HomePageList(request.name, list)),
                hasNext = list.isNotEmpty()
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // 豆瓣站内搜索同样走聚合：直接搜标题
        return VodAggregatorProvider().search(query)
    }

    /**
     * 点击豆瓣条目：用标题去聚合站搜索，打开第一个匹配的可播放详情。
     * url 为 DoubanSubject 的 JSON（含标题）。
     */
    override suspend fun load(url: String): LoadResponse {
        val subject = tryParseJson<DoubanSubject>(url) ?: throw ErrorLoadingException("无效的豆瓣条目")
        val title = subject.title
        val aggregator = VodAggregatorProvider()
        val results = aggregator.search(title)
        // 优先精确匹配标题，否则取第一个
        val target = results.firstOrNull { it.name == title } ?: results.firstOrNull()
                ?: throw ErrorLoadingException("聚合站未找到《$title》")
        // 委托给聚合站加载详情（多源多集）
        return aggregator.load(target.url)
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        // load() 已委托给聚合站，这里同样委托
        return VodAggregatorProvider().loadLinks(data, isCasting, subtitleCallback, callback)
    }

    private fun DoubanSubjectItem.toSearchResponse(tvType: TvType, tag: String): SearchResponse? {
        if (title.isBlank() || cover.isBlank()) return null
        val type = when {
            tvType == TvType.Movie -> TvType.Movie
            tag.contains("动画") || tag.contains("动漫") -> TvType.Anime
            else -> TvType.TvSeries
        }
        // url 存 subject JSON（含标题），load() 时用标题去聚合站搜
        val data = DoubanSubject(title = title, id = id).toJson()
        return newMovieSearchResponse(title, data, type) {
            this.posterUrl = cover
        }
    }

    companion object {
        const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }
}

/** 豆瓣条目（load 用） */
private data class DoubanSubject(
        val title: String = "",
        val id: String = "",
)

/** /j/search_subjects 返回 */
private data class DoubanSearchResp(
        val subjects: List<DoubanSubjectItem> = emptyList(),
)

private data class DoubanSubjectItem(
        val id: String = "",
        val title: String = "",
        val cover: String = "",
        val rate: String = "",
)
