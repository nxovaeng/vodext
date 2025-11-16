package nxovaeng

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.ActorRole
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

    val lineSeparator: String = "$$$"
    val episodeSeparator: String = "#"
    val nameUrlSeparator: String = "$"

    override val mainPage = mainPageOf(
        "" to "最新更新",
        "t=2" to "电影",
        "t=1" to "电视剧",
        "t=17" to "动漫"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // 调用 api json 风格的 list 接口
        var url = "$mainUrl/api.php/provide/vod/?ac=detail&pg=$page"
        if (request.data.isNotBlank()) {
            url = "$mainUrl/api.php/provide/vod/?ac=detail&${request.data}&pg=$page"
        }

        val detail = app.get(url).parsed<VideoDetail>()
        val list = detail.list.mapNotNull { it.toSearchResponse() }

        return newHomePageResponse(listOf(HomePageList(request.name, list)), hasNext = true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$mainUrl/api.php/provide/vod/?ac=detail&wd=$encoded"
        val result = app.get(url).parsed<VideoDetail>()

        return result.list.mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.split("/").last()
        val durl = "$mainUrl/api.php/provide/vod/?ac=detail&ids=$id"
        val detail = app.get(durl).parsed<VideoDetail>()

        val media = detail.list.first()

        return media.toLoadResponse(durl)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        if (data.contains(nameUrlSeparator)) {
            // 剧集 多线路数据包 ("线路1$url1#线路2$url2")
            // 电影 ("正片$url1#花絮$url2")
            data.split(episodeSeparator).forEach { sourcePair ->
                try {
                    val parts = sourcePair.split(nameUrlSeparator, limit = 2)
                    val lineName = parts.getOrNull(0)?.trim()
                    val playUrl = parts.getOrNull(1)?.trim()

                    if (!lineName.isNullOrEmpty() && !playUrl.isNullOrEmpty()) {
                        // 如果本身就是 m3u8 地址，直接使用
                        if (playUrl.contains(".m3u8")) {
                            M3u8Helper.generateM3u8(lineName, playUrl, mainUrl, name = name).forEach(callback)
                        } else {
                            extractPlayUrl(playUrl, lineName, subtitleCallback, callback)
                        }
                    }
                } catch (e: Exception) {
                    // 捕获单个来源的解析异常，防止Sentry崩溃
                    // e.printStackTrace()
                }
            }
        } else if (data.isNotBlank()) {
            // 1. 电影只有一个 URL
            if (data.contains(".m3u8")) {
                M3u8Helper.generateM3u8(name, data, mainUrl).forEach(callback)
            } else {
                extractPlayUrl(data, name, subtitleCallback, callback)
            }
        }

        return true // 始终返回 true
    }

    private suspend fun extractPlayUrl(
        playUrl: String,
        lineName: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // 尝试从 playUrl 中提取实际的播放地址
        val pageContent = app.get(playUrl).text
        // 使用正则表达式提取 m3u8 地址
        val m3u8Regex = Regex("url: '(https?://[^']+\\.m3u8)'")
        val m3u8Match = m3u8Regex.find(pageContent)
        val m3u8Url = m3u8Match?.groupValues?.get(1)
        if (m3u8Url != null) {
            M3u8Helper.generateM3u8(lineName, m3u8Url, mainUrl, name = name).forEach(callback)
        } else {
            // fallback: try loadExtractor
            loadExtractor(
                playUrl, referer = mainUrl, subtitleCallback = subtitleCallback, callback = callback
            )
        }
    }

    // Minimal API models for list
    private data class VideoList(
        val code: Int = 0,
        val msg: String = "",
        val page: Int = 1,
        val pagecount: Int = 1,
        val limit: Int = 20,
        val total: Int = 0,
        val list: List<VideoInfo> = emptyList()
    )

    private data class VideoInfo(
        val vod_id: Int = 0,
        val vod_name: String = "",
        val type_id: Int = 0,
        val type_name: String = "",
        val vod_en: String = "",
        val vod_time: String = "",
        val vod_remarks: String = "",
        val vod_play_from: String = "",
    )

    data class VideoDetail(
        val code: Int,
        val msg: String,
        val page: Int,
        val pagecount: Int,
        val limit: Int,
        val total: Int,
        val list: List<VideoItem>
    )

    data class VideoItem(
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

    suspend fun VideoItem.toSearchResponse(): SearchResponse? {
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

    suspend fun VideoItem.toLoadResponse(url: String): LoadResponse {
        val type = mapTypeByName(type_name)

        val actors =
            vod_actor?.split(",")?.map { ActorData(Actor(name = it.trim())) } ?: emptyList()
        val director = vod_director?.split(",")?.map { director ->
            ActorData(Actor(name = director.trim()), role = ActorRole.Background)
        } ?: emptyList()

        val episodeList = getEpisodes()

        return if (type == TvType.Movie && episodeList.count() <= 1) {
            this@BaseVodProvider.newMovieLoadResponse(
                vod_name, url, TvType.Movie, episodeList.first().data
            ) {
                this.posterUrl = vod_pic
                this.plot = vod_content
                this.year = vod_year?.toIntOrNull()
                this.tags = vod_class?.split(",")
                this.actors = director + actors
            }
        } else {

            this@BaseVodProvider.newTvSeriesLoadResponse(vod_name, url, type, episodeList) {
                this.posterUrl = vod_pic
                this.plot = vod_content
                this.year = vod_year?.toIntOrNull()
                this.tags = vod_class?.split(",")
                this.actors = director + actors
            }
        }
    }


    /**
     * 将 Api 采集的扁平化数据源 (vod_play_from, vod_play_url) 进行转置。
     * * 原始数据: 线路: "线路1$$$线路2" 剧集: "第1集$url_A1#第2集$url_A2$$$第1集$url_B1#第2集$url_B2"
     *
     * * 目标 (转置后): 只显示一个列表 "剧集":
     * - 第1集 (data: "线路1$url_A1#线路2$url_B1")
     * - 第2集 (data: "线路1$url_A2#线路2$url_B2")
     * * 这样，当 loadLinks 接收到 data 时，它就能解析出所有来源。
     */
    fun VideoItem.getEpisodes(): List<Episode> {
        if (vod_play_url.isNullOrEmpty()) return emptyList()

        // 1. 解析原始数据
        val sources =
            vod_play_from?.split(lineSeparator)?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: listOf("默认线路")
        val playLists = vod_play_url.split(lineSeparator)

        if (sources.size != playLists.size) {
            // 数据源和播放列表数量不匹配，返回空
            return emptyList()
        }

        // 2. [关键] 解析所有线路的剧集数据
        // allEpisodeData 结构: List<List<Pair<String, String>>>
        // 外层 List: 按线路 (线路1, 线路2, ...)
        // 内层 List: 按剧集 (Ep1, Ep2, ...)
        // Pair: (剧集名称, 剧集URL)
        val allEpisodeData = playLists.map { playList ->
            // 解析这条线路上的所有剧集
            playList.split(episodeSeparator).mapNotNull { item ->
                val parts = item.split(nameUrlSeparator, limit = 2)
                val name = parts.getOrNull(0)?.trim()?.ifEmpty { null }
                val url = parts.getOrNull(1)?.trim()?.ifEmpty { null } ?: parts.getOrNull(0)?.trim()
                    ?.ifEmpty {
                        null
                    } // 容错：可能只有 URL

                if (url != null) {
                    Pair(name, url)
                } else {
                    null
                }
            }
        }

        // 3. [核心] 转置数据
        // 找到所有线路中剧集数最多的那个，作为我们的剧集列表长度
        val maxEpisodes = allEpisodeData.maxOfOrNull { it.size } ?: 0
        if (maxEpisodes == 0) return emptyList()

        val mergedEpisodes = (0 until maxEpisodes).map { episodeIndex ->
            // 这是"第 episodeIndex + 1 集"

            // 3a. 决定这一集的统一名称
            // 查找第一个不为空的剧集名称作为代表
            val episodeName = allEpisodeData.firstNotNullOfOrNull {
                it.getOrNull(episodeIndex)?.first
            } // 取第一个有效的
                ?: "第 ${episodeIndex + 1} 集" // 实在没有就用索引

            // 3b. 组合所有线路的 URL, 保持与原来一致，与电影返回兼容, 方便统一解析
            // 使用 "#" 作为线路分隔符, "$" 作为线路名和URL的分隔符
            // 格式: "线路1$url_A1#线路2$url_B1"
            val dataString = sources.indices.mapNotNull { sourceIndex ->
                // 尝试获取这条线路的、这一集的 URL
                allEpisodeData.getOrNull(sourceIndex)?.getOrNull(episodeIndex)?.second?.let { url ->
                    val sourceName = sources[sourceIndex]
                    "$sourceName$nameUrlSeparator$url" // 组合
                }
            }.joinToString(episodeSeparator)

            // 3c. 创建 Episode 对象
            this@BaseVodProvider.newEpisode(dataString) {
                this.name = episodeName
                this.episode = episodeIndex + 1
            }
        }

        return mergedEpisodes
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
