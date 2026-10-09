package nxovaeng

import com.lagradost.cloudstream3.TvType

/** 金鹰资源站点提供者实现（老牌稳定，百度云济南） */
class JinyingProvider : BaseVodProvider() {
    override var mainUrl = "https://jyzyapi.com"
    override var name = "金鹰资源"

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
