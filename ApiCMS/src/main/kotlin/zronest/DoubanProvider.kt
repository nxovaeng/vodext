package zronest

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.StringUtils.encodeUrl

/**
 * 豆瓣元数据 Provider：首页展示豆瓣榜单/分类（海报、评分），
 * 豆瓣只提供元数据，点击条目时用标题去聚合站搜索并打开可播放的详情页。
 *
 * 逻辑参照 cluntop/tvbox py/douban.py（Frodo API，GROUPS 分类映射）。
 * 注意：豆瓣有 IP 访问限制，请求频繁会被拉黑；此 Provider 只在用户主动浏览时请求。
 */
class DoubanProvider : MainAPI() {
    override var mainUrl = "https://frodo.douban.com"
    override var name = "豆瓣"
    override val hasMainPage = true
    override var lang = "zh"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    // 分类：显示名 -> (接口路径, 数据key)，对照 douban.py GROUPS
    private val categories = listOf(
            "热门电影" to ("movie/hot_gaia" to "items"),
            "热播剧集" to ("subject_collection/tv_hot/items" to "subject_collection_items"),
            "热门动漫" to ("subject_collection/tv_animation/items" to "subject_collection_items"),
            "实时热门电影" to ("subject_collection/movie_real_time_hotest/items" to "subject_collection_items"),
            "实时热播剧集" to ("subject_collection/tv_real_time_hotest/items" to "subject_collection_items"),
            "豆瓣电影Top250" to ("subject_collection/movie_top250/items" to "subject_collection_items"),
            "院线口碑榜" to ("subject_collection/movie_weekly_best/items" to "subject_collection_items"),
    )

    override val mainPage = mainPageOf(
            *categories.map { (label, _) -> label to label }.toTypedArray()
    )

    private val headers = mapOf(
            "Host" to "frodo.douban.com",
            "Referer" to REFERER,
            "User-Agent" to USER_AGENT,
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val (path, dataKey) = categories.first { it.first == request.data }.second
        val start = (page - 1) * 20
        val url = "$DOUBAN_API/$path?apikey=$API_KEY&start=$start&count=20"
        // hot_gaia 需要 sort/area 参数（对照 douban.py）
        val fullUrl = if (path == "movie/hot_gaia") "$url&sort=recommend&area=${"全部".encodeUrl()}" else url
        val res = app.get(fullUrl, headers = headers).parsedSafe<FrodoResp>()
        val rawItems = res?.getItems(dataKey) ?: emptyList()
        val list = rawItems.mapNotNull { it.toSearchResponse() }
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

    private fun FrodoItem.toSearchResponse(): SearchResponse? {
        if (title.isBlank()) return null
        val picUrl = pic?.normal?.takeIf { it.isNotBlank() } ?: return null
        val tvType = when {
            type.contains("movie") -> TvType.Movie
            else -> TvType.TvSeries
        }
        // url 存 subject JSON（含标题），load() 时用标题去聚合站搜
        val data = DoubanSubject(title = title, id = id).toJson()
        return newMovieSearchResponse(title, data, tvType) {
            this.posterUrl = picUrl
        }
    }

    companion object {
        const val DOUBAN_API = "https://frodo.douban.com/api/v2"
        // 对照 douban.py 的 API_KEY（Frodo 接口 key，有 IP 访问限制）
        const val API_KEY = "0ac44ae016490db2204ce0a042db2916"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.143 Safari/537.36 MicroMessenger/7.0.9.501 NetType/WIFI MiniProgramEnv/Windows WindowsWechat"
        const val REFERER = "https://servicewechat.com/wx2f9b06c1de1ccfca/84/page-frame.html"
    }
}

/** 豆瓣条目（load 用） */
private data class DoubanSubject(
        val title: String = "",
        val id: String = "",
)

/** Frodo 集合接口返回：items 或 subject_collection_items */
private data class FrodoResp(
        val items: List<FrodoItem>? = null,
        val subject_collection_items: List<FrodoItem>? = null,
) {
    fun getItems(dataKey: String): List<FrodoItem> =
            if (dataKey == "items") items ?: emptyList()
            else subject_collection_items ?: emptyList()
}

private data class FrodoItem(
        val id: String = "",
        val title: String = "",
        val type: String = "",
        val pic: FrodoPic? = null,
        val rating: FrodoRating? = null,
        val card_subtitle: String = "",
)

private data class FrodoPic(
        val normal: String = "",
)

private data class FrodoRating(
        val value: Double = 0.0,
)
