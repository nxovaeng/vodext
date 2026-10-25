package com.Donghuastream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.StringUtils.encodeUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.json.JSONObject

/**
 * 国漫 2K/4K 聚合 Provider。
 * 整合 Donghuastream (donghuastream.org)、Animekhor (animekhor.org) 和 SeaTV (donghuafun.com) 三大源。
 *
 * - 首页/搜索：并行向各站请求，按归一化剧名（去除季数、括号、标点等）合并为同一条目，
 *   url 存 {"站点名": url/id} 的 JSON（fix = false）。
 * - 详情：并行获取各站剧集，从播放列表标题（.epl-title / .epl-num / EP等）中智能提取真实集数，
 *   按集数对齐合并，聚合为多播放源（Dailymotion 1080p/1440p、SeaTV 4K VIP、Rumble等）。
 * - 播放：按源前缀路由委托至对应站点原生的 loadLinks 与 Extractor 处理。
 */
class DonghuaAggregatorProvider : MainAPI() {

    override var name = "国漫2K聚合"
    override var mainUrl = "https://donghuastream.org"
    override var lang = "zh"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        "update" to "最近更新",
        "popular" to "热门国漫",
    )

    companion object {
        const val SITE_DHS = "Donghuastream"
        const val SITE_AK = "Animekhor"
        const val SITE_SEATV = "SeaTV"

        /**
         * 从播放列表标题、集数文本或 URL 中提取真实集数
         * 例如："Episode 164", "163 (Good Sub)(4K)", "EP164", "第164集", "...-episode-163-..."
         */
        fun extractEpNum(vararg candidates: String): Int? {
            for (text in candidates) {
                if (text.isBlank()) continue
                // 1. Episode 123 / Eps 123 / Ep 123 / EP123
                Regex("""(?:Episode|Eps|Ep|EP)\s*0*(\d+)""", RegexOption.IGNORE_CASE)
                    .find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }

                // 2. 第 123 集 / 话 / 期
                Regex("""第\s*0*(\d+)\s*[集话期]""")
                    .find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }

                // 3. 行首数字，如 "164 only Discord..." 或 "163 (Good Sub)..."
                Regex("""^\s*0*(\d+)\b""")
                    .find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }

                // 4. URL slug 提取，如 "...-episode-163-..."
                Regex("""(?:-episode-|-ep-|-eps-)0*(\d+)""", RegexOption.IGNORE_CASE)
                    .find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
            }
            return null
        }

        /**
         * 归一化剧名：去除 HTML 实体、标点符号、多余空格及 Season 变体，用于跨站精确合并
         * 例如："Battle Through the Heavens Season 5" 与 "Battle Through The Heavens 5th Season"
         */
        fun normTitle(name: String): String {
            var n = name.lowercase()
            n = n.replace("&#8217;", "'").replace("&amp;", "&").replace("’", "'")
            n = n.replace(Regex("""\[.*?\]"""), "")
            n = n.replace(Regex("""\(.*?\)"""), "")
            n = n.replace(Regex("""\b(\d+)(?:st|nd|rd|th)\s+season\b"""), "season $1")
            return n.replace(Regex("""[\s\W_]+"""), "")
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val order = request.data // "update" 或 "popular"
        val items = supervisorScope {
            val dhsDeferred = async {
                try {
                    val statusParam = if (order == "popular") "" else "ongoing"
                    val url = "https://donghuastream.org/anime/?status=$statusParam&type=&order=$order&page=$page"
                    val doc = app.get(url).document
                    doc.select("div.listupd > article").mapNotNull { article ->
                        val a = article.selectFirst(".bsx > a") ?: return@mapNotNull null
                        val title = a.attr("title").ifEmpty { article.selectFirst("h2")?.text() ?: "" }.trim()
                        val href = a.attr("href").trim()
                        val poster = article.selectFirst("img")?.let { it.attr("data-src").ifEmpty { it.attr("src") } } ?: ""
                        if (title.isNotEmpty() && href.isNotEmpty()) {
                            AnimeEntry(title, poster, SITE_DHS, href)
                        } else null
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }

            val akDeferred = async {
                try {
                    val statusParam = if (order == "popular") "" else "ongoing"
                    val url = "https://animekhor.org/anime/?status=$statusParam&type=&order=$order&page=$page"
                    val doc = app.get(url).document
                    doc.select("div.listupd > article").mapNotNull { article ->
                        val a = article.selectFirst(".bsx > a") ?: return@mapNotNull null
                        val title = a.attr("title").ifEmpty { article.selectFirst("h2")?.text() ?: "" }.trim()
                        val href = a.attr("href").trim()
                        val poster = article.selectFirst("img")?.let { it.attr("data-src").ifEmpty { it.attr("src") } } ?: ""
                        if (title.isNotEmpty() && href.isNotEmpty()) {
                            AnimeEntry(title, poster, SITE_AK, href)
                        } else null
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }

            val seaTvDeferred = async {
                try {
                    val url = "https://donghuafun.com/api.php/provide/vod/at/json?ac=videolist&pg=$page"
                    val json = JSONObject(app.get(url).text)
                    val arr = json.optJSONArray("list") ?: return@async emptyList()
                    List(arr.length()) { i ->
                        val obj = arr.getJSONObject(i)
                        val title = obj.optString("vod_name").trim()
                        val id = obj.optInt("vod_id", 0).toString()
                        val poster = obj.optString("vod_pic")
                        if (title.isNotEmpty() && id != "0") {
                            AnimeEntry(title, poster, SITE_SEATV, id)
                        } else null
                    }.filterNotNull()
                } catch (_: Exception) {
                    emptyList()
                }
            }

            awaitAll(dhsDeferred, akDeferred, seaTvDeferred).flatten()
        }

        val merged = mergeEntries(items).map { it.toSearchResponse() }
        return newHomePageResponse(listOf(HomePageList(request.name, merged)), hasNext = merged.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val items = supervisorScope {
            val dhsDeferred = async {
                try {
                    val doc = app.get("https://donghuastream.org/?s=${query.encodeUrl()}").document
                    doc.select("div.listupd > article").mapNotNull { article ->
                        val a = article.selectFirst(".bsx > a") ?: return@mapNotNull null
                        val title = a.attr("title").ifEmpty { article.selectFirst("h2")?.text() ?: "" }.trim()
                        val href = a.attr("href").trim()
                        val poster = article.selectFirst("img")?.let { it.attr("data-src").ifEmpty { it.attr("src") } } ?: ""
                        if (title.isNotEmpty() && href.isNotEmpty()) {
                            AnimeEntry(title, poster, SITE_DHS, href)
                        } else null
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }

            val akDeferred = async {
                try {
                    val doc = app.get("https://animekhor.org/page/1/?s=${query.encodeUrl()}").document
                    doc.select("div.listupd > article").mapNotNull { article ->
                        val a = article.selectFirst(".bsx > a") ?: return@mapNotNull null
                        val title = a.attr("title").ifEmpty { article.selectFirst("h2")?.text() ?: "" }.trim()
                        val href = a.attr("href").trim()
                        val poster = article.selectFirst("img")?.let { it.attr("data-src").ifEmpty { it.attr("src") } } ?: ""
                        if (title.isNotEmpty() && href.isNotEmpty()) {
                            AnimeEntry(title, poster, SITE_AK, href)
                        } else null
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }

            val seaTvDeferred = async {
                try {
                    SeaTV().search(query).map { sr ->
                        AnimeEntry(sr.name, sr.posterUrl ?: "", SITE_SEATV, sr.url)
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }

            awaitAll(dhsDeferred, akDeferred, seaTvDeferred).flatten()
        }

        return mergeEntries(items).map { it.toSearchResponse() }
    }

    private data class AnimeEntry(
        val title: String,
        val poster: String,
        val site: String,
        val urlOrId: String,
    )

    private data class MergedAnime(
        val displayName: String,
        val poster: String?,
        val siteMap: Map<String, String>, // Site -> urlOrId
    )

    private fun mergeEntries(items: List<AnimeEntry>): List<MergedAnime> {
        val result = mutableListOf<MergedAnime>()
        val groups = items.groupBy { normTitle(it.title) }
        for ((_, group) in groups) {
            val first = group.first()
            val poster = group.firstNotNullOfOrNull { it.poster.takeIf { p -> p.isNotBlank() } }
            val map = group.associate { it.site to it.urlOrId }
            result.add(MergedAnime(first.title, poster, map))
        }
        return result
    }

    private fun MergedAnime.toSearchResponse(): SearchResponse {
        val jsonUrl = JSONObject(siteMap).toString()
        return newTvSeriesSearchResponse(displayName, jsonUrl, TvType.Anime, fix = false) {
            this.posterUrl = this@toSearchResponse.poster
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val siteMap: Map<String, String> = try {
            val obj = JSONObject(url)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        } catch (_: Exception) {
            // 兼容直接传入单个站点 URL
            mapOf(SITE_DHS to url)
        }

        var title = ""
        var poster: String? = null
        var plot: String? = null
        var year: Int? = null

        // 并行拉取各站的剧集列表
        val siteEpisodes = supervisorScope {
            val dhsDeferred = async {
                val dhsUrl = siteMap[SITE_DHS] ?: return@async emptyList<Triple<Int, String, String>>()
                try {
                    val doc = app.get(dhsUrl).document
                    if (title.isEmpty()) title = doc.selectFirst("h1.entry-title")?.text()?.trim() ?: ""
                    if (poster.isNullOrEmpty()) poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
                    if (plot.isNullOrEmpty()) plot = doc.selectFirst("div.entry-content")?.text()?.trim()

                    // 直接从番剧主页的 .eplister 提取所有集数（无需二次请求 Eppage）
                    doc.select("div.eplister li").mapNotNull { li ->
                        val a = li.selectFirst("a") ?: return@mapNotNull null
                        val epHref = a.attr("href").trim()
                        if (epHref.isEmpty()) return@mapNotNull null
                        val numText = li.selectFirst(".epl-num")?.text()?.trim() ?: ""
                        val titleText = li.selectFirst(".epl-title")?.text()?.trim() ?: a.attr("title").trim()
                        val epNum = extractEpNum(numText, titleText, epHref) ?: return@mapNotNull null
                        Triple(epNum, titleText.ifEmpty { "Episode $epNum" }, "$SITE_DHS$$epHref")
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }

            val akDeferred = async {
                val akUrl = siteMap[SITE_AK] ?: return@async emptyList<Triple<Int, String, String>>()
                try {
                    val doc = app.get(akUrl).document
                    if (title.isEmpty()) title = doc.selectFirst("h1.entry-title")?.text()?.trim() ?: ""
                    if (poster.isNullOrEmpty()) poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
                    if (plot.isNullOrEmpty()) plot = doc.selectFirst("div.entry-content")?.text()?.trim()

                    doc.select("div.eplister li").mapNotNull { li ->
                        val a = li.selectFirst("a") ?: return@mapNotNull null
                        val epHref = a.attr("href").trim()
                        if (epHref.isEmpty()) return@mapNotNull null
                        val numText = li.selectFirst(".epl-num")?.text()?.trim() ?: ""
                        val titleText = li.selectFirst(".epl-title")?.text()?.trim() ?: a.attr("title").trim()
                        val epNum = extractEpNum(numText, titleText, epHref) ?: return@mapNotNull null
                        Triple(epNum, titleText.ifEmpty { "Episode $epNum" }, "$SITE_AK$$epHref")
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }

            val seaTvDeferred = async {
                val rawUrl = siteMap[SITE_SEATV] ?: return@async emptyList<Triple<Int, String, String>>()
                try {
                    val id = rawUrl.substringAfterLast("/").substringBefore(".html").filter { it.isDigit() }.ifEmpty { rawUrl.filter { it.isDigit() } }
                    if (id.isEmpty()) return@async emptyList()
                    val apiUrl = "https://donghuafun.com/api.php/provide/vod/at/json?ac=detail&ids=$id"
                    val json = JSONObject(app.get(apiUrl).text)
                    val arr = json.optJSONArray("list") ?: return@async emptyList()
                    if (arr.length() == 0) return@async emptyList()
                    val vod = arr.getJSONObject(0)

                    if (title.isEmpty()) title = vod.optString("vod_name").trim()
                    if (poster.isNullOrEmpty()) poster = vod.optString("vod_pic")
                    if (plot.isNullOrEmpty()) plot = vod.optString("vod_content").replace(Regex("<[^>]*>"), "").trim()
                    if (year == null) year = vod.optInt("vod_year", 0).takeIf { it > 0 }

                    val fromList = vod.optString("vod_play_from").split("$$$")
                    val urlList = vod.optString("vod_play_url").split("$$$")

                    val result = mutableListOf<Triple<Int, String, String>>()
                    for (li in fromList.indices) {
                        if (li >= urlList.size) break
                        val lineName = fromList[li].trim().ifEmpty { "线路${li + 1}" }
                        val eps = urlList[li].split("#")
                        for (epStr in eps) {
                            val parts = epStr.split("$", limit = 2)
                            if (parts.size == 2 && parts[1].isNotBlank()) {
                                val epName = parts[0].trim()
                                val epData = parts[1].trim()
                                val epNum = extractEpNum(epName) ?: continue
                                result.add(Triple(epNum, epName, "$SITE_SEATV@$lineName\$$epData"))
                            }
                        }
                    }
                    result
                } catch (_: Exception) {
                    emptyList()
                }
            }

            awaitAll(dhsDeferred, akDeferred, seaTvDeferred).flatten()
        }

        if (siteEpisodes.isEmpty()) throw ErrorLoadingException("未找到可播放剧集")

        // 按真实集数分组对齐合并各站数据
        val allNums = siteEpisodes.map { it.first }.toSortedSet()
        val episodes = allNums.map { num ->
            val matching = siteEpisodes.filter { it.first == num }
            val epName = matching.firstOrNull()?.second ?: "第 $num 集"
            // 每集的 data 为多站、多线路的数据拼合串（例如 "Donghuastream$url#Animekhor$url#SeaTV@线路$data"）
            val combinedData = matching.joinToString("#") { it.third }

            newEpisode(combinedData, fix = false, initializer = {
                this.name = if (epName.startsWith("EP", ignoreCase = true) || epName.startsWith("Episode", ignoreCase = true)) {
                    "第 $num 集"
                } else {
                    epName
                }
                this.episode = num
            })
        }.sortedBy { it.episode ?: Int.MAX_VALUE }

        return newTvSeriesLoadResponse(title.ifEmpty { "国漫详情" }, url, TvType.Anime, episodes) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        coroutineScope {
            data.split("#").forEach { part ->
                launch {
                    try {
                        when {
                            part.startsWith("$SITE_DHS$") -> {
                                val epUrl = part.substringAfter("$SITE_DHS$").trim()
                                if (epUrl.isNotEmpty()) {
                                    Donghuastream().loadLinks(epUrl, isCasting, subtitleCallback, callback)
                                }
                            }
                            part.startsWith("$SITE_AK$") -> {
                                val epUrl = part.substringAfter("$SITE_AK$").trim()
                                if (epUrl.isNotEmpty()) {
                                    Animekhor().loadLinks(epUrl, isCasting, subtitleCallback, callback)
                                }
                            }
                            part.startsWith("$SITE_SEATV@") -> {
                                val lineAndData = part.substringAfter("$SITE_SEATV@").trim()
                                if (lineAndData.isNotEmpty()) {
                                    SeaTV().loadLinks(lineAndData, isCasting, subtitleCallback, callback)
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // 单个线路解析失败不影响其他线路
                    }
                }
            }
        }
        return true
    }
}
