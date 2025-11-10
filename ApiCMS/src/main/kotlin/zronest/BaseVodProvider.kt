package nxovaeng

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
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URLEncoder

/** 基于 api 类型采集站点提供者（例如 bfzyapi.com） 基于 JSON 的接口返回 list -> media */
open class BaseVodProvider : MainAPI() {

    override var mainUrl = "https://jszyapi.com"
    override var name = "极速资源"
    override var lang = "zh"

    override val hasMainPage = true

    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    override val mainPage =
        mainPageOf(
            "vod/?ac=list" to "最新更新",
            "vod/?ac=list&t=2" to "电影",
            "vod/?ac=list&t=1" to "电视剧",
            "vod/?ac=list&t=17" to "动漫"
        )


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // 调用 api json 风格的 list 接口
        val url = "$mainUrl/api.php/provide/${request.data}&pg=$page"
        val api = app.get(url).parsed<ApiResponse>()

        val ids = api.list.joinToString(",") { it.vod_id.toString() }

        val detailUri = "$mainUrl/api.php/provide/vod/?ac=detail&ids=$ids"

        // 批量请求详情, 获取封面地址
        val detailApi =
            app.get(detailUri).parsed<MediaDetail>()

        val list = detailApi.list.mapNotNull { it.toSearchResponse() }

        return newHomePageResponse(listOf(HomePageList(request.name, list)), hasNext = true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$mainUrl/api.php/provide/vod/?ac=search&wd=$encoded"
        val api = app.get(url).parsed<ApiResponse>()

        val ids = api.list.joinToString(",") { it.vod_id.toString() }
        val detailUrl = "$mainUrl/api.php/provide/vod/?ac=detail&ids=$ids"

        // 批量请求详情, 获取封面及详情介绍
        val detailApi = app.get(detailUrl).parsed<MediaDetail>()
        return detailApi.list.mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.split("/").last()
        val url = "$mainUrl/api.php/provide/vod/?ac=detail&ids=$id"
        val detail =
            app.get(url).parsed<MediaDetail>()

        val media = detail.list.first()

        return media.toLoadResponse(url)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // data usually contains playUrl or m3u8
        if (data.contains(".m3u8")) {
            M3u8Helper.generateM3u8(name, data, mainUrl).forEach(callback)
            return true
        }

        // fallback: try loadExtractor
        loadExtractor(
            data,
            referer = mainUrl,
            subtitleCallback = subtitleCallback,
            callback = callback
        )
        return true
    }


    // Minimal API models for list
    private data class ApiResponse(
        val code: Int = 0,
        val msg: String = "",
        val page: Int = 1,
        val pagecount: Int = 1,
        val limit: Int = 20,
        val total: Int = 0,
        val list: List<MediaInfo> = emptyList()
    )

    private data class MediaInfo(
        val vod_id: Int = 0,
        val vod_name: String = "",
        val type_id: Int = 0,
        val type_name: String = "",
        val vod_en: String = "",
        val vod_time: String = "",
        val vod_remarks: String = "",
        val vod_play_from: String = "",
    )

    data class MediaDetail(
        val code: Int,
        val msg: String,
        val page: Int,
        val pagecount: Int,
        val limit: Int,
        val total: Int,
        val list: List<MediaDetailVideo>
    )

    data class MediaDetailVideo(
        val vod_id: Int,
        val type_id: Int,
        val type_id_1: Int,
        val group_id: Int,
        val vod_name: String,
        val vod_sub: String?,
        val vod_en: String?,
        val vod_status: Int,
        val vod_letter: String?,
        val vod_class: String?,
        val vod_pic: String?, // 封面图
        val vod_actor: String?,
        val vod_director: String?,
        val vod_blurb: String?,
        val vod_remarks: String?,
        val vod_area: String?,
        val vod_lang: String?,
        val vod_year: String?,
        val vod_content: String?, // 简介
        val vod_play_from: String?,
        val vod_play_url: String?, // 播放地址列表
        val type_name: String = ""
    )

    suspend fun MediaDetailVideo.toSearchResponse(): SearchResponse? {
        val type = mapTypeByName(type_name)

        return if (type == TvType.Movie) {
            this@BaseVodProvider.newMovieSearchResponse(vod_name, vod_id.toString(), type) {
                posterUrl = vod_pic
            }
        } else {
            this@BaseVodProvider.newTvSeriesSearchResponse(vod_name, vod_id.toString(), type) {
                posterUrl = vod_pic
            }
        }
    }


    suspend fun MediaDetailVideo.toLoadResponse(url: String): LoadResponse {
        val type = mapTypeByName(type_name)

        return if (type == TvType.Movie) {
            this@BaseVodProvider.newMovieLoadResponse(
                vod_name,
                url,
                TvType.Movie,
                vod_play_url ?: ""
            ) {
                this.posterUrl = vod_pic
                this.plot = vod_content
                this.year = vod_year?.toIntOrNull()
                this.tags = vod_class?.split(",")
            }
        } else {
            val episodes = getEpisodes().values.flatten()
            this@BaseVodProvider.newTvSeriesLoadResponse(vod_name, url, type, episodes) {
                this.posterUrl = vod_pic
                this.plot = vod_content
                this.year = vod_year?.toIntOrNull()
                this.tags = vod_class?.split(",")
            }
        }
    }

    fun MediaDetailVideo.getEpisodes(): Map<String, List<Episode>> {
        if (vod_play_url.isNullOrEmpty()) return emptyMap()

        // 播放源名称，比如 "bfzym3u8" 或 "modum3u8"
        val source = vod_play_from ?: "默认线路"

        // 每条地址用 # 分隔
        val playUrls = vod_play_url.split("#")

        val episodes =
            playUrls.mapIndexed { index, item ->
                val parts = item.split("$", limit = 2)
                val name = parts.getOrNull(0) ?: "第 ${index + 1} 集"
                val url = parts.getOrNull(1) ?: parts.getOrNull(0) ?: ""
                this@BaseVodProvider.newEpisode(url) {
                    this.name = name.ifEmpty { "第 ${index + 1} 集" }
                    this.episode = index + 1
                }
            }

        // 返回 Map，key 是线路名，value 是该线路的所有 Episode
        return mapOf(source to episodes)
    }


    data class KeywordRule(val keyword: String, val type: TvType, val priority: Int)

    val baseVodProviderKeywordRules = listOf(
        KeywordRule("动漫电影", TvType.AnimeMovie, 100),
        KeywordRule("电影", TvType.Movie, 90),
        KeywordRule("短剧", TvType.CustomMedia, 85),
        KeywordRule("剧", TvType.TvSeries, 80),
        KeywordRule("动漫", TvType.Anime, 70),
        KeywordRule("动画", TvType.Cartoon, 70),
        KeywordRule("OVA", TvType.OVA, 70),
        KeywordRule("纪录", TvType.Documentary, 60),
        KeywordRule("综艺", TvType.Cartoon, 60),
        KeywordRule("韩剧", TvType.AsianDrama, 60),
        KeywordRule("日剧", TvType.AsianDrama, 60),
        KeywordRule("国产剧", TvType.AsianDrama, 60),
        KeywordRule("音乐", TvType.Music, 50),
        KeywordRule("有声书", TvType.AudioBook, 50),
        KeywordRule("播客", TvType.Podcast, 50),
        KeywordRule("直播", TvType.Live, 50),
        KeywordRule("磁力", TvType.Torrent, 50),
        KeywordRule("音频", TvType.Audio, 50),
        KeywordRule("自定义", TvType.CustomMedia, 40),
        KeywordRule("NSFW", TvType.NSFW, 40),
        KeywordRule("福利", TvType.NSFW, 40),
        KeywordRule("预告片", TvType.Others, 30),
        KeywordRule("片", TvType.Movie, 20),
    )

    fun mapTypeByName(typeName: String): TvType {
        val matches = baseVodProviderKeywordRules.filter { rule ->
            typeName.contains(rule.keyword, ignoreCase = true)
        }
        return matches.maxByOrNull { it.priority }?.type ?: TvType.Others
    }

}