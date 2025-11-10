package zronest

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf

/** 简单的 TVBox 类型采集站点提供者示例（例如 bfzyapi.com） 基于简单的 TVBox API 风格：请求 JSON 或简单的接口返回 list -> media */
class BfzyProvider : BaseVodProvider() {
    override var mainUrl = "https://bfzyapi.com"
    override var name = "暴风资源"

    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    override val mainPage =
        mainPageOf(
            "vod/?ac=list" to "最新更新",
            "vod/?ac=list&t=20" to "电影",
            "vod/?ac=list&t=30" to "电视剧",
            "vod/?ac=list&t=39" to "动漫"
        )

    // 分类映射函数 暂时未使用
    fun mapTypeByID(typeId: Int): TvType {
        return when (typeId) {
            // 电影类
            20,
            21,
            22,
            23,
            24,
            25,
            26,
            27,
            29,
            50 -> TvType.Movie

            28 -> TvType.Documentary

            // 连续剧
            30,
            31,
            32,
            33,
            34,
            35,
            36,
            37,
            38 -> TvType.TvSeries

            // 动漫
            39,
            40,
            41,
            42,
            43,
            44 -> TvType.Anime

            // 综艺
            45,
            46,
            47,
            48,
            49 -> TvType.TvSeries

            // 体育赛事
            53,
            54,
            55,
            56,
            57 -> TvType.TvSeries

            // 短剧
            58,
            65,
            66,
            67,
            68,
            69,
            70,
            71,
            72 -> TvType.TvSeries

            // 其他特殊类
            51,
            52,
            73 -> TvType.TvSeries

            else -> TvType.Others
        }
    }

}
