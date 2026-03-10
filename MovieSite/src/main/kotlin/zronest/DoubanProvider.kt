package zronest

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/** 豆瓣热搜 - 使用豆瓣 JSON API 参考 douban_hot_v2.js，使用 /j/search_subjects 等 API 获取数据 图片直接从 API 返回，无防盗链问题 */
class DoubanProvider : MainAPI() {
    override var mainUrl = "https://movie.douban.com"
    override var name = "豆瓣热搜"
    override var lang = "zh"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    companion object {
        private const val TAG = "DoubanProvider"
        private const val CATEGORY_API = "https://movie.douban.com/j/search_subjects"
        private const val SUGGEST_API = "https://movie.douban.com/j/subject_suggest"
        private const val SUBJECT_API = "https://movie.douban.com/j/subject_abstract"
    }

    private val apiHeaders =
            mapOf(
                    "User-Agent" to
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                    "Referer" to "https://movie.douban.com/"
            )

    // 使用标签分类作为首页，与 JS 插件保持一致
    override val mainPage =
            mainPageOf(
                    "热门" to "🔥热门",
                    "最新" to "🆕最新",
                    "经典" to "🎬经典",
                    "豆瓣高分" to "⭐高分",
                    "冷门佳片" to "💎冷门",
                    "华语" to "🇨🇳华语",
                    "欧美" to "🇺🇸欧美",
                    "韩国" to "🇰🇷韩国",
                    "日本" to "🇯🇵日本"
            )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val tag = request.data // 标签名称
        val pageStart = (page - 1) * 20
        val url =
                "$CATEGORY_API?type=movie&tag=${URLEncoder.encode(tag, "UTF-8")}&page_limit=20&page_start=$pageStart"

        val response = app.get(url, headers = apiHeaders).text
        val json = JSONObject(response)
        val subjects = json.optJSONArray("subjects") ?: JSONArray()

        val home = mutableListOf<SearchResponse>()
        for (i in 0 until subjects.length()) {
            val item = subjects.getJSONObject(i)
            val title = item.optString("title", "").trim()
            val rate = item.optString("rate", "")
            val cover = item.optString("cover", "")
            val id = item.optString("id", "")

            if (title.isNotEmpty() && id.isNotEmpty()) {
                home.add(
                        newMovieSearchResponse(title, "$mainUrl/subject/$id/", TvType.Movie) {
                            this.posterUrl = cover
                            this.quality =
                                    if (rate.isNotEmpty() && rate != "0")
                                            SearchQuality.valueOf("⭐$rate")
                                    else null
                        }
                )
            }
        }

        return newHomePageResponse(
                listOf(HomePageList(request.name, home)),
                hasNext = home.size >= 20
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // 使用 subject_suggest API（与 JS 保持一致）
        val url = "$SUGGEST_API?q=${URLEncoder.encode(query, "UTF-8")}"
        val response = app.get(url, headers = apiHeaders).text
        val data = JSONArray(response)

        val results = mutableListOf<SearchResponse>()
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            val type = item.optString("type", "")
            // 只处理电影和电视剧
            if (type != "movie" && type != "tv") continue

            val title = item.optString("title", "").trim()
            val cover = item.optString("img", "")
            val id = item.optString("id", "")
            val year = item.optString("year", "")
            val episode = item.optString("episode", "")

            if (title.isNotEmpty() && id.isNotEmpty()) {
                val tvType = if (type == "tv") TvType.TvSeries else TvType.Movie
                val detailUrl = "$mainUrl/subject/$id/"

                if (tvType == TvType.TvSeries) {
                    results.add(
                            newTvSeriesSearchResponse(title, detailUrl, tvType) {
                                this.posterUrl = cover
                            }
                    )
                } else {
                    results.add(
                            newMovieSearchResponse(title, detailUrl, tvType) {
                                this.posterUrl = cover
                            }
                    )
                }
            }
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        // 从 URL 提取豆瓣 ID
        val id = Regex("""/subject/(\d+)""").find(url)?.groupValues?.get(1) ?: return null

        // 使用 subject_abstract API 获取详情（与 JS 保持一致）
        val apiUrl = "$SUBJECT_API?subject_id=$id"
        val response = app.get(apiUrl, headers = apiHeaders).text
        val json = JSONObject(response)
        val subject = json.optJSONObject("subject") ?: return null

        var title = subject.optString("title", "影片$id")
        // 清理标题中的年份后缀，如 "年会不能停!(2023)"
        title = title.replace(Regex("""\s*\(\d{4}\)\s*$"""), "").trim()

        val cover = subject.optString("cover", "")
        val rate = subject.optString("rate", "")
        val year = subject.optString("release_year", "").toIntOrNull()

        // 解析简介
        val intro = buildString {
            val shortComment = subject.optJSONObject("short_comment")
            if (shortComment != null) {
                val content = shortComment.optString("content", "")
                if (content.isNotEmpty()) append(content)
            } else {
                val shortInfo = subject.optString("short_info", "")
                if (shortInfo.isNotEmpty()) append(shortInfo)
            }
        }

        // 导演和演员
        val directors =
                subject.optJSONArray("directors")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }
                        ?: emptyList()

        val actors =
                subject.optJSONArray("actors")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }
                        ?: emptyList()

        val types =
                subject.optJSONArray("types")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }
                        ?: emptyList()

        val region = subject.optString("region", "")
        val duration = subject.optString("duration", "")

        // 构建详细描述
        val description = buildString {
            if (rate.isNotEmpty()) appendLine("⭐ 豆瓣评分: $rate")
            if (year != null) appendLine("📅 年份: $year")
            if (types.isNotEmpty()) appendLine("🎭 类型: ${types.joinToString(" / ")}")
            if (region.isNotEmpty()) appendLine("🌍 地区: $region")
            if (duration.isNotEmpty()) appendLine("⏱️ 时长: $duration")
            if (directors.isNotEmpty()) appendLine("🎬 导演: ${directors.joinToString(" / ")}")
            if (actors.isNotEmpty()) appendLine("👥 演员: ${actors.joinToString(" / ")}")
            if (intro.isNotEmpty()) appendLine("\n📖 短评: $intro")
        }

        val actorData = actors.map { ActorData(Actor(it)) }

        // 判断是否为剧集
        val isTvSeries =
                types.any {
                    it.contains("剧", ignoreCase = true) || it.contains("动画", ignoreCase = true)
                }

        // 聚合搜索其他源
        val providers = listOf<MainAPI>(DadaquProvider(), PipishiProvider())

        val searchResults = mutableListOf<SearchResponse>()
        for (provider in providers) {
            try {
                searchResults.addAll(provider.search(title).orEmpty())
            } catch (e: Exception) {
                Log.d(TAG, "搜索 ${provider.name} 失败: ${e.message}")
            }
        }

        if (searchResults.isEmpty()) {
            return if (isTvSeries) {
                newTvSeriesLoadResponse(title, url, TvType.TvSeries, emptyList()) {
                    this.posterUrl = cover.ifEmpty { null }
                    this.year = year
                    this.plot = description
                    this.tags = types
                    this.actors = actorData
                    this.score = rate.toDoubleOrNull()?.let { Score.from10(it.toInt()) }
                }
            } else {
                newMovieLoadResponse(title, url, TvType.Movie, "") {
                    this.posterUrl = cover.ifEmpty { null }
                    this.year = year
                    this.plot = description
                    this.tags = types
                    this.actors = actorData
                    this.score = rate.toDoubleOrNull()?.let { Score.from10(it.toInt()) }
                }
            }
        }

        // 按相似度排序
        val sorted = searchResults.sortedByDescending { similarity(title, it.name) }.take(5)

        // 构造候选播放源列表
        val episodes = mutableListOf<Episode>()
        for (result in sorted) {
            episodes.add(
                    newEpisode(result.url) {
                        this.name = "${result.name} [${result.apiName}]"
                        this.posterUrl = result.posterUrl
                    }
            )
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = cover.ifEmpty { sorted.firstOrNull()?.posterUrl }
            this.year = year
            this.plot = description.ifEmpty { "聚合搜索结果，选择一个播放源进入" }
            this.tags = types
            this.actors = actorData
            this.score = rate.toDoubleOrNull()?.let { Score.from10(it.toInt()) }
        }
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        // 聚合其他源，直接加载对应源的链接
        loadExtractor(data, subtitleCallback, callback)
        return true
    }

    // 相似度计算
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
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.length][b.length]
    }
}
