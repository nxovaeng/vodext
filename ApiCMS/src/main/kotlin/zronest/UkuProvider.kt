package nxovaeng

import com.lagradost.cloudstream3.TvType

/** U酷资源站点提供者实现（Cogent 洛杉矶） */
class UkuProvider : BaseVodProvider() {
    override var mainUrl = "https://api.ukuapi88.com" // 2026-10-09：站方换域名，旧 api.ukuapi.com 301 到此
    override var name = "U酷资源"

    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    private val mainPageDelegate by
            buildMainPageLazy(
                    categoryNames = listOf("最新更新", "国产剧", "国产动漫", "动作片"),
                    fallbackPages =
                            listOf("" to "最新更新", "t=1" to "电影", "t=2" to "电视剧", "t=4" to "动漫")
            )

    override val mainPage
        get() = mainPageDelegate
}
