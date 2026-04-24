package zronest

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.api.Log

class Donghuaword : Animekhor() {
    override var mainUrl = "https://donghuaworld.com"
    override var name = "Donghuaword"
    override val hasMainPage = true
    override var lang = "zh"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime)

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // 尝试多种选择器和属性以适应网站变化
        var servers = document.select("div.server-item a")
        
        servers.mapNotNull { it ->
            try {
                // 尝试多个属性名
                val base64 = it.attr("data-hash").takeIf { s -> s.isNotEmpty() }
                        ?: it.attr("data-url").takeIf { s -> s.isNotEmpty() }
                        ?: it.attr("data-src").takeIf { s -> s.isNotEmpty() }
                        ?: return@mapNotNull null
                        
                val decodedUrl = base64Decode(base64)
                val regex = Regex("""src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                val matchResult = regex.find(decodedUrl)
                val url = matchResult?.groups?.get(1)?.value?.takeIf { u -> u.isNotEmpty() } ?: return@mapNotNull null
                
                loadExtractor(url, referer = mainUrl, subtitleCallback) { link ->
                    // Filter for quality >= 720p or unknown
                    if (link.quality >= 720 || link.quality <= 0) {
                        callback(link)
                    }
                }
            } catch (e: Exception) {
                Log.w("Donghuaword", "Failed to load link: ${e.message}")
            }
        }
        return true
    }
}
