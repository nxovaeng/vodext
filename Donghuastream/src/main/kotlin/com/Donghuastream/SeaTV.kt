package com.Donghuastream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.StringUtils.encodeUrl
import com.lagradost.cloudstream3.utils.loadExtractor
import org.json.JSONObject

open class SeaTV : MainAPI() {
    override var mainUrl = "https://donghuafun.com"
    override var name = "SeaTV"
    override val hasMainPage = true
    override var lang = "zh"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        "20" to "Donghua",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl/api.php/provide/vod/at/json?ac=videolist&t=${request.data}&pg=$page"
        val response = app.get(url).text
        val json = JSONObject(response)
        val list = json.getJSONArray("list")
        val home = mutableListOf<SearchResponse>()
        for (i in 0 until list.length()) {
            val item = list.getJSONObject(i)
            home.add(item.toSearchResponse())
        }
        return newHomePageResponse(
            list = HomePageList(name = request.name, list = home, isHorizontalImages = false),
            hasNext = json.getInt("page") < json.getInt("pagecount")
        )
    }

    private fun JSONObject.toSearchResponse(): SearchResponse {
        val title = optString("vod_name")
        val id = optInt("vod_id", 0).toString()
        val poster = optString("vod_pic")
        return newTvSeriesSearchResponse(title, id, TvType.Anime) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // 1. 先走 API 搜索（英文有效，JSON 快）
        val apiResults = searchApi(query)
        if (apiResults.isNotEmpty()) return apiResults

        // 2. API 不支持中文搜索，回退到网页搜索
        //    /index.php/vod/search/wd/关键词.html 服务端支持中文
        return searchHtml(query)
    }

    private suspend fun searchApi(query: String): List<SearchResponse> {
        return try {
            val url = "$mainUrl/api.php/provide/vod/at/json?ac=videolist&wd=${query.encodeUrl()}"
            val json = JSONObject(app.get(url).text)
            val list = json.optJSONArray("list") ?: return emptyList()
            List(list.length()) { i -> list.getJSONObject(i).toSearchResponse() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun searchHtml(query: String): List<SearchResponse> {
        return try {
            // Ktor 的 encodeURLParameter（跨平台），空格编为 %20，path 里可直接用
            val encoded = query.trim().encodeUrl()
            val doc = app.get("$mainUrl/index.php/vod/search/wd/$encoded.html").document
            doc.select("div.public-list-box").mapNotNull { box ->
                val a = box.selectFirst("a.public-list-exp") ?: return@mapNotNull null
                val title = a.attr("title").ifEmpty {
                    box.selectFirst("a.time-title")?.attr("title") ?: ""
                }.trim()
                val href = fixUrl(a.attr("href"))
                if (title.isEmpty() || href.isEmpty()) return@mapNotNull null
                val poster = fixUrlNull(
                    a.selectFirst("img")?.let { img ->
                        img.attr("data-src").ifEmpty { img.attr("src") }
                    }
                )
                newTvSeriesSearchResponse(title, href, TvType.Anime) {
                    this.posterUrl = poster
                }
            }.distinctBy { it.url }.take(50)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        // url 可能是 "245" / "https://donghuafun.com/245" / ".../id/245.html"
        // 旧的 substringAfter("/id/") 永远匹配不到，导致 ids 参数错误、detail 返回空列表
        val id = url.substringAfterLast("/")
            .substringBefore(".html")
            .filter { it.isDigit() }
            .ifEmpty { url.filter { it.isDigit() } }
        if (id.isEmpty()) throw ErrorLoadingException("invalid url: $url")

        val apiUrl = "$mainUrl/api.php/provide/vod/at/json?ac=detail&ids=$id"
        val response = app.get(apiUrl).text
        val json = JSONObject(response)
        val arr = json.optJSONArray("list")
        if (arr == null || arr.length() == 0) throw ErrorLoadingException("vod not found: $id")
        val vod = arr.getJSONObject(0)

        val title = vod.optString("vod_name")
        val poster = vod.optString("vod_pic")
        val plot = vod.optString("vod_content").replace(Regex("<[^>]*>"), "").trim()
        val year = vod.optInt("vod_year", 0).takeIf { it > 0 }

        val fromList = vod.optString("vod_play_from").split("$$$")
        val urlList = vod.optString("vod_play_url").split("$$$")

        // 优先 dailymotion 线路（集数最全）；没有则退回集数最多的线路
        var srcIndex = fromList.indexOfFirst { it.contains("dailymotion", ignoreCase = true) }
        if (srcIndex == -1 || srcIndex >= urlList.size) {
            srcIndex = urlList.indices.maxByOrNull { urlList[it].split("#").size } ?: -1
        }

        val episodes = mutableListOf<Episode>()
        if (srcIndex != -1) {
            urlList[srcIndex].split("#").forEachIndexed { index, epStr ->
                val epParts = epStr.split("$")
                if (epParts.size == 2 && epParts[1].isNotBlank()) {
                    val epName = epParts[0].trim()
                    val epData = epParts[1].trim()
                    // 从 "EP07" 解析真实集号（源站是倒序的）；解析失败则用索引
                    val epNum = Regex("""EP\s*0*(\d+)""", RegexOption.IGNORE_CASE)
                        .find(epName)?.groupValues?.get(1)?.toIntOrNull()
                        ?: (index + 1)
                    // fix = false：epData 是 dailymotion 视频 ID，不是 URL，不能被 fixUrl 加前缀
                    episodes.add(newEpisode(epData, fix = false) {
                        this.name = epName.ifEmpty { "EP$epNum" }
                        this.episode = epNum
                    })
                }
            }
        }
        // 按集号升序
        val sorted = episodes.sortedBy { it.episode ?: Int.MAX_VALUE }

        return newTvSeriesLoadResponse(title, url, TvType.Anime, sorted) {
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
        // data 是 dailymotion 视频 ID；防御性地去掉可能带上的 url 前缀（旧构建 fixUrl 加的）
        val videoId = data.substringAfterLast("/").trim()
        if (videoId.isEmpty()) return false
        val url = "https://www.dailymotion.com/video/$videoId"
        loadExtractor(url, referer = mainUrl, subtitleCallback, callback)
        return true
    }
}
