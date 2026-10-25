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
 * 支持根据站点上游分组继承派生不同梯队的聚合 Provider（如主流聚合、综合聚合）。
 *
 * - 搜索/首页：各站并行请求，按归一化剧名合并为一部，url 存 {"站点名": vod_id} 的 JSON
 *   （newTvSeriesSearchResponse 用 fix=false，避免 fixUrl 破坏 JSON）。
 * - 详情：按 JSON 并行拉各站 detail，剧集按集号对齐合并，每集 data 复用
 *   BaseVodProvider 的 "线路名$url" + "#" 协议，标签为 "站点@线路"，
 *   loadLinks 拆分后各站即成为不同的播放源。
 * - referer：sourceReferer 按 "站点@" 前缀取对应站点的 mainUrl。
 */
open class VodAggregatorProvider(
        protected open val sites: List<VodSite> = fastSites
) : BaseVodProvider() {

    override var name = "聚合采集"
    override var mainUrl = "https://bfzyapi.com" // 占位；实际 referer 按源解析
    override var lang = "zh"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    companion object {
        // 主流/秒播梯队：三大顶级独立上游，响应极快，自建云切片，无冗余无死链
        val fastSites = listOf(
                VodSite("极速", "https://jszyapi.com", "jisu",
                        movieIds = listOf(9, 12, 35), tvIds = listOf(20), animeIds = listOf(24)),
                VodSite("暴风", "https://bfzyapi.com", "baofeng",
                        movieIds = listOf(21, 24), tvIds = listOf(31), animeIds = listOf(40)),
                VodSite("非凡", "https://api.ffzyapi.com", "feifan",
                        movieIds = listOf(6, 9), tvIds = listOf(13), animeIds = listOf(29)),
        )

        // 综合/互补梯队：四大特色独立切片库，补充魔都高清动漫、冷门片源、欧美港台
        val generalSites = listOf(
                VodSite("魔都采集", "https://caiji.moduapi.cc", "modu",
                        movieIds = listOf(10, 13, 21), tvIds = listOf(26), animeIds = listOf(1)),
                VodSite("红牛", "https://hongniuzy2.com", "hongniu",
                        movieIds = listOf(5, 8, 49), tvIds = listOf(12), animeIds = listOf(36)),
                VodSite("U酷", "https://api.ukuapi88.com", "uku",
                        movieIds = listOf(6, 9, 34), tvIds = listOf(13), animeIds = listOf(4, 20)),
                VodSite("光速", "https://api.guangsuapi.com", "guangsu",
                        movieIds = listOf(6, 9, 50), tvIds = listOf(13), animeIds = listOf(41)),
        )

        // 全部已知站点（含兼容映射与备用站）
        val allSites = fastSites + generalSites + listOf(
                VodSite("魔都", "https://www.mdzyapi.com", "modu"),
                VodSite("金鹰", "https://jyzyapi.com", "jisu"),
                VodSite("速播", "https://subocj.com", "subo"),
        )
        val allSiteMap = allSites.associateBy { it.name }

        /** data 标签里 "站点@线路" 的分隔符 */
        const val SITE_SEP = "@"

        /** 归一化剧名：去空白/标点/符号 + 小写，用于跨站合并 */
        fun normName(name: String): String =
                name.lowercase().replace(Regex("[\\s\\p{P}\\p{S}]"), "")
    }

    protected val siteMap by lazy { (allSiteMap + sites.associateBy { it.name }) }

    /** 首页每栏按上游分组取代表站（每组第一个有该分类的站点） */
    protected fun repSites(getIds: (VodSite) -> List<Int>): List<VodSite> =
            sites.groupBy { it.group }
                    .mapNotNull { (_, g) -> g.firstOrNull { getIds(it).isNotEmpty() } }

    override fun sourceReferer(lineName: String): String {
        val siteName = lineName.substringBefore(SITE_SEP)
        return siteMap[siteName]?.mainUrl ?: super.sourceReferer(lineName)
    }

    override val mainPage = mainPageOf(
            "movie" to "热播电影",
            "tv" to "国产剧集",
            "anime" to "热门国漫",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // 按栏取对应分类ID列表，每栏按上游分组只查代表站；多ID并行查后合并
        val getIds: (VodSite) -> List<Int> = { site ->
            when (request.data) {
                "movie" -> site.movieIds
                "tv" -> site.tvIds
                "anime" -> site.animeIds
                else -> emptyList()
            }
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

/** 主流聚合：三大顶级独立上游（极速、暴风、非凡），秒播低延迟 */
class FastVodAggregatorProvider : VodAggregatorProvider(fastSites) {
    override var name = "主流聚合"
}

/** 综合聚合：四大特色互补上游（魔都采集、红牛、U酷、光速），片源更全 */
class GeneralVodAggregatorProvider : VodAggregatorProvider(generalSites) {
    override var name = "综合聚合"
}
