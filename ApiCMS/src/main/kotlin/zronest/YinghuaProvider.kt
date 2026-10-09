package nxovaeng

import com.lagradost.cloudstream3.TvType

/** 樱花资源站点提供者实现（无 CNAME，直解拉斯维加斯） */
class YinghuaProvider : BaseVodProvider() {
    override var mainUrl = "https://m3u8.apiyhzy.com"
    override var name = "樱花资源"

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
