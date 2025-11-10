package nxovaeng

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink

// JisuExtractor.kt
class JisuExtractor : ExtractorApi() {
    override val name = "Jisu云"
    override val mainUrl = "https://vv.jisuzyv.com"

    // 定义此 Extractor 支持的 URL 模式
    override val requiresReferer = false

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {

        if (url.contains("/play/")) {
            val doc = app.get(url).text
            val m3u8Url = doc.findM3u8Url() // 使用正则或其他方式提取
            if (m3u8Url != null) {
                callback.invoke(
                        newExtractorLink(name, name, m3u8Url) {
                            this.type = ExtractorLinkType.M3U8
                            this.quality = getQualityFromName(m3u8Url)
                            this.referer = referer ?: mainUrl
                        }
                )
            }
        }
    }

    private fun String.findM3u8Url(): String? {
        val m3u8Regex = Regex("url: '(https?://[^']+\\.m3u8)'")
        return m3u8Regex.find(this)?.groupValues?.get(1)
    }
}
