package nxovaeng

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf

/** 卧龙资源站，会屏蔽非中国ip访问 */
class WolongProvider : BaseVodProvider() {
    override var mainUrl = "https://collect.wolongzy.cc"
    override var name = "卧龙资源"

    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    override val mainPage =
        mainPageOf(
            "" to "最新更新",
            "t=1" to "电影",
            "t=2" to "电视剧",
            "t=4" to "动漫"
        )
}

/** ikun资源站 */
class IKunProvider : BaseVodProvider() {
    override var mainUrl = "https://ikunzyapi.com"
    override var name = "iKun资源"

    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    override val mainPage =
        mainPageOf(
            "" to "最新更新",
            "t=1" to "电影",
            "t=2" to "电视剧",
            "t=4" to "动漫"
        )
}

/** 茅台资源站 */
class MaotaiProvider : BaseVodProvider() {
    override var mainUrl = "https://maotaizy.com/"
    override var name = "茅台资源"

    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    override val mainPage =
        mainPageOf(
            "" to "最新更新",
            "t=1" to "电影",
            "t=2" to "电视剧",
            "t=4" to "动漫"
        )
}
