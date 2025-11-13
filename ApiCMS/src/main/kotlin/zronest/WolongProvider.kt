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
            "vod/?ac=list" to "最新更新"
        )
}