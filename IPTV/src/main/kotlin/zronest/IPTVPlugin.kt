package nxovaeng

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class IPTVPlugin: Plugin() {
    override fun load(context: Context) {
        // 注册 IPTV 提供者
        registerMainAPI(ChinaIPTVProvider())
    }
}
