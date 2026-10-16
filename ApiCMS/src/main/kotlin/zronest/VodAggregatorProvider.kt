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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import org.json.JSONObject

/** 聚合站点配置（含上游分组与分类ID，用于首页分组聚合） */
data class VodSite(
        val name: String,
        val mainUrl: String,
        /** 上游分组：同组只查一个代表站，减少首页请求数 */
        val group: String = name,
        /** 电影栏分类ID列表（动作片等细分类；空则不参与） */
        val movieIds: List<Int> = emptyList(),
        /** 电视剧栏分类ID列表（国产剧/内地剧） */
        val tvIds: List<Int> = emptyList(),
        /** 动漫栏分类ID列表（国产动漫/中国动漫等） */
        val animeIds: List<Int> = emptyList(),
)

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
        // 分类ID取自各站 ?ac=list 的 class 字典（2026-10-16 实测）；
        // group 按上游归组：同组站点首页只查代表站（每组第一个）
        // 首页三栏用细分类（国产剧/动作片/国产动漫系），大分类（电影/电视剧）数据太杂
        val sites =
                listOf(
                        VodSite("暴风", "https://bfzyapi.com", "baofeng",
                                movieIds = listOf(21), tvIds = listOf(31), animeIds = listOf(40)),
                        VodSite("iKun", "https://ikunzyapi.com", "ikun",
                                movieIds = listOf(6), tvIds = listOf(23), animeIds = listOf(4, 35)),
                        VodSite("极速", "https://jszyapi.com", "jisu",
                                movieIds = listOf(9), tvIds = listOf(20), animeIds = listOf(17, 24)),
                        VodSite("茅台", "https://maotaizy.com", "maotai",
                                movieIds = listOf(6), tvIds = listOf(13), animeIds = listOf(4, 30)),
                        VodSite("魔都", "https://www.mdzyapi.com", "modu",
                                movieIds = listOf(10), tvIds = listOf(26), animeIds = listOf(1)),
                        VodSite("非凡", "https://api.ffzyapi.com", "feifan",
                                movieIds = listOf(6), tvIds = listOf(13), animeIds = listOf(29)),
                        VodSite("索尼", "https://suoniapi.com", "suoni",
                                movieIds = listOf(6), tvIds = listOf(13), animeIds = listOf(4, 29)),
                        // 魔都采集与魔都同上游（class 映射完全相同），首页不重复查
                        VodSite("魔都采集", "https://caiji.moduapi.cc", "modu",
                                movieIds = listOf(10), tvIds = listOf(26), animeIds = listOf(1)),
                        // 金鹰与极速同模板（class 映射完全相同），首页不重复查
                        VodSite("金鹰", "https://jyzyapi.com", "jisu",
                                movieIds = listOf(9), tvIds = listOf(20), animeIds = listOf(17, 24)),
                        VodSite("樱花", "https://m3u8.apiyhzy.com", "yinghua",
                                movieIds = listOf(6), tvIds = listOf(13), animeIds = listOf(4, 30)),
                        VodSite("红牛", "https://hongniuzy2.com", "hongniu",
                                movieIds = listOf(5), tvIds = listOf(12), animeIds = listOf(4, 36)),
                        VodSite("U酷", "https://api.ukuapi88.com", "uku",
                                movieIds = listOf(6), tvIds = listOf(13), animeIds = listOf(4)),
                        VodSite("光速", "https://api.guangsuapi.com", "guangsu",
                                movieIds = listOf(6), animeIds = listOf(4, 41)),
                        VodSite("速播", "https://subocj.com", "subo",
                                movieIds = listOf(6), animeIds = listOf(3, 24)),
                )
        private val siteMap = sites.associateBy { it.name }

        /** 首页每栏按上游分组取代表站（每组第一个有该分类的站点） */
        private fun repSites(getIds: (VodSite) -> List<Int>): List<VodSite> =
                sites.groupBy { it.group }
                        .mapNotNull { (_, g) -> g.firstOrNull { getIds(it).isNotEmpty() } }

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

    override val mainPage = mainPageOf(
            "movie" to "动作电影",
            "tv" to "国产剧集",
            "anime" to "国漫",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // 按栏取对应分类ID列表，每栏按上游分组只查代表站；多ID并行查后合并
        val getIds: (VodSite) -> List<Int> = when (request.data) {
            "movie" -> { it.movieIds }
            "tv" -> { it.tvIds }
            "anime" -> { it.animeIds }
            else -> { _ -> emptyList() }
        }
        val items = supervisorScope {
            repSites(getIds).flatMap { site ->
                getIds(site).map { typeId ->
                    async { listSite(site.mainUrl, page, typeId).map { site to it } }
                }
            }.awaitAll().flatten()
        }
        val list = mergeItems(items).map { it.toSearchResponse() }
        // 有返回才认为有下一页，避免空页死循环
        return newHomePageResponse(listOf(HomePageList(request.name, list)), hasNext = list.isNotEmpty())
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
            newEpisode(data, fix = false, initializer = {
                this.name = epName
                this.episode = num
            })
        }

        // 复用首站的元数据（标题/海报/简介/演员等）
        return firstItem.toLoadResponse(url, episodes)
    }
}
