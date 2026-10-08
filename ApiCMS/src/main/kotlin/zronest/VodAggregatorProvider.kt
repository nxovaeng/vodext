package nxovaeng

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import org.json.JSONObject

/** 聚合站点配置 */
data class VodSite(val name: String, val mainUrl: String)

/**
 * 聚合多个苹果CMS采集站为一个 Provider。
 *
 * - 搜索/首页：各站并行请求，按归一化剧名合并为一部，url 存 {"站点名": vod_id} 的 JSON
 *   （newTvSeriesSearchResponse 用 fix=false，避免 fixUrl 破坏 JSON）。
 * - 详情：按 JSON 并行拉各站 detail，剧集按集号对齐合并，每集 data 复用
 *   BaseVodProvider 的 "线路名$url" + "#" 协议，标签为 "站点@线路"，
 *   loadLinks 拆分后各站即成为不同的播放源。
 * - referer：sourceReferer 按 "站点@" 前缀取对应站点的 mainUrl。
 */
class VodAggregatorProvider : BaseVodProvider() {

    override var name = "聚合采集"
    override var mainUrl = "https://bfzyapi.com" // 占位；实际 referer 按源解析
    override var lang = "zh"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    companion object {
        val sites =
                listOf(
                        VodSite("暴风", "https://bfzyapi.com"),
                        VodSite("iKun", "https://ikunzyapi.com"),
                        VodSite("极速", "https://jszyapi.com"),
                        VodSite("茅台", "https://maotaizy.com"),
                        VodSite("魔都", "https://www.mdzyapi.com"),
                        VodSite("卧龙", "https://wolongzy.cc"),
                )
        private val siteMap = sites.associateBy { it.name }

        /** data 标签里 "站点@线路" 的分隔符 */
        const val SITE_SEP = "@"

        /** 归一化剧名：去空白/标点/符号 + 小写，用于跨站合并 */
        fun normName(name: String): String =
                name.lowercase().replace(Regex("[\\s\\p{P}\\p{S}]"), "")
    }

    override fun sourceReferer(lineName: String): String {
        val siteName = lineName.substringBefore(SITE_SEP)
        return siteMap[siteName]?.mainUrl ?: super.sourceReferer(lineName)
    }

    override val mainPage = mainPageOf("" to "聚合最新")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = supervisorScope {
            sites.map { site ->
                async { listSite(site.mainUrl, page).map { site to it } }
            }.awaitAll().flatten()
        }
        val list = mergeItems(items).map { it.toSearchResponse() }
        return newHomePageResponse(listOf(HomePageList(request.name, list)), hasNext = true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val items = supervisorScope {
            sites.map { site ->
                async { searchSite(site.mainUrl, query).map { site to it } }
            }.awaitAll().flatten()
        }
        return mergeItems(items).map { it.toSearchResponse() }
    }

    private data class MergedEntry(
            val displayName: String,
            val posterUrl: String?,
            val typeName: String,
            val idMap: Map<String, String>, // 站点名 -> vod_id
    )

    /**
     * 按归一化剧名合并；同名但年份明显不同（翻拍）时按年拆分。
     */
    private fun mergeItems(items: List<Pair<VodSite, VideoItem>>): List<MergedEntry> {
        val result = mutableListOf<MergedEntry>()
        for (group in items.groupBy { normName(it.second.vod_name) }.values) {
            val years =
                    group.mapNotNull {
                                it.second.vod_year?.toIntOrNull()?.takeIf { y -> y > 0 }
                            }
                            .toSet()
            // 多个不同已知年份 → 按年拆分，避免翻拍误合并
            val subGroups: List<List<Pair<VodSite, VideoItem>>> =
                    if (years.size > 1) {
                        group.groupBy {
                                    it.second.vod_year?.toIntOrNull()?.takeIf { y -> y > 0 } ?: -1
                                }
                                .values
                                .toList()
                    } else {
                        listOf(group)
                    }
            for (sg in subGroups) {
                val first = sg.first().second
                result.add(
                        MergedEntry(
                                displayName = first.vod_name,
                                posterUrl =
                                        sg.firstNotNullOfOrNull {
                                            it.second.vod_pic?.ifEmpty { null }
                                        },
                                typeName = first.type_name,
                                idMap = sg.associate { (site, item) -> site.name to item.vod_id.toString() }
                        )
                )
            }
        }
        return result
    }

    private fun MergedEntry.toSearchResponse(): SearchResponse {
        val type = mapTypeByName(typeName)
        // fix=false：url 是 JSON，不能被 fixUrl 加前缀破坏
        val urlJson = JSONObject(idMap).toString()
        return if (type == TvType.Movie) {
            newMovieSearchResponse(displayName, urlJson, type, fix = false) {
                posterUrl = this@toSearchResponse.posterUrl
            }
        } else {
            newTvSeriesSearchResponse(displayName, urlJson, type, fix = false) {
                posterUrl = this@toSearchResponse.posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val idMap: Map<String, String> =
                try {
                    val obj = JSONObject(url)
                    obj.keys().asSequence().associateWith { obj.getString(it) }
                } catch (_: Exception) {
                    throw ErrorLoadingException("invalid aggregator url")
                }

        // 并行拉各站 detail，单站失败不影响其它站
        val details = supervisorScope {
            idMap.mapNotNull { (siteName, id) ->
                siteMap[siteName]?.let { site ->
                    async {
                        try {
                            val item = fetchSiteDetail(site.mainUrl, id) ?: return@async null
                            val triples = item.getEpisodeTriples()
                            if (triples.isEmpty()) return@async null
                            Triple(site, item, triples)
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        if (details.isEmpty()) throw ErrorLoadingException("all sources failed")

        val firstItem = details.first().second

        // 按集号对齐合并各站剧集
        val allNums = details.flatMap { (_, _, triples) -> triples.map { it.first } }.toSortedSet()
        val episodes = allNums.map { num ->
            val epName =
                    details.firstNotNullOfOrNull { (_, _, triples) ->
                        triples.find { it.first == num }?.second
                    } ?: "第${num}集"
            // 每站该集的数据是 "线路1$u1#线路2$u2"，拆开后加上 "站点@" 前缀再拼回去；
            // loadLinks 按 "#" 拆分后即得到 "站点@线路" 为名的多个播放源
            val data =
                    details
                            .flatMap { (site, _, triples) ->
                                triples.find { it.first == num }?.third
                                        ?.split(episodeSeparator)
                                        ?.mapNotNull { part ->
                                            val seg = part.split(nameUrlSeparator, limit = 2)
                                            val lineName = seg.getOrNull(0)?.trim()
                                            val playUrl = seg.getOrNull(1)?.trim()
                                            if (lineName.isNullOrEmpty() ||
                                                            playUrl.isNullOrEmpty()
                                            )
                                                    null
                                            else
                                                    "${site.name}$SITE_SEP$lineName$nameUrlSeparator$playUrl"
                                        } ?: emptyList()
                            }
                            .joinToString(episodeSeparator)
            // fix=false：data 是 "名$url#..." 协议串，不是 URL
            newEpisode(data, fix = false) {
                this.name = epName
                this.episode = num
            }
        }

        // 复用首站的元数据（标题/海报/简介/演员等）
        return firstItem.toLoadResponse(url, episodes)
    }
}
