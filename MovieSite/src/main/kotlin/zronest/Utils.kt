package zronest

import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.USER_AGENT


object Utils {

    suspend fun sniffVideoWithWebView(
        url: String,
        referer: String?
    ): String {
        val targetRegex = Regex("""(?i).*\.m3u8.*""") // 忽略大小写匹配 m3u8

        val webViewM3u8Resolver = WebViewResolver(
            interceptUrl = targetRegex,
            additionalUrls = listOf(Regex("""txt|m3u8""")),
            userAgent = USER_AGENT,
            useOkhttp = false,
            timeout = 15_000L
        )

        val interceptedUrl = app.get(
            url,
            referer = referer,
            interceptor = webViewM3u8Resolver
        ).url

        return interceptedUrl
    }

}