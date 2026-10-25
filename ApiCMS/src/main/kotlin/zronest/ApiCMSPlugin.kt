package nxovaeng

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class ApiCMSPlugin : BasePlugin() {
    override fun load() {
        // 1. 豆瓣：元数据选片展示，点击后智能走聚合站搜索播放
        registerMainAPI(DoubanProvider())
        // 2. 主流聚合：精选极速、暴风、非凡三大独立上游，秒播低延迟、无冗余无死链
        registerMainAPI(FastVodAggregatorProvider())
        // 3. 综合聚合：精选魔都采集、红牛、U酷、光速四大特色互补上游，片源覆盖更广
        registerMainAPI(GeneralVodAggregatorProvider())
    }
}
