package nxovaeng

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class ApiCMSPlugin: BasePlugin() {
    override fun load() {
        // 注册所有 API CMS 风格的资源站
        registerMainAPI(DoubanProvider())
        registerMainAPI(BaseVodProvider())
        registerMainAPI(BfzyProvider())
        registerMainAPI(MdzyProvider())
        // 注册 extractor
        registerExtractorAPI(JisuExtractor())
    }
}