package nxovaeng

import com.lagradost.cloudstream3.TvType

/** 魔都采集站点提供者实现（魔都 API 镜像） */
class ModuCaijiProvider : BaseVodProvider() {
    override var mainUrl = "https://caiji.moduapi.cc"
    override var name = "魔都采集"

    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    /** 动态生成主页分类 使用 lazy 延迟加载，避免阻塞应用启动 第一次访问时才从 API 获取分类，失败时使用默认配置 */
    private val mainPageDelegate by
            buildMainPageLazy(
                    categoryNames = listOf("最新更新", "国产剧", "国产动漫", "动作片"),
                    fallbackPages =
                            listOf("" to "最新更新", "t=1" to "电影", "t=2" to "电视剧", "t=4" to "动漫")
            )

    override val mainPage
        get() = mainPageDelegate
}
