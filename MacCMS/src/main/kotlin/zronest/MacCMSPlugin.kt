package nxovaeng

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class MacCMSPlugin: BasePlugin() {
    override fun load() {
        // 注册所有 MacCMS 风格的站点
        registerMainAPI(BaseMacCmsProvider())
        registerMainAPI(ReboVodProvider())
        registerMainAPI(ZjkrmvProvider())
    }
}