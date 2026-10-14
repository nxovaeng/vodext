package nxovaeng

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class GuaziPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(GuaziProvider())
    }
}
