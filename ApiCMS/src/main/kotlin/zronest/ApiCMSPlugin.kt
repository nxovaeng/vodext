package nxovaeng

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class ApiCMSPlugin : BasePlugin() {
    override fun load() {
        // 聚合站：多采集站合并为不同播放源
        registerMainAPI(VodAggregatorProvider())
        // 注册所有 API CMS 风格的资源站
        registerMainAPI(JisuProvider())
        registerMainAPI(BfzyProvider())
        registerMainAPI(MdzyProvider())
        registerMainAPI(IKunProvider())
        registerMainAPI(MaotaiProvider())
        registerMainAPI(FfzyProvider())
        registerMainAPI(SuoniProvider())
        registerMainAPI(ModuCaijiProvider())
        registerMainAPI(JinyingProvider())
        registerMainAPI(YinghuaProvider())
        registerMainAPI(HongniuProvider())
        registerMainAPI(UkuProvider())
        // 注册 extractor
        registerExtractorAPI(JisuExtractor())
    }
}
