package com.Donghuastream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
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
        val title = getString("vod_name")
        val id = getInt("vod_id").toString()
        val poster = getString("vod_pic")
        return newTvSeriesSearchResponse(title, id, TvType.Anime) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/api.php/provide/vod/at/json?ac=videolist&wd=$query"
        val response = app.get(url).text
        val json = JSONObject(response)
        val list = json.optJSONArray("list") ?: return emptyList()
        val results = mutableListOf<SearchResponse>()
        for (i in 0 until list.length()) {
            val item = list.getJSONObject(i)
            results.add(item.toSearchResponse())
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val id = if (url.startsWith("http")) {
            url.substringAfter("/id/").substringBefore(".html")
        } else {
            url
        }
        val apiUrl = "$mainUrl/api.php/provide/vod/at/json?ac=detail&ids=$id"
        val response = app.get(apiUrl).text
        val json = JSONObject(response)
        val vod = json.getJSONArray("list").getJSONObject(0)
        
        val title = vod.getString("vod_name")
        val poster = vod.getString("vod_pic")
        val plot = vod.optString("vod_content").replace(Regex("<[^>]*>"), "").trim()
        val year = vod.optInt("vod_year", 0).takeIf { it > 0 }

        val fromList = vod.getString("vod_play_from").split("$$$")
        val urlList = vod.getString("vod_play_url").split("$$$")
        
        val episodes = mutableListOf<Episode>()
        
        // Find "dailymotion" index
        val dmIndex = fromList.indexOf("dailymotion")
        if (dmIndex != -1) {
            val dmUrls = urlList[dmIndex].split("#")
            dmUrls.forEachIndexed { index, epStr ->
                val epParts = epStr.split("$")
                if (epParts.size == 2) {
                    val epName = epParts[0]
                    val epData = epParts[1]
                    episodes.add(newEpisode(epData) {
                        this.name = epName
                        this.episode = index + 1
                    })
                }
            }
        }

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
        // data is the dailymotion video ID
        val url = "https://www.dailymotion.com/video/$data"
        loadExtractor(url, referer = mainUrl, subtitleCallback, callback)
        return true
    }
}
