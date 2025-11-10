package zronest

import com.lagradost.cloudstream3.TvType

class ReboVodProvider : BaseMacCmsProvider() {
    override var mainUrl = "https://www.rebovod.com"
    override var name = "热剧天堂"

    // 如果该站点的列表选择器与 Base 不同，可覆盖
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
}
