package nxovaeng

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

/** 瓜子影视（App 私有协议，移植自 cluntop/tvbox py/guazi.py，基于 CatVodSpider 规范） */
class GuaziProvider : MainAPI() {
    override var mainUrl = "https://apinew.uozvr.com"
    override var name = "瓜子影视"
    override var lang = "zh"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    private val api = GuaziApi()

    // type_id: 电影=1 电视剧=2 动漫=4 综艺=3 短剧=64
    override val mainPage = mainPageOf(
        "1" to "电影",
        "2" to "电视剧",
        "4" to "动漫",
        "3" to "综艺",
        "64" to "短剧",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = api.request(
            mapOf(
                "area" to "0",
                "year" to "0",
                "pageSize" to "30",
                "sort" to "d_id",
                "page" to page.toString(),
                "tid" to request.data
            ),
            "/App/IndexList/indexList"
        )
        val list = data?.getAsJsonArray("list")?.mapNotNull { item ->
            val obj = item.asJsonObject
            val vodId = obj.get("vod_id")?.asString ?: return@mapNotNull null
            val continu = obj.get("vod_continu")?.asString?.toIntOrNull() ?: 0
            val name = obj.get("vod_name")?.asString ?: ""
            val pic = obj.get("vod_pic")?.asString ?: ""
            // id 拼 continu，load 时拆分（沿用 py 版格式）
            val id = "$vodId/$continu"
            if (request.data == "1") {
                newMovieSearchResponse(name, id, TvType.Movie, fix = false) {
                    this.posterUrl = pic
                }
            } else {
                newTvSeriesSearchResponse(name, id, TvType.TvSeries, fix = false) {
                    this.posterUrl = pic
                }
            }
        } ?: emptyList()

        return newHomePageResponse(
            listOf(HomePageList(request.name, list)),
            hasNext = list.size >= 30
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val data = api.request(
            mapOf("keywords" to query, "order_val" to "1", "page" to "1"),
            "/App/Index/findMoreVod"
        )
        return data?.getAsJsonArray("list")?.mapNotNull { item ->
            val obj = item.asJsonObject
            val vodId = obj.get("vod_id")?.asString ?: return@mapNotNull null
            val continu = obj.get("vod_continu")?.asString?.toIntOrNull() ?: 0
            val name = obj.get("vod_name")?.asString ?: ""
            val pic = obj.get("vod_pic")?.asString ?: ""
            val tid = obj.get("t_id")?.asString ?: ""
            val id = "$vodId/$continu"

            val type = when (tid) {
                "1" -> TvType.Movie
                "4" -> TvType.Anime
                else -> if (continu == 0) TvType.Movie else TvType.TvSeries
            }

            if (type == TvType.Movie) {
                newMovieSearchResponse(name, id, TvType.Movie, fix = false) {
                    this.posterUrl = pic
                }
            } else {
                newTvSeriesSearchResponse(name, id, type, fix = false) {
                    this.posterUrl = pic
                }
            }
        } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        // 兼容 "155204/0" 以及被 Cloudstream fixUrl 处理过的 "https://apinew.uozvr.com/155204/0"
        val cleanPath = if (url.contains("://")) url.substringAfter("://").substringAfter("/") else url
        val segments = cleanPath.trim('/').split("/")
        val vodId = segments.firstOrNull { it.isNotEmpty() && it.all(Char::isDigit) }
            ?: segments.firstOrNull() ?: url.substringBefore("/")
        val urlContinu = segments.getOrNull(1)?.toIntOrNull() ?: 0
        val t = (System.currentTimeMillis() / 1000).toString()
        val auth = api.authParams()

        // 1. 详情
        val qdata = api.request(
            mapOf(
                "token_id" to (auth["token_id"] ?: ""),
                "vod_id" to vodId,
                "mobile_time" to t,
                "token" to (auth["token"] ?: "")
            ),
            "/App/IndexPlay/playInfo"
        )
        // 2. 播放源
        val jdata = api.request(
            mapOf("vurl_cloud_id" to "2", "vod_d_id" to vodId),
            "/App/Resource/Vurl/show"
        )

        val vod = qdata?.getAsJsonObject("vodInfo")
            ?: throw Exception("获取详情失败")
        val name = vod.get("vod_name")?.asString ?: ""
        val pic = vod.get("vod_pic")?.asString
        // 修复：服务端返回格式如 "2026-10-13"，直接 asInt 会导致 NumberFormatException 崩溃
        val year = vod.get("vod_year")?.asString?.substringBefore("-")?.toIntOrNull()
        val plot = vod.get("vod_use_content")?.asString?.trim()
        val actorStr = vod.get("vod_actor")?.asString
        val actors = actorStr?.split(",")?.mapNotNull {
            val a = it.trim()
            if (a.isNotEmpty()) ActorData(Actor(a)) else null
        } ?: emptyList()
        val area = vod.get("vod_area")?.asString?.trim()
        val tags = listOfNotNull(area?.takeIf { it.isNotEmpty() })
        val tid = vod.get("t_id")?.asString ?: ""

        // 解析播放列表：每集 data = "param||res1@res2"（py 版格式）
        val episodes = mutableListOf<Episode>()
        val listArray = jdata?.getAsJsonArray("list")
        val isSingleEpisode = listArray?.size() == 1

        listArray?.forEachIndexed { index, item ->
            val playMap = item.asJsonObject.getAsJsonObject("play") ?: return@forEachIndexed
            val names = mutableListOf<String>()
            val params = mutableListOf<String>()
            for ((k, v) in playMap.entrySet()) {
                val param = v.asJsonObject.get("param")?.asString
                if (!param.isNullOrEmpty()) {
                    names.add(k)
                    params.add(param)
                }
            }
            if (params.isNotEmpty()) {
                val playName = if (isSingleEpisode) name else (index + 1).toString()
                // data 格式：param||res1@res2，loadLinks 时解析
                val data = "${params.last()}||${names.joinToString("@")}"
                episodes.add(newEpisode(data) {
                    this.name = playName
                    this.episode = index + 1
                })
            }
        }

        val isMovie = (tid == "1" || (urlContinu == 0 && episodes.size <= 1))
        val tvType = when (tid) {
            "1" -> TvType.Movie
            "4" -> TvType.Anime
            else -> if (isMovie) TvType.Movie else TvType.TvSeries
        }

        return if (isMovie) {
            newMovieLoadResponse(name, url, TvType.Movie, episodes.firstOrNull()?.data ?: "") {
                this.posterUrl = pic
                this.year = year
                this.plot = plot
                this.actors = actors
                this.tags = tags
            }
        } else {
            newTvSeriesLoadResponse(name, url, tvType, episodes) {
                this.posterUrl = pic
                this.year = year
                this.plot = plot
                this.actors = actors
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // data 格式：param||res1@res2
        val parts = data.split("||")
        if (parts.size < 2) return false
        val paramStr = parts[0]
        val resolutions = parts[1].split("@")

        // 解析 param 为 map，取最高清晰度
        val params = paramStr.split("&").mapNotNull {
            val kv = it.split("=", limit = 2)
            if (kv.size == 2) kv[0] to kv[1] else null
        }.toMap().toMutableMap()
        val bestResolution = resolutions
            .mapNotNull { it.toIntOrNull() }
            .maxOrNull()?.toString()
            ?: resolutions.firstOrNull()
        if (bestResolution != null) {
            params["resolution"] = bestResolution
        }

        val result = api.request(params, "/App/Resource/VurlDetail/showOne")
        val playUrl = result?.get("url")?.asString ?: return false
        if (playUrl.isBlank()) return false

        val quality = when (bestResolution) {
            "1080" -> Qualities.P1080.value
            "720" -> Qualities.P720.value
            "480" -> Qualities.P480.value
            else -> Qualities.Unknown.value
        }

        // py 原版 playerContent 返回的固定 header，CDN 校验用
        // 不走 M3u8Helper（其内部取 m3u8 时 header 不可控），直接发 ExtractorLink 让播放器处理
        val headers = mapOf(
            "User-Agent" to "Lavf/57.83.100",
            "Referer" to "http://WJiZxLXA2.com/"
        )
        callback(
            newExtractorLink(name, name, playUrl) {
                this.referer = "http://WJiZxLXA2.com/"
                this.headers = headers
                this.quality = quality
                this.type = if (playUrl.contains(".m3u8")) ExtractorLinkType.M3U8
                            else ExtractorLinkType.VIDEO
            }
        )
        return true
    }
}
