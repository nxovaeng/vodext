package nxovaeng

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class IPTVPlugin : Plugin() {
    override fun load(context: Context) {
        // 注册合并优化后的中国 IPTV 提供者（已整合央视官方稳定源、实时 EPG 与同频道多源切换）
        registerMainAPI(ChinaIPTVProvider())
    }
}
