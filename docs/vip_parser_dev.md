
## 如何在 CloudStream 中实现类似TVBox的解析播放功能

虽然 CloudStream 没有专门的"解析器"系统，但可以通过以下方式实现：

### 方案 1: 在 Provider 中直接调用解析服务

```kotlin
class MyProvider : MainAPI() {
    override var name = "My Provider"
    override var mainUrl = "https://example.com"
    
    // 定义解析服务列表（类似 TVBox 的 parses）
    private val parsers = listOf(
        Parser("咸鱼", "https://jx.xyflv.cc/?url=", mapOf(
            "User-Agent" to "Mozilla/5.0 ...",
            "Referer" to "https://www.xyflv.cc/"
        )),
        Parser("虾米", "https://jx.xmflv.com/?url=", mapOf(
            "User-Agent" to "Mozilla/5.0 ..."
        )),
        Parser("爱酷", "https://jx.zhanlangbu.com/?url=", mapOf(
            "User-Agent" to "Mozilla/5.0 ..."
        ))
    )
    
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // 假设页面返回的是需要 VIP 的链接
        val vipUrl = document.selectFirst(".play-btn")?.attr("data-url")
            ?: return false
        
        // 尝试使用多个解析器
        parsers.forEach { parser ->
            try {
                val realUrl = parseVipUrl(vipUrl, parser)
                if (realUrl != null) {
                    callback.invoke(
                        ExtractorLink(
                            source = "${name} - ${parser.name}",
                            name = parser.name,
                            url = realUrl,
                            referer = mainUrl,
                            quality = Qualities.Unknown.value,
                            isM3u8 = realUrl.contains(".m3u8")
                        )
                    )
                }
            } catch (e: Exception) {
                logError(e)
            }
        }
        
        return true
    }
    
    // 调用解析服务
    private suspend fun parseVipUrl(vipUrl: String, parser: Parser): String? {
        // 构造解析请求
        val parseUrl = "${parser.baseUrl}${vipUrl}"
        
        val response = app.get(
            parseUrl,
            headers = parser.headers
        )
        
        // 从解析服务的响应中提取真实播放地址
        // 不同解析服务返回格式不同，需要具体分析
        return extractPlayUrl(response.text)
    }
    
    // 提取播放地址（需要根据实际解析服务调整）
    private fun extractPlayUrl(html: String): String? {
        // 方式1: 从 HTML 中提取
        val doc = Jsoup.parse(html)
        val videoTag = doc.selectFirst("video source")
        if (videoTag != null) {
            return videoTag.attr("src")
        }
        
        // 方式2: 从 script 中提取
        val scriptRegex = """url\s*=\s*["']([^"']+)["']""".toRegex()
        val match = scriptRegex.find(html)
        if (match != null) {
            return match.groupValues[1]
        }
        
        // 方式3: JSON 响应
        try {
            val json = parseJson<ParseResponse>(html)
            return json.url
        } catch (e: Exception) {
            // Ignore
        }
        
        return null
    }
}

data class Parser(
    val name: String,
    val baseUrl: String,
    val headers: Map<String, String>
)

data class ParseResponse(
    val url: String,
    val code: Int? = null
)
```

### 方案 2: 使用 localProxy 实现解析中转

如果解析服务返回的不是直接播放地址，而是需要进一步处理：

```kotlin
class MyProvider : MainAPI() {
    
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val vipUrl = extractVipUrl(data)
        
        // 通过 localProxy 中转解析
        val proxyUrl = buildParseProxyUrl(vipUrl, "咸鱼")
        
        callback.invoke(
            ExtractorLink(
                source = name,
                name = "咸鱼解析",
                url = proxyUrl,
                referer = mainUrl,
                quality = Qualities.Unknown.value,
                isM3u8 = true
            )
        )
        
        return true
    }
    
    private fun buildParseProxyUrl(vipUrl: String, parser: String): String {
        val encoded = URLEncoder.encode(vipUrl, "UTF-8")
        return "http://127.0.0.1:8080/localProxy?action=parse&parser=$parser&url=$encoded"
    }
    
    override fun localProxy(params: Map<String, String>): ByteArray? {
        val action = params["action"]
        
        if (action == "parse") {
            val parser = params["parser"] ?: return null
            val vipUrl = params["url"] ?: return null
            
            // 调用对应的解析服务
            val parseResult = when (parser) {
                "咸鱼" -> parseWithXianyu(vipUrl)
                "虾米" -> parseWithXiami(vipUrl)
                else -> null
            }
            
            return parseResult
        }
        
        return null
    }
    
    private fun parseWithXianyu(vipUrl: String): ByteArray? {
        val parseUrl = "https://jx.xyflv.cc/?url=${vipUrl}"
        
        val response = app.get(parseUrl, headers = mapOf(
            "User-Agent" to "Mozilla/5.0 ...",
            "Referer" to "https://www.xyflv.cc/"
        ))
        
        // 提取真实 m3u8 地址
        val realUrl = extractPlayUrl(response.text) ?: return null
        
        // 获取 m3u8 内容
        val m3u8Response = app.get(realUrl)
        return m3u8Response.body?.bytes()
    }
}
```

### 方案 3: 创建通用解析 Extractor

CloudStream 支持自定义 Extractor（视频提取器）：

```kotlin
// 通用解析器 Extractor
class GenericParserExtractor : ExtractorApi() {
    override val name = "通用解析"
    override val mainUrl = "https://jx.xyflv.cc"
    override val requiresReferer = false
    
    // 解析服务配置
    private val parsers = mapOf(
        "咸鱼" to ParserConfig(
            "https://jx.xyflv.cc/?url=",
            mapOf(
                "User-Agent" to "Mozilla/5.0 ...",
                "Referer" to "https://www.xyflv.cc/"
            )
        ),
        "虾米" to ParserConfig(
            "https://jx.xmflv.com/?url=",
            mapOf("User-Agent" to "Mozilla/5.0 ...")
        )
    )
    
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // url 是要解析的 VIP 链接
        parsers.forEach { (name, config) ->
            try {
                val parseUrl = "${config.baseUrl}${url}"
                val response = app.get(parseUrl, headers = config.headers)
                
                val realUrl = extractRealUrl(response.text)
                if (realUrl != null) {
                    callback.invoke(
                        ExtractorLink(
                            source = this.name,
                            name = name,
                            url = realUrl,
                            referer = referer ?: "",
                            quality = Qualities.Unknown.value,
                            isM3u8 = realUrl.contains(".m3u8")
                        )
                    )
                }
            } catch (e: Exception) {
                logError(e)
            }
        }
    }
    
    private fun extractRealUrl(html: String): String? {
        // 多种提取策略
        
        // 策略1: 查找 video 标签
        val doc = Jsoup.parse(html)
        doc.selectFirst("video source")?.attr("src")?.let { return it }
        
        // 策略2: 正则提取
        val patterns = listOf(
            """url\s*[:=]\s*["']([^"']+\.m3u8[^"']*)["']""",
            """src\s*[:=]\s*["']([^"']+\.mp4[^"']*)["']""",
            """"url"\s*:\s*"([^"]+)"""
        )
        
        patterns.forEach { pattern ->
            Regex(pattern).find(html)?.groupValues?.get(1)?.let { return it }
        }
        
        // 策略3: JSON 解析
        try {
            val json = parseJson<Map<String, Any>>(html)
            (json["url"] as? String)?.let { return it }
        } catch (e: Exception) {
            // Ignore
        }
        
        return null
    }
}

data class ParserConfig(
    val baseUrl: String,
    val headers: Map<String, String>
)

// 在 Provider 中使用
class MyProvider : MainAPI() {
    override suspend fun loadLinks(...): Boolean {
        val vipUrl = extractVipUrl(data)
        
        // 使用通用解析器
        val extractor = GenericParserExtractor()
        extractor.getUrl(vipUrl, mainUrl, subtitleCallback, callback)
        
        return true
    }
}
```

---
