package nxovaeng

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class IPTVPlugin : Plugin() {
    override fun load(context: Context) {
        // 注册 IPTV 提供者
        registerMainAPI(ChinaIPTVProvider())
        registerMainAPI(CCTVProvider()) // CCTV 官方直播
    }
}
