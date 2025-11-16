package nxovaeng

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf

/** 魔都资源站点提供者实现 */
class MdzyProvider : BaseVodProvider() {
    override var mainUrl = "https://www.mdzyapi.com"
    override var name = "魔都资源"

    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    override val mainPage =
        mainPageOf(
            "" to "最新更新",
            "t=7" to "电影",
            "t=8" to "电视剧",
            "t=1" to "动漫"
        )

    // 分类映射函数 暂时未使用
    private fun mapTypeByID(typeId: Int): TvType {
        return when (typeId) {
            // 电影
            7,
            10,
            11,
            12,
            13,
            14,
            15,
            16,
            17,
            18,
            19,
            20,
            21,
            22,
            23,
            25,
            39 -> TvType.Movie

            24 -> TvType.Documentary

            // 连续剧
            8,
            26,
            27,
            28,
            29,
            30,
            31,
            32,
            33,
            38 -> TvType.TvSeries

            // 动漫
            1,
            2,
            3,
            4,
            5,
            6 -> TvType.Anime

            // 综艺
            9,
            34,
            35,
            36,
            37 -> TvType.TvSeries

            else -> TvType.TvSeries
        }
    }

}
