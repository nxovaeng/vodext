package com.Donghuastream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
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

        // 解析每条线路的剧集：(线路名, [(集号, 集名, 数据)])
        // 数据可能是 dailymotion 视频 ID、rumble/ganjing 视频 ID，或直接 URL
        val lineEpisodes = fromList.indices.mapNotNull { li ->
            if (li >= urlList.size) return@mapNotNull null
            val lineName = fromList[li].trim().ifEmpty { "线路${li + 1}" }
            val eps = urlList[li].split("#").mapNotNull { epStr ->
                val parts = epStr.split("$", limit = 2)
                if (parts.size == 2 && parts[1].isNotBlank()) {
                    val epName = parts[0].trim()
                    val epData = parts[1].trim()
                    // 从 "EP07" 解析真实集号（源站是倒序的）；解析失败先记 null，后面用索引补
                    val epNum = Regex("""EP\s*0*(\d+)""", RegexOption.IGNORE_CASE)
                        .find(epName)?.groupValues?.get(1)?.toIntOrNull()
                    Triple(epNum, epName, epData)
                } else null
            }.mapIndexed { index, (num, name, data) ->
                Triple(num ?: (index + 1), name.ifEmpty { "EP${num ?: (index + 1)}" }, data)
            }
            if (eps.isEmpty()) null else lineName to eps
        }

        // 按集号对齐合并各线路；每集 data 为 "线路名$数据#..."，loadLinks 拆分后即多播放源
        val allNums = lineEpisodes.flatMap { (_, eps) -> eps.map { it.first } }.toSortedSet()
        val episodes = allNums.map { num ->
            val epName = lineEpisodes.firstNotNullOfOrNull { (_, eps) ->
                eps.find { it.first == num }?.second
            } ?: "EP$num"
            val data = lineEpisodes.mapNotNull { (lineName, eps) ->
                eps.find { it.first == num }?.third?.let { epData ->
                    "$lineName\$$epData"
                }
            }.joinToString("#")
            // fix = false：data 是 "线路$数据#..." 协议串，不是 URL
            newEpisode(data, fix = false, initializer = {
                this.name = epName
                this.episode = num
            })
        }.sortedBy { it.episode ?: Int.MAX_VALUE }

        return newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
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
        // data 为 "线路名$数据#..."，拆分后每条线路即一个播放源
        data.split("#").forEach { part ->
            try {
                val lineName = part.substringBefore("$").trim()
                val rawData = part.substringAfter("$").trim()
                if (lineName.isEmpty() || rawData.isEmpty()) return@forEach

                when {
                    // dailymotion 线路：rawData 是视频 ID；
                    // 防御性地去掉旧构建 fixUrl 可能加上的 url 前缀
                    lineName.contains("dailymotion", ignoreCase = true) -> {
                        val videoId = rawData.substringAfterLast("/").trim()
                        if (videoId.isNotEmpty()) {
                            loadExtractor(
                                "https://www.dailymotion.com/video/$videoId",
                                referer = mainUrl,
                                subtitleCallback, callback
                            )
                        }
                    }
                    // 直接 URL（rumble m3u8 等），保持完整
                    rawData.startsWith("http") -> {
                        if (rawData.contains(".m3u8")) {
                            // source 传 provider 名（播放器靠它找回 provider 取拦截器）
                            M3u8Helper.generateM3u8(
                                name, rawData, mainUrl,
                                name = "$name · $lineName"
                            ).forEach(callback)
                        } else {
                            loadExtractor(rawData, referer = mainUrl, subtitleCallback, callback)
                        }
                    }
                    // rumble 视频 ID
                    lineName.contains("rumble", ignoreCase = true) -> {
                        loadExtractor(
                            "https://rumble.com/$rawData",
                            referer = mainUrl,
                            subtitleCallback, callback
                        )
                    }
                    // ganjing 等其他 ID 暂不支持，跳过
                    else -> {}
                }
            } catch (_: Exception) {
            }
        }
        return true
    }
}
