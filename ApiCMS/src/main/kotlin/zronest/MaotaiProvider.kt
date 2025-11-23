package nxovaeng

import com.lagradost.cloudstream3.TvType

/** 茅台资源站 - 支持动态获取分类 */
class MaotaiProvider : BaseVodProvider() {
    override var mainUrl = "https://maotaizy.com/"
    override var name = "茅台资源"

    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    /** 动态生成主页分类 使用 lazy 延迟加载，避免阻塞应用启动 第一次访问时才从 API 获取分类，失败时使用默认配置 */
    private val mainPageDelegate by
            buildMainPageLazy(
                    categoryNames = listOf("最新更新", "国产剧", "国产动漫", "动作片"),
                    fallbackPages =
                            listOf("" to "最新更新", "t=6" to "电影", "t=13" to "国产剧", "t=30" to "国产动漫")
            )

    override val mainPage
        get() = mainPageDelegate
}
