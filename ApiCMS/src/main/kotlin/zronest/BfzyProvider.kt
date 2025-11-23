package nxovaeng

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import kotlinx.coroutines.runBlocking

/** 暴风资源，会屏蔽非中国ip访问 */
class BfzyProvider : BaseVodProvider() {
    override var mainUrl = "https://bfzyapi.com"
    override var name = "暴风资源"

    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    /** 动态生成主页分类 可以选择从 API 获取分类，或使用默认配置 */
    override val mainPage = runBlocking {
        // 方案1: 从 API 动态获取（推荐，但第一次加载可能稍慢）
        val categoryNames = listOf("最新更新", "国产剧", "国产动漫", "动作片")
        val dynamicPages = buildMainPageList(categoryNames)

        // 如果 API 获取失败，使用默认配置
        if (dynamicPages.isNotEmpty()) {
            mainPageOf(*dynamicPages.toTypedArray())
        } else {
            // 方案2: 使用默认硬编码配置（作为后备）
            mainPageOf("" to "最新更新", "t=21" to "电影", "t=30" to "电视剧", "t=39" to "动漫")
        }
    }
}
