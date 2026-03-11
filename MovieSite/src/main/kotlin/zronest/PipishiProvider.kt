package zronest

import android.util.Base64
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLEncoder
import java.security.MessageDigest
import org.json.JSONObject
import org.jsoup.nodes.Document

/** 皮皮狮 - 基于 HTTP 纯解密 API 解析 */
open class PipishiProvider : MainAPI() {
    override var mainUrl = "https://www.pipishi.fun"
    override var name = "皮皮狮"

    override val hasMainPage = true
    override var lang = "zh"

    // 备用域名：https://www.pipishi.pro, https://www.pipishi.me

    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    override val mainPage = mainPageOf("1" to "电影", "2" to "剧集", "3" to "综艺", "4" to "动漫")

    companion object {
        private const val TAG = "PipishiProvider"

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
    // 主页 / 搜索 / 详情加载
    // ========================================================================

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val typeId = request.data
        // Catalog: /list/{typeId}--------{page}---.html
        val pageUrl = "$mainUrl/list/$typeId--------$page---.html"

        val document = fetchWithBypass(pageUrl)

        val home =
                document.select("a.module-poster-item, a.module-item").mapNotNull {
                    val title =
                            it.attr("title").ifEmpty {
                                return@mapNotNull null
                            }
                    val href = fixUrl(it.attr("href"))
                    val posterUrl =
                            fixUrlNull(
                                    it.selectFirst("img")?.attr("data-original")
                                            ?: it.selectFirst("img")?.attr("data-src")
                            )

                    val type =
                            when {
                                href.contains("/vod/1") -> TvType.Movie
                                href.contains("/vod/2") -> TvType.TvSeries
                                href.contains("/vod/3") -> TvType.TvSeries
                                href.contains("/vod/4") -> TvType.Anime
                                else -> TvType.TvSeries
                            }

                    if (type == TvType.Movie) {
                        newMovieSearchResponse(title, href, TvType.Movie) {
                            this.posterUrl = posterUrl
                        }
                    } else {
                        newTvSeriesSearchResponse(title, href, type) { this.posterUrl = posterUrl }
                    }
                }

        return newHomePageResponse(
                listOf(HomePageList(request.name, home)),
                hasNext = home.isNotEmpty()
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$mainUrl/search/-------------.html?wd=$encodedQuery"

        return try {
            val doc = fetchWithBypass(searchUrl)
            doc.select("a.module-poster-item, a.module-item, .module-card-item").mapNotNull { el ->
                var title: String? = null
                var href: String? = null
                var posterUrl: String? = null

                if (el.`is`("a")) {
                    href = el.attr("href")
                    title = el.attr("title")
                    posterUrl =
                            el.selectFirst("img")?.attr("data-original")
                                    ?: el.selectFirst("img")?.attr("data-src")
                } else {
                    val titleEl = el.selectFirst(".module-card-item-title a") ?: el.selectFirst("a")
                    title = titleEl?.text()?.trim() ?: titleEl?.attr("title")
                    href = titleEl?.attr("href")
                    posterUrl =
                            el.selectFirst("img")?.attr("data-original")
                                    ?: el.selectFirst("img")?.attr("data-src")
                }

                if (title.isNullOrEmpty() || href.isNullOrEmpty()) return@mapNotNull null

                href = fixUrl(href)
                posterUrl = fixUrlNull(posterUrl)

                val typeClass =
                        el.selectFirst(".module-card-item-class, .module-item-note")?.text()?.trim()
                val type =
                        if (typeClass == "电影" || typeClass == "正片") TvType.Movie
                        else TvType.TvSeries

                if (type == TvType.Movie) {
                    newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
                } else {
                    newTvSeriesSearchResponse(title, href, type) { this.posterUrl = posterUrl }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = fetchWithBypass(url)

        val title = doc.selectFirst("h1")?.text()?.trim() ?: return null
        val playLink = url // Store the URL as data for loadLinks

        val posterUrl =
                fixUrlNull(
                        doc.selectFirst(".module-item-pic img")?.attr("data-original")
                                ?: doc.selectFirst(".module-item-pic img")?.attr("data-src")
                )

        val plot =
                doc.selectFirst(".module-info-introduction-content p")?.text()?.trim()
                        ?: doc.selectFirst(".module-info-introduction-content")?.text()?.trim()

        val episodes = mutableListOf<Episode>()
        val firstList = doc.selectFirst(".module-list")

        firstList?.select("a")?.forEach { el ->
            val link = el.attr("href")
            val epTitle = el.text().trim()
            val match = Regex("""/play/(\d+)-(\d+)-(\d+)\.html""").find(link)
            if (match != null) {
                val epNum = match.groupValues[3].toInt()
                if (episodes.none { it.episode == epNum }) {
                    episodes.add(
                            newEpisode(url + "|||" + epNum) { // Pack detail and epNum
                                this.name = epTitle
                                this.episode = epNum
                            }
                    )
                }
            }
        }

        val isSeries =
                episodes.size > 1 || (episodes.size == 1 && episodes[0].name?.contains("集") == true)
        val tvType = if (isSeries) TvType.TvSeries else TvType.Movie

        if (isSeries) {
            return newTvSeriesLoadResponse(title, playLink, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
                this.plot = plot
            }
        } else {
            return newMovieLoadResponse(title, playLink, TvType.Movie, url + "|||1") {
                this.posterUrl = posterUrl
                this.plot = plot
            }
        }
    }

    // ========================================================================
    // 多源视频链接提取（纯 HTTP + 解密）
    // ========================================================================

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        // 解包 detailUrl 和 episodeNum
        val parts = data.split("|||")
        if (parts.size != 2) return false

        val detailUrl = parts[0]
        val episodeNum = parts[1].toInt()

        Log.d(TAG, "Fetching detail page for multi-source: $detailUrl, episode=$episodeNum")
        val detailDoc = fetchWithBypass(detailUrl)

        val sourceNames = mutableListOf<String>()
        detailDoc.select(".module-tab-items-box .tab-item, .module-tab-item").forEach { el ->
            val tabName =
                    el.attr("data-dropdown-value").ifEmpty {
                        el.text().replace(Regex("\\d+$"), "").trim()
                    }
            sourceNames.add(tabName)
        }

        data class PlayLinkInfo(val link: String, val sourceName: String, val resolution: String)

        val playLinks = mutableListOf<PlayLinkInfo>()
        detailDoc.select(".module-list, .module-play-list").forEachIndexed { sourceIndex, listEl ->
            val sourceName = sourceNames.getOrElse(sourceIndex) { "线路${sourceIndex + 1}" }
            listEl.select("a").forEach { el ->
                val link = el.attr("href")
                val resolution = el.text().trim()
                val m = Regex("""/play/(\d+)-(\d+)-(\d+)\.html""").find(link)
                if (m != null && m.groupValues[3].toInt() == episodeNum) {
                    playLinks.add(PlayLinkInfo(link, sourceName, resolution))
                }
            }
        }

        Log.d(TAG, "Found ${playLinks.size} play links for episode $episodeNum")

        var hasAny = false
        for (item in playLinks) {
            try {
                val playUrl = fixUrl(item.link)
                val linkName = "${item.sourceName} - ${item.resolution}"
                val success = extractStreamFromPlayPage(playUrl, linkName, callback)
                if (success) hasAny = true
            } catch (e: Exception) {
                Log.d(TAG, "Extract ${item.sourceName} failed: ${e.message}")
            }
        }

        return hasAny
    }

    private suspend fun extractStreamFromPlayPage(
            playPageUrl: String,
            sourceName: String,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        val playDoc = fetchWithBypass(playPageUrl)
        val playHtml = playDoc.html()

        val playerMatch = Regex("""var player_aaaa=(\{.*?\})</script>""").find(playHtml)
        if (playerMatch == null) return false

        val playerJson = org.json.JSONObject(playerMatch.groupValues[1])
        val vid = playerJson.optString("url", "")
        if (vid.isEmpty()) return false

        val apiUrl = "$mainUrl/lionplay/api.php"
        val apiRes =
                app.post(
                        apiUrl,
                        data = mapOf("vid" to vid),
                        headers =
                                mapOf(
                                        "Content-Type" to "application/x-www-form-urlencoded",
                                        "Origin" to mainUrl,
                                        "Referer" to "$mainUrl/lionplay/index.php?vid=$vid",
                                        "Cookie" to globalCookie
                                ),
                        referer = "$mainUrl/lionplay/index.php?vid=$vid"
                )

        val apiJson =
                try {
                    org.json.JSONObject(apiRes.text)
                } catch (e: Exception) {
                    return false
                }

        val streamData = apiJson.optJSONObject("data") ?: return false
        val urlMode = streamData.optInt("urlmode", 0)
        val encryptedUrl = streamData.optString("url", "")
        if (encryptedUrl.isEmpty()) return false

        var streamUrl: String? = null
        if (urlMode == 1) {
            streamUrl = decodeFinalStream(encryptedUrl)
        } else if (urlMode == 2) {
            streamUrl = decodeFinalStream2(encryptedUrl)
        } else if (encryptedUrl.startsWith("http")) {
            streamUrl = encryptedUrl
        }

        if (streamUrl.isNullOrEmpty() || streamUrl.contains("404.mp4")) return false

        Log.d(TAG, "[$sourceName] ✅ $streamUrl")

        callback.invoke(
                newExtractorLink(
                        name = sourceName,
                        source = this.name,
                        url = streamUrl,
                        type = INFER_TYPE
                ) {
                    this.referer = "$mainUrl/lionplay/index.php?vid=$vid"
                    this.headers =
                            mapOf(
                                    "Origin" to mainUrl,
                                    "Accept" to "*/*",
                                    "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
                                    "User-Agent" to
                                            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                            )
                }
        )
        return true
    }

    // ========================================================================
    // 反爬绕过相关 (fetchWithBypass, encrypt, encrypt2, decodes)
    // ========================================================================

    private suspend fun fetchWithBypass(url: String): Document {
        Log.d(TAG, "Fetching: $url")
        val headers =
                mutableMapOf(
                        "User-Agent" to
                                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
                        "Accept" to
                                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
                        "Accept-Language" to "en-US,en;q=0.9",
                )
        if (globalCookie.isNotEmpty()) {
            headers["Cookie"] = globalCookie
        }

        var res = app.get(url, headers = headers, allowRedirects = true)

        var cookieList = res.okhttpResponse.headers("set-cookie")
        if (cookieList.isNotEmpty()) {
            globalCookie = cookieList.joinToString("; ") { it.substringBefore(";") }
            headers["Cookie"] = globalCookie
            Log.d(TAG, "Updated global cookie: $globalCookie")
        }

        val htmlText = res.text
        if (res.code == 200 && htmlText.contains("robot.php")) {
            Log.d(TAG, "触发反爬保护: $url")

            val staticMatch1 = Regex("""var\s+staticchars\s*=\s*'([^']+)'""").find(htmlText)
            val tokenMatch1 = Regex("""var\s+token\s*=\s*'([^']+)'""").find(htmlText)
            val isChallenge1 =
                    staticMatch1 != null &&
                            tokenMatch1 != null &&
                            htmlText.contains("math.random", ignoreCase = true)

            val staticMatch2 = Regex("""var\s+staticchars\s*=\s*"([^"]+)"""").find(htmlText)
            val tokenMatch2 = Regex("""var\s+token\s*=\s*encrypt\("([^"]+)"\);""").find(htmlText)
            val isChallenge2 = staticMatch2 != null && tokenMatch2 != null

            if (isChallenge1) {
                Log.d(TAG, "检测到挑战机制 1")
                val staticchars = staticMatch1!!.groupValues[1]
                val token = tokenMatch1!!.groupValues[1]
                val p = encrypt(staticchars, token)
                val verificationUrl = "$mainUrl/static/js/robot.php?p=$p&$token="

                val verifyRes = app.get(verificationUrl, headers = headers, referer = url)

                cookieList = verifyRes.okhttpResponse.headers("set-cookie")
                if (cookieList.isNotEmpty()) {
                    globalCookie = cookieList.joinToString("; ") { it.substringBefore(";") }
                    headers["Cookie"] = globalCookie
                }

                res = app.get(url, headers = headers)
                Log.d(TAG, "挑战机制 1 已绕过")
            } else if (isChallenge2) {
                Log.d(TAG, "检测到挑战机制 2")
                val tokenRaw = tokenMatch2!!.groupValues[1]
                val staticchars = staticMatch2!!.groupValues[1]

                val value = encrypt2(url, staticchars)
                val tokenEnc = encrypt2(tokenRaw, staticchars)

                val postHeaders = headers.toMutableMap()
                postHeaders["Content-Type"] = "application/x-www-form-urlencoded"
                postHeaders["Referer"] = url

                val verifyRes =
                        app.post(
                                "$mainUrl/robot.php",
                                data = mapOf("value" to value, "token" to tokenEnc),
                                headers = postHeaders,
                                referer = url
                        )

                cookieList = verifyRes.okhttpResponse.headers("set-cookie")
                if (cookieList.isNotEmpty()) {
                    globalCookie = cookieList.joinToString("; ") { it.substringBefore(";") }
                    headers["Cookie"] = globalCookie
                }

                res = app.get(url, headers = headers)
                Log.d(TAG, "挑战机制 2 已绕过")
            }
        }

        return res.document
    }

    /** 挑战机制 1: MD5 + XOR + Base64 混淆加密 */
    private fun encrypt(txt: String, key: String): String {
        val nh = (Math.random() * 64).toInt()
        val ch = BASE64_CHARS[nh]

        val md = MessageDigest.getInstance("MD5")
        md.update((key + ch).toByteArray(Charsets.UTF_8))
        var mdKey = md.digest().joinToString("") { "%02x".format(it) }

        val start = nh % 8
        val len = if (nh % 8 > 7) nh % 8 else nh % 8 + 17
        mdKey = mdKey.substring(start, start + len)

        val txtBase64 = String(Base64.encode(txt.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))

        val tmp = StringBuilder()
        var k = 0
        for (i in txtBase64.indices) {
            k = if (k == mdKey.length) 0 else k
            val charCode = txtBase64[i].code xor mdKey[k++].code
            tmp.append(charCode.toChar())
        }

        val finalBase64 =
                String(Base64.encode(tmp.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
        return URLEncoder.encode(ch + finalBase64, "UTF-8")
    }

    /** 挑战机制 2: 自定义字母表替换并混入随机字符 */
    private fun encrypt2(str: String, staticchars: String): String {
        val encodechars = StringBuilder()
        for (i in str.indices) {
            val num0 = staticchars.indexOf(str[i])
            val code = if (num0 == -1) str[i] else staticchars[(num0 + 3) % 62]
            val num1 = (Math.random() * 62).toInt()
            val num2 = (Math.random() * 62).toInt()
            encodechars.append(staticchars[num1]).append(code).append(staticchars[num2])
        }
        return String(
                Base64.encode(encodechars.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        )
    }

    private fun decode1(cipherStr: String): String {
        val md = MessageDigest.getInstance("MD5")
        md.update("test".toByteArray(Charsets.UTF_8))
        val key = md.digest().joinToString("") { "%02x".format(it) }

        val decoded1 = String(Base64.decode(cipherStr, Base64.NO_WRAP), Charsets.ISO_8859_1)
        val code = StringBuilder()
        for (i in decoded1.indices) {
            val k = i % key.length
            code.append((decoded1[i].code xor key[k].code).toChar())
        }
        return String(Base64.decode(code.toString(), Base64.NO_WRAP), Charsets.UTF_8)
    }

    private fun decodeFinalStream(input: String): String? {
        val out = decode1(input)
        val parts = out.split('/')
        if (parts.size < 3) return null

        return try {
            val arr1Text = String(Base64.decode(parts[0], Base64.NO_WRAP), Charsets.UTF_8)
            val arr2Text = String(Base64.decode(parts[1], Base64.NO_WRAP), Charsets.UTF_8)
            val cipherUrl = String(Base64.decode(parts[2], Base64.NO_WRAP), Charsets.UTF_8)

            val arr1 = JSONObject("{\"data\":$arr1Text}").getJSONArray("data")
            val arr2 = JSONObject("{\"data\":$arr2Text}").getJSONArray("data")

            val arr2List = (0 until arr2.length()).map { arr2.getString(it) }

            val realUrl = java.lang.StringBuilder()
            for (c in cipherUrl) {
                if (c.toString().matches(Regex("^[a-zA-Z]$"))) {
                    val idx = arr2List.indexOf(c.toString())
                    if (idx != -1) {
                        realUrl.append(arr1.getString(idx))
                    } else {
                        realUrl.append(c)
                    }
                } else {
                    realUrl.append(c)
                }
            }
            realUrl.toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeFinalStream2(input: String): String? {
        return try {
            val decoded = String(Base64.decode(input, Base64.NO_WRAP), Charsets.ISO_8859_1)
            val res = java.lang.StringBuilder()
            for (i in 1 until decoded.length step 3) {
                val idx = STATIC_CHARS.indexOf(decoded[i])
                if (idx == -1) {
                    res.append(decoded[i])
                } else {
                    res.append(STATIC_CHARS[(idx + 59) % 62])
                }
            }
            res.toString()
        } catch (e: Exception) {
            null
        }
    }
}
