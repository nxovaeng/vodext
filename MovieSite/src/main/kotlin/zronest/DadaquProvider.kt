package zronest

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.json.JSONArray
import org.jsoup.nodes.Element

/** 达达趣 - https://www.dadaqu.pro/ */
class DadaquProvider : MainAPI() {
        override var mainUrl = "https://www.dadaqu.pro"
        override var name = "达达趣"
        override var lang = "zh"
        override val hasMainPage = true

        // 备用域名：https://www.dadaqu.fun, https://www.dadaqu.me

        override val supportedTypes =
                setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

        override val mainPage = mainPageOf("1" to "电影", "2" to "电视剧", /*"3" to "综艺",*/ "4" to "动漫")

        companion object {
                private const val TAG = "DadaquProvider"

                // 加密/解密所需的常量
                private const val JS_KEY = "jZ#8C*d!2$"
                private const val STATIC_CHARS =
                        "PXhw7UT1B0a9kQDKZsjIASmOezxYG4CHo5Jyfg2b8FLpEvRr3WtVnlqMidu6cN"
                private const val BASE64_CHARS =
                        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

                // 全局 Cookie（用于反爬绕过后保持会话）
                @Volatile private var globalCookie = ""
        }

        // ========================================================================
        // 主页 / 搜索 / 详情加载（保持原有逻辑）
        // ========================================================================

        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
                val typeId = request.data
                // 使用 /show/ 热播排序，与 JS scraper 的 getDadaquRecent 一致
                val pageUrl =
                        if (page == 1) {
                                "$mainUrl/show/$typeId--hits---------.html"
                        } else {
                                "$mainUrl/show/$typeId--hits------$page---.html"
                        }

                val document = fetchWithBypass(pageUrl)

                val home = document.select(".module-item").mapNotNull { it.toShowResult() }

                return newHomePageResponse(
                        listOf(HomePageList(request.name, home)),
                        hasNext = home.isNotEmpty()
                )
        }

        /** 解析 /show/ 页面的 .module-item 元素 该页面中 .module-item 是 <a> 标签，包含 href、title 和图片 */
        private fun Element.toShowResult(): SearchResponse? {
                val title =
                        this.attr("title").ifEmpty { this.text().trim() }.ifEmpty {
                                return null
                        }
                val href = fixUrl(this.attr("href"))
                val posterUrl =
                        fixUrlNull(
                                this.selectFirst(".module-item-pic img")?.attr("data-original")
                                        ?: this.selectFirst("img")?.attr("data-original")
                                                ?: this.selectFirst("img")?.attr("src")
                        )

                return newMovieSearchResponse(title, href, TvType.Movie) {
                        this.posterUrl = posterUrl
                }
        }

        private fun Element.toSearchResult(): SearchResponse? {
                val title =
                        this.attr("title").ifEmpty { this.selectFirst("img")?.attr("alt") }?.trim()
                                ?: return null

                val href = fixUrl(this.attr("href"))
                val posterUrl =
                        fixUrlNull(
                                this.selectFirst("img")?.attr("data-original")
                                        ?: this.selectFirst("img")?.attr("src")
                        )

                val type =
                        when {
                                href.contains("/type/1") -> TvType.Movie
                                href.contains("/type/2") -> TvType.TvSeries
                                href.contains("/type/3") -> TvType.AsianDrama
                                href.contains("/type/4") -> TvType.Anime
                                else -> TvType.Movie
                        }

                return if (type == TvType.Movie) {
                        newMovieSearchResponse(title, href, TvType.Movie) {
                                this.posterUrl = posterUrl
                        }
                } else {
                        newTvSeriesSearchResponse(title, href, type) { this.posterUrl = posterUrl }
                }
        }

        private fun Element.toCardSearchResult(): SearchResponse? {
                val title = this.selectFirst("strong")?.text()?.trim() ?: return null
                val href = fixUrl(this.attr("href"))
                val posterUrl =
                        fixUrlNull(
                                this.selectFirst("img")?.attr("data-original")
                                        ?: this.selectFirst("img")?.attr("src")
                        )

                return newMovieSearchResponse(title, href, TvType.Movie) {
                        this.posterUrl = posterUrl
                }
        }

        override suspend fun search(query: String): List<SearchResponse> {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val searchUrl = "$mainUrl/search/-------------.html?wd=$encodedQuery"

                val document = fetchWithBypass(searchUrl)

                return document.select("a.module-card-item-poster").mapNotNull {
                        it.toCardSearchResult()
                }
        }

        override suspend fun load(url: String): LoadResponse? {
                val document = fetchWithBypass(url)

                val title = document.selectFirst("h1")?.text()?.trim() ?: return null

                val poster =
                        fixUrlNull(
                                document.selectFirst("div.module-info-poster img")
                                        ?.attr("data-original")
                                        ?: document.selectFirst("div.module-info-poster img")
                                                ?.attr("src")
                        )

                val description =
                        document.selectFirst("div.module-info-introduction-content")?.text()?.trim()

                val year =
                        document.select("div.module-info-item")
                                .find { it.text().contains("年份") }
                                ?.selectFirst("div.module-info-item-content")
                                ?.text()
                                ?.trim()
                                ?.toIntOrNull()

                val tags =
                        document.select("div.module-info-item")
                                .find { it.text().contains("类型") }
                                ?.select("a")
                                ?.map { it.text() }
                                ?: emptyList()

                val actors =
                        document.select("div.module-info-item")
                                .find { it.text().contains("主演") }
                                ?.select("a")
                                ?.mapNotNull {
                                        val actorName = it.text().trim()
                                        if (actorName.isNotEmpty()) ActorData(Actor(actorName))
                                        else null
                                }
                                ?: emptyList()

                val ratingText = document.selectFirst("div.module-info-item")?.text()
                val rating =
                        Regex("评分：([\\d.]+)")
                                .find(ratingText ?: "")
                                ?.groupValues
                                ?.get(1)
                                ?.toDoubleOrNull()

                val playLists = document.select("div.module-play-list")

                if (playLists.isEmpty()) {
                        return newMovieLoadResponse(title, url, TvType.Movie, "") {
                                this.posterUrl = poster
                                this.year = year
                                this.plot = description
                                this.tags = tags
                                this.actors = actors
                                this.score = rating?.let { Score.from10(it.toInt()) }
                        }
                }

                val episodes = mutableListOf<Episode>()

                // 只取第一个播放列表的集数；多线路在 loadLinks() 播放时才并行提取
                val firstPlayList = playLists[0]
                val episodeLinks = firstPlayList.select("div.module-play-list-content a")

                episodeLinks.forEachIndexed { epIndex, epLink ->
                        val epTitle = epLink.text().trim()
                        val epUrl = fixUrl(epLink.attr("href"))
                        episodes.add(
                                newEpisode(epUrl) {
                                        this.name = epTitle.ifEmpty { "第${epIndex + 1}集" }
                                        this.episode = epIndex + 1
                                        this.posterUrl = poster
                                }
                        )
                }

                val type =
                        if (episodes.size <= 1) TvType.Movie
                        else {
                                when {
                                        tags.any {
                                                it.contains("动漫", ignoreCase = true) ||
                                                        it.contains("动画", ignoreCase = true)
                                        } -> TvType.Anime
                                        tags.any { it.contains("综艺", ignoreCase = true) } ->
                                                TvType.AsianDrama
                                        else -> TvType.TvSeries
                                }
                        }

                return if (type == TvType.Movie && episodes.size == 1) {
                        newMovieLoadResponse(title, url, TvType.Movie, episodes.first().data) {
                                this.posterUrl = poster
                                this.year = year
                                this.plot = description
                                this.tags = tags
                                this.actors = actors
                                this.score = rating?.let { Score.from10(it.toInt()) }
                        }
                } else {
                        newTvSeriesLoadResponse(title, url, type, episodes) {
                                this.posterUrl = poster
                                this.year = year
                                this.plot = description
                                this.tags = tags
                                this.actors = actors
                                this.score = rating?.let { Score.from10(it.toInt()) }
                        }
                }
        }

        // ========================================================================
        // 核心：多源视频链接提取（纯 HTTP + 解密）
        // ========================================================================

        override suspend fun loadLinks(
                data: String,
                isCasting: Boolean,
                subtitleCallback: (SubtitleFile) -> Unit,
                callback: (ExtractorLink) -> Unit
        ): Boolean {
                Log.d(TAG, "loadLinks: $data")

                // 从播放页 URL 提取 vodId 和 episodeNum
                // 格式: /play/{vodId}-{sourceIndex}-{episodeNum}.html
                val playMatch = Regex("""/play/(\d+)-(\d+)-(\d+)\.html""").find(data)
                if (playMatch == null) {
                        Log.d(TAG, "无法解析播放 URL: $data")
                        // 直接尝试单源提取
                        return extractStreamFromPlayPage(data, name, callback)
                }

                val vodId = playMatch.groupValues[1]
                val episodeNum = playMatch.groupValues[3].toInt()

                Log.d(TAG, "vodId=$vodId, episodeNum=$episodeNum")

                // 获取详情页，找到所有播放源
                val detailUrl = "$mainUrl/detail/$vodId.html"
                val detailDoc = fetchWithBypass(detailUrl)

                // 提取播放源名称（从 tab 标签）
                val sourceNames = mutableListOf<String>()
                detailDoc.select(".module-tab-items-box .tab-item").forEach { el ->
                        val tabName =
                                el.attr("data-dropdown-value").ifBlank {
                                        el.text().replace(Regex("\\d+$"), "").trim()
                                }
                        sourceNames.add(tabName)
                }

                // 收集每个播放源中匹配当前集数的播放链接
                data class PlayLinkInfo(
                        val link: String,
                        val sourceName: String,
                        val resolution: String
                )

                val playLinks = mutableListOf<PlayLinkInfo>()
                detailDoc.select(".module-list").forEachIndexed { sourceIndex, listEl ->
                        val sourceName =
                                sourceNames.getOrElse(sourceIndex) { "线路${sourceIndex + 1}" }
                        listEl.select(".module-play-list-link").forEach { el ->
                                val link = el.attr("href")
                                val resolution = el.text().trim()
                                val m = Regex("""/play/(\d+)-(\d+)-(\d+)\.html""").find(link)
                                if (m != null && m.groupValues[3].toInt() == episodeNum) {
                                        playLinks.add(PlayLinkInfo(link, sourceName, resolution))
                                }
                        }
                }

                Log.d(TAG, "找到 ${playLinks.size} 个播放源 (集数=$episodeNum)")

                if (playLinks.isEmpty()) {
                        // 没找到多源，回退到直接提取当前播放页
                        return extractStreamFromPlayPage(data, name, callback)
                }

                // 并行提取每个播放源的视频流
                // coroutineScope 会等待所有 launch 子协程完成后才返回
                // AtomicBoolean 避免多协程并发写入的 race condition
                val hasAny = AtomicBoolean(false)
                coroutineScope {
                        playLinks.forEach { item ->
                                launch {
                                        try {
                                                withTimeout(10_000L) {
                                                        val playUrl = fixUrl(item.link)
                                                        val linkName =
                                                                "${item.sourceName} - ${item.resolution}"
                                                        val success =
                                                                extractStreamFromPlayPage(
                                                                        playUrl,
                                                                        linkName,
                                                                        callback
                                                                )
                                                        if (success) hasAny.set(true)
                                                }
                                        } catch (e: TimeoutCancellationException) {
                                                Log.d(TAG, "提取 ${item.sourceName} 超时")
                                        } catch (e: Exception) {
                                                Log.d(TAG, "提取 ${item.sourceName} 失败: ${e.message}")
                                        }
                                }
                        }
                }

                return hasAny.get()
        }

        /** 从单个播放页提取视频流： 播放页 → player_aaaa → POST /ddplay/api.php → 解密 → 视频 URL */
        private suspend fun extractStreamFromPlayPage(
                playPageUrl: String,
                sourceName: String,
                callback: (ExtractorLink) -> Unit
        ): Boolean {
                val playDoc = fetchWithBypass(playPageUrl)
                val playHtml = playDoc.html()

                // 提取 player_aaaa JSON（用括号计数，避免嵌套 } 截断）
                val markerIdx = playHtml.indexOf("var player_aaaa=")
                if (markerIdx == -1) {
                        Log.d(TAG, "[$sourceName] 未找到 player_aaaa")
                        return false
                }
                val jsonStart = playHtml.indexOf('{', markerIdx)
                if (jsonStart == -1) {
                        Log.d(TAG, "[$sourceName] 未找到 player_aaaa JSON 起始")
                        return false
                }
                var depth = 0
                var jsonEnd = -1
                for (i in jsonStart until playHtml.length) {
                        when (playHtml[i]) {
                                '{' -> depth++
                                '}' -> {
                                        depth--
                                        if (depth == 0) {
                                                jsonEnd = i
                                                break
                                        }
                                }
                        }
                }
                if (jsonEnd == -1) {
                        Log.d(TAG, "[$sourceName] player_aaaa JSON 括号不匹配")
                        return false
                }
                val playerJsonStr = playHtml.substring(jsonStart, jsonEnd + 1)

                val playerJson = org.json.JSONObject(playerJsonStr)
                val vid = playerJson.optString("url", "")
                if (vid.isEmpty()) {
                        Log.d(TAG, "[$sourceName] vid 为空")
                        return false
                }

                // 调用解析 API
                val apiUrl = "$mainUrl/ddplay/api.php"
                val apiRes =
                        app.post(
                                apiUrl,
                                data = mapOf("vid" to vid),
                                headers =
                                        mapOf(
                                                "Content-Type" to
                                                        "application/x-www-form-urlencoded",
                                                "Origin" to mainUrl,
                                                "Referer" to "$mainUrl/ddplay/index.php?vid=$vid",
                                                "Cookie" to globalCookie
                                        ),
                                referer = "$mainUrl/ddplay/index.php?vid=$vid"
                        )

                val apiJson =
                        try {
                                org.json.JSONObject(apiRes.text)
                        } catch (e: Exception) {
                                Log.d(TAG, "[$sourceName] API 返回非 JSON")
                                return false
                        }

                val streamData = apiJson.optJSONObject("data") ?: return false
                val urlMode = streamData.optInt("urlmode", 0)
                val encryptedUrl = streamData.optString("url", "")
                if (encryptedUrl.isEmpty()) return false

                Log.d(TAG, "[$sourceName] urlmode=$urlMode")

                // 解密
                val streamUrl =
                        when (urlMode) {
                                1 -> decodeFinalStream(encryptedUrl)
                                2 -> decodeFinalStream2(encryptedUrl)
                                else -> null
                        }

                if (streamUrl.isNullOrEmpty() || streamUrl.contains("404.mp4")) {
                        Log.d(TAG, "[$sourceName] 解密失败或 404")
                        return false
                }

                Log.d(TAG, "[$sourceName] ✅ $streamUrl")

                // 根据线路标记决定 Origin/Referer
                val streamHost =
                        when {
                                sourceName.contains("自营") -> mainUrl
                                sourceName.contains("有广") -> null
                                else ->
                                        runCatching {
                                                val u = java.net.URL(streamUrl)
                                                "${u.protocol}://${u.host}"
                                        }.getOrNull()
                        }

                // 自营线路 Referer 带 vid 参数，与 API 请求保持一致
                val streamReferer =
                        when {
                                sourceName.contains("自营") -> "$mainUrl/ddplay/index.php?vid=$vid"
                                streamHost != null -> "$streamHost/"
                                else -> null
                        }

                callback.invoke(
                        newExtractorLink(
                                name = sourceName,
                                source = this.name,
                                url = streamUrl,
                                type = INFER_TYPE
                        ) {
                                if (streamHost != null) {
                                        this.referer = streamReferer ?: "$streamHost/"
                                        this.headers =
                                                mapOf(
                                                        "Origin" to streamHost,
                                                        "Accept" to "*/*",
                                                        "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8"
                                                )
                                } else {
                                        this.headers =
                                                mapOf(
                                                        "Accept" to "*/*",
                                                        "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8"
                                                )
                                }
                        }
                )
                return true
        }

        // ========================================================================
        // 反爬绕过：fetchWithBypass
        // ========================================================================

        /** 带反爬绕过的页面请求。 当检测到 robot.php 挑战页面时，自动解决并重试。 */
        private suspend fun fetchWithBypass(url: String): org.jsoup.nodes.Document {
                val headers =
                        mutableMapOf(
                                "Accept" to
                                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
                                "Accept-Language" to "en-US,en;q=0.9",
                                "Connection" to "keep-alive"
                        )
                if (globalCookie.isNotEmpty()) {
                        headers["Cookie"] = globalCookie
                }

                var res = app.get(url, headers = headers)

                // 更新 Cookie
                updateCookies(res.headers)

                val html = res.text

                if (res.code == 200 && html.contains("robot.php")) {
                        Log.d(TAG, "检测到反爬挑战页面")

                        // Challenge 1: staticchars + token + encrypt
                        val staticMatch = Regex("""var\s+staticchars\s*=\s*'([^']+)'""").find(html)
                        val tokenMatch1 = Regex("""var\s+token\s*=\s*'([^']+)'""").find(html)
                        // Challenge 2: encrypt2 方式
                        val tokenMatch2 =
                                Regex("""var\s+token\s*=\s*encrypt\("([^"]+)"\);""").find(html)

                        if (staticMatch != null &&
                                        tokenMatch1 != null &&
                                        html.contains("math.random")
                        ) {
                                // Challenge 1
                                val staticchars = staticMatch.groupValues[1]
                                val token = tokenMatch1.groupValues[1]
                                val p = encrypt(staticchars, token)
                                val verificationUrl = "$mainUrl/static/js/robot.php?p=$p&$token="

                                Log.d(TAG, "Challenge1: 发送验证请求")
                                val verifyHeaders =
                                        mutableMapOf("Referer" to url, "Cookie" to globalCookie)
                                val verifyRes = app.get(verificationUrl, headers = verifyHeaders)
                                updateCookies(verifyRes.headers)

                                // 重试原请求
                                headers["Cookie"] = globalCookie
                                res = app.get(url, headers = headers)
                                updateCookies(res.headers)
                        } else if (tokenMatch2 != null) {
                                // Challenge 2
                                val tokenRaw = tokenMatch2.groupValues[1]
                                val value = encrypt2(url)
                                val token = encrypt2(tokenRaw)

                                Log.d(TAG, "Challenge2: 发送 POST 验证请求")
                                val postData = mapOf("value" to value, "token" to token)
                                val verifyHeaders =
                                        mutableMapOf(
                                                "Content-Type" to
                                                        "application/x-www-form-urlencoded",
                                                "Cookie" to globalCookie,
                                                "Referer" to url
                                        )
                                val verifyRes =
                                        app.post(
                                                "$mainUrl/robot.php",
                                                data = postData,
                                                headers = verifyHeaders
                                        )
                                updateCookies(verifyRes.headers)

                                // 重试原请求
                                headers["Cookie"] = globalCookie
                                res = app.get(url, headers = headers)
                                updateCookies(res.headers)
                        }
                }

                return res.document
        }

        /** 从响应头中提取并更新全局 Cookie */
        private fun updateCookies(headers: okhttp3.Headers) {
                val setCookies = headers.values("Set-Cookie")
                if (setCookies.isNotEmpty()) {
                        val newCookies = setCookies.map { it.split(";")[0] }.joinToString("; ")
                        // 合并已有的 cookie
                        val existingMap = mutableMapOf<String, String>()
                        if (globalCookie.isNotEmpty()) {
                                globalCookie.split("; ").forEach { part ->
                                        val eqIdx = part.indexOf('=')
                                        if (eqIdx > 0) {
                                                existingMap[part.substring(0, eqIdx)] =
                                                        part.substring(eqIdx + 1)
                                        }
                                }
                        }
                        setCookies.forEach { raw ->
                                val cookiePart = raw.split(";")[0]
                                val eqIdx = cookiePart.indexOf('=')
                                if (eqIdx > 0) {
                                        existingMap[cookiePart.substring(0, eqIdx)] =
                                                cookiePart.substring(eqIdx + 1)
                                }
                        }
                        globalCookie =
                                existingMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
                }
        }

        // ========================================================================
        // 加密函数（用于反爬绕过）
        // ========================================================================

        /** Challenge 1 加密：MD5 + XOR + Base64 */
        @OptIn(ExperimentalEncodingApi::class)
        private fun encrypt(txt: String, key: String): String {
                val nh = (Math.random() * 64).toInt()
                val ch = BASE64_CHARS[nh]
                val mdKey = md5(key + ch)
                val startIdx = nh % 8
                val length = if (nh % 8 > 7) nh % 8 else nh % 8 + 17
                val subKey =
                        mdKey.substring(startIdx, (startIdx + length).coerceAtMost(mdKey.length))

                val txtBase64 = Base64.encode(txt.toByteArray(Charsets.UTF_8))
                val sb = StringBuilder()
                var k = 0
                for (c in txtBase64) {
                        if (k == subKey.length) k = 0
                        val xored = c.code xor subKey[k].code
                        k++
                        sb.append(xored.toChar())
                }
                val result = ch + Base64.encode(sb.toString().toByteArray(Charsets.ISO_8859_1))
                return URLEncoder.encode(result, "UTF-8")
        }

        /** Challenge 2 加密：自定义字母表替换 + 随机填充 + Base64 */
        @OptIn(ExperimentalEncodingApi::class)
        private fun encrypt2(str: String): String {
                val sb = StringBuilder()
                for (c in str) {
                        val idx = STATIC_CHARS.indexOf(c)
                        val code = if (idx == -1) c else STATIC_CHARS[(idx + 3) % 62]
                        val r1 = (Math.random() * 62).toInt()
                        val r2 = (Math.random() * 62).toInt()
                        sb.append(STATIC_CHARS[r1])
                        sb.append(code)
                        sb.append(STATIC_CHARS[r2])
                }
                return Base64.encode(sb.toString().toByteArray(Charsets.UTF_8))
        }

        // ========================================================================
        // 解密函数（用于视频 URL 解密）
        // ========================================================================

        /** MD5 哈希 */
        private fun md5(input: String): String {
                val digest = MessageDigest.getInstance("MD5")
                val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
                return bytes.joinToString("") { "%02x".format(it) }
        }

        /** decode1: 第一层解密 Base64 解码 → XOR(MD5("test")) → Base64 解码 */
        @OptIn(ExperimentalEncodingApi::class)
        private fun decode1(cipherStr: String): String {
                val key = md5("test")
                // Base64 解码为 binary string（ISO_8859_1 保留原始字节）
                val decoded1 = String(Base64.decode(cipherStr), Charsets.ISO_8859_1)
                val sb = StringBuilder()
                for (i in decoded1.indices) {
                        val k = i % key.length
                        val xored = decoded1[i].code xor key[k].code
                        sb.append(xored.toChar())
                }
                // 再做一次 Base64 解码得到 UTF-8 文本
                return String(Base64.decode(sb.toString()), Charsets.UTF_8)
        }

        /** decodeFinalStream (urlmode=1): decode1 → 按 "/" 分割 → Base64 解码各部分 → 字母替换映射 → 真实 URL */
        @OptIn(ExperimentalEncodingApi::class)
        private fun decodeFinalStream(input: String): String? {
                return try {
                        val decoded = decode1(input)
                        val parts = decoded.split("/")
                        if (parts.size < 3) return null

                        val arr1 = JSONArray(String(Base64.decode(parts[0]), Charsets.UTF_8))
                        val arr2 = JSONArray(String(Base64.decode(parts[1]), Charsets.UTF_8))
                        val cipherUrl = String(Base64.decode(parts[2]), Charsets.UTF_8)

                        // 构建映射表：arr2[i] -> arr1[i]
                        val charMap = mutableMapOf<Char, Char>()
                        for (i in 0 until arr2.length().coerceAtMost(arr1.length())) {
                                val from = arr2.getString(i)
                                val to = arr1.getString(i)
                                if (from.length == 1 && to.length == 1) {
                                        charMap[from[0]] = to[0]
                                }
                        }

                        // 替换密文中的字母
                        val sb = StringBuilder()
                        for (c in cipherUrl) {
                                if (c.isLetter()) {
                                        sb.append(charMap[c] ?: c)
                                } else {
                                        sb.append(c)
                                }
                        }
                        sb.toString()
                } catch (e: Exception) {
                        Log.d(TAG, "decodeFinalStream 解密失败: ${e.message}")
                        null
                }
        }

        /** decodeFinalStream2 (urlmode=2): Base64 解码 → 每3字符取中间字符 → 用静态字母表逆映射(偏移59, mod62) */
        @OptIn(ExperimentalEncodingApi::class)
        private fun decodeFinalStream2(input: String): String? {
                return try {
                        val decoded = String(Base64.decode(input), Charsets.ISO_8859_1)
                        val sb = StringBuilder()
                        var i = 1
                        while (i < decoded.length) {
                                val c = decoded[i]
                                val idx = STATIC_CHARS.indexOf(c)
                                if (idx == -1) {
                                        sb.append(c)
                                } else {
                                        sb.append(STATIC_CHARS[(idx + 59) % 62])
                                }
                                i += 3
                        }
                        sb.toString()
                } catch (e: Exception) {
                        Log.d(TAG, "decodeFinalStream2 解密失败: ${e.message}")
                        null
                }
        }
}
