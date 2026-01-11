package zronest

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLEncoder
import org.jsoup.nodes.Element

/** 达达趣 - https://www.dadaqu.pro/ */
class DadaquProvider : MainAPI() {
        override var mainUrl = "https://www.dadaqu.pro"
        override var name = "达达趣"
        override var lang = "zh"
        override val hasMainPage = true
        override val supportedTypes =
                setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

        override val mainPage =
                mainPageOf(
                        "$mainUrl/type/1.html" to "电影",
                        "$mainUrl/type/2.html" to "电视剧",
                        "$mainUrl/type/3.html" to "综艺",
                        "$mainUrl/type/4.html" to "动漫"
                )

        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
                val pageUrl =
                        if (page == 1) {
                                request.data
                        } else {
                                request.data.replace(".html", "-$page.html")
                        }

                val document = app.get(pageUrl, referer = mainUrl).document

                val home =
                        document.select("a.module-poster-item").mapNotNull { it.toSearchResult() }

                return newHomePageResponse(
                        listOf(HomePageList(request.name, home)),
                        hasNext = home.isNotEmpty()
                )
        }

        private fun Element.toSearchResult(): SearchResponse? {
                // 使用 title 属性或 img 的 alt 属性获取真正的标题
                // 而不是 div 文本（那个会是状态标签如"完结"、"正片"）
                val title =
                        this.attr("title").ifEmpty { this.selectFirst("img")?.attr("alt") }?.trim()
                                ?: return null

                val href = fixUrl(this.attr("href"))
                val posterUrl =
                        fixUrlNull(
                                this.selectFirst("img")?.attr("data-original")
                                        ?: this.selectFirst("img")?.attr("src")
                        )

                // 根据 URL 判断类型
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

                // 从详情页判断类型，这里先默认为电影
                return newMovieSearchResponse(title, href, TvType.Movie) {
                        this.posterUrl = posterUrl
                }
        }

        override suspend fun search(query: String): List<SearchResponse> {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val searchUrl = "$mainUrl/search/${encodedQuery}-------------.html"

                val document = app.get(searchUrl, referer = mainUrl).document

                return document.select("a.module-card-item-poster").mapNotNull {
                        it.toCardSearchResult()
                }
        }

        override suspend fun load(url: String): LoadResponse? {
                val document = app.get(url, referer = mainUrl).document

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

                // 提取年份
                val year =
                        document.select("div.module-info-item")
                                .find { it.text().contains("年份") }
                                ?.selectFirst("div.module-info-item-content")
                                ?.text()
                                ?.trim()
                                ?.toIntOrNull()

                // 提取标签
                val tags =
                        document.select("div.module-info-item")
                                .find { it.text().contains("类型") }
                                ?.select("a")
                                ?.map { it.text() }
                                ?: emptyList()

                // 提取演员
                val actors =
                        document.select("div.module-info-item")
                                .find { it.text().contains("主演") }
                                ?.select("a")
                                ?.mapNotNull {
                                        val name = it.text().trim()
                                        if (name.isNotEmpty()) ActorData(Actor(name)) else null
                                }
                                ?: emptyList()

                // 提取评分
                val ratingText = document.selectFirst("div.module-info-item")?.text()
                val rating =
                        Regex("评分：([\\d.]+)")
                                .find(ratingText ?: "")
                                ?.groupValues
                                ?.get(1)
                                ?.toDoubleOrNull()

                // 提取播放源和剧集
                val playLists = document.select("div.module-play-list")

                if (playLists.isEmpty()) {
                        // 没有播放源，返回基本信息
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

                playLists.forEachIndexed { sourceIndex, playList ->
                        val sourceName =
                                playList.selectFirst("div.module-play-list-title")?.text()?.trim()
                                        ?: "播放源${sourceIndex + 1}"

                        val episodeLinks = playList.select("div.module-play-list-content a")

                        episodeLinks.forEachIndexed { epIndex, epLink ->
                                val epTitle = epLink.text().trim()
                                val epUrl = fixUrl(epLink.attr("href"))

                                episodes.add(
                                        newEpisode(epUrl) {
                                                // 使用实际的剧集名称，而不是播放源名称
                                                this.name =
                                                        if (episodeLinks.size == 1 &&
                                                                        epTitle.isNotEmpty()
                                                        ) {
                                                                epTitle // 单集时直接用链接文本（如"正片"、"HD"等）
                                                        } else if (epTitle.isNotEmpty()) {
                                                                "$epTitle ($sourceName)" // 多集时显示
                                                                // "第01集
                                                                // (高清线路)"
                                                        } else {
                                                                sourceName
                                                        }
                                                this.episode = epIndex + 1
                                                this.posterUrl = poster
                                        }
                                )
                        }
                }

                // 判断类型：单集为电影，多集为剧集
                val type =
                        if (episodes.size <= 1) TvType.Movie
                        else {
                                // 根据标签判断更具体的类型
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

        override suspend fun loadLinks(
                data: String,
                isCasting: Boolean,
                subtitleCallback: (SubtitleFile) -> Unit,
                callback: (ExtractorLink) -> Unit
        ): Boolean {
                Log.d("DadaquProvider", "Loading links for: $data")

                // 直接在播放页面上操作，而不是加载 iframe
                // 因为 iframe URL 有防盗链保护
                val webViewResolver =
                        WebViewResolver(
                                script =
                                        """
                                        (function() {
                                            console.log('[DadaquProvider] Script started');
                                            var foundUrl = null;
                                            var attempts = 0;
                                            var maxAttempts = 80; // 40 seconds
                                            
                                            // 等待页面完全加载
                                            setTimeout(function() {
                                                console.log('[DadaquProvider] Closing popup first...');
                                                closePopup();
                                                
                                                // 关闭弹窗后等待一下再触发播放
                                                setTimeout(function() {
                                                    console.log('[DadaquProvider] Attempting to trigger play...');
                                                    triggerPlay();
                                                }, 500);
                                            }, 3000);
                                            
                                            function closePopup() {
                                                // 尝试点击弹窗关闭按钮
                                                var popupCloseSelectors = [
                                                    '.close-pop',
                                                    '#popup .popup-btn',
                                                    '.popup-footer .popup-btn'
                                                ];
                                                
                                                for (var i = 0; i < popupCloseSelectors.length; i++) {
                                                    var closeBtn = document.querySelector(popupCloseSelectors[i]);
                                                    if (closeBtn) {
                                                        try {
                                                            console.log('[DadaquProvider] Clicking popup close button:', popupCloseSelectors[i]);
                                                            closeBtn.click();
                                                            return;
                                                        } catch(e) {
                                                            console.log('[DadaquProvider] Error clicking popup:', e);
                                                        }
                                                    }
                                                }
                                                
                                                // 如果找不到按钮，尝试隐藏弹窗
                                                var popup = document.querySelector('#popup');
                                                if (popup) {
                                                    try {
                                                        console.log('[DadaquProvider] Hiding popup directly');
                                                        popup.style.display = 'none';
                                                        popup.classList.remove('popupShow');
                                                    } catch(e) {
                                                        console.log('[DadaquProvider] Error hiding popup:', e);
                                                    }
                                                }
                                            }
                                            
                                            function triggerPlay() {
                                                // 方法 1: 尝试通过 ArtPlayer API 播放
                                                if (window.art) {
                                                    try {
                                                        console.log('[DadaquProvider] Found ArtPlayer, calling play()');
                                                        window.art.play();
                                                    } catch(e) {
                                                        console.log('[DadaquProvider] ArtPlayer play error:', e);
                                                    }
                                                }
                                                
                                                // 方法 2: 点击播放按钮和容器（优先点击 #start）
                                                var clickTargets = [
                                                    '#start',           // 主播放按钮
                                                    '.content',         // 内容区域
                                                    '.art-video-player',
                                                    '.art-control-play',
                                                    '#player',
                                                    'video',
                                                    'body'
                                                ];
                                                
                                                for (var i = 0; i < clickTargets.length; i++) {
                                                    var elem = document.querySelector(clickTargets[i]);
                                                    if (elem) {
                                                        try {
                                                            console.log('[DadaquProvider] Clicking:', clickTargets[i]);
                                                            elem.click();
                                                            var evt = new MouseEvent('click', {
                                                                bubbles: true,
                                                                cancelable: true,
                                                                view: window
                                                            });
                                                            elem.dispatchEvent(evt);
                                                        } catch(e) {
                                                            console.log('[DadaquProvider] Click error:', e);
                                                        }
                                                    }
                                                }
                                                
                                                // 方法 3: 直接调用 video.play()
                                                var video = document.querySelector('video');
                                                if (video) {
                                                    try {
                                                        console.log('[DadaquProvider] Calling video.play()');
                                                        video.play();
                                                    } catch(e) {
                                                        console.log('[DadaquProvider] video.play error:', e);
                                                    }
                                                }
                                            }
                                            
                                            // 定期检查视频 URL
                                            var checkInterval = setInterval(function() {
                                                attempts++;
                                                
                                                // 方法 1: 检查 ArtPlayer 配置
                                                if (window.art && window.art.option && window.art.option.url) {
                                                    var url = window.art.option.url;
                                                    if (url && url.startsWith('http')) {
                                                        clearInterval(checkInterval);
                                                        console.log('[DadaquProvider] Found URL in ArtPlayer:', url);
                                                        window.location = 'cloudstream-intercept://' + url;
                                                        return;
                                                    }
                                                }
                                                
                                                // 方法 2: 检查 video.src
                                                var video = document.querySelector('video');
                                                if (video) {
                                                    var src = video.src || video.currentSrc;
                                                    if (src && src.startsWith('http')) {
                                                        clearInterval(checkInterval);
                                                        console.log('[DadaquProvider] Found video.src:', src);
                                                        window.location = 'cloudstream-intercept://' + src;
                                                        return;
                                                    }
                                                }
                                                
                                                // 方法 3: 检查 source 元素
                                                if (video) {
                                                    var sources = video.querySelectorAll('source');
                                                    for (var i = 0; i < sources.length; i++) {
                                                        var src = sources[i].src;
                                                        if (src && src.startsWith('http')) {
                                                            clearInterval(checkInterval);
                                                            console.log('[DadaquProvider] Found source.src:', src);
                                                            window.location = 'cloudstream-intercept://' + src;
                                                            return;
                                                        }
                                                    }
                                                }
                                                
                                                // 超时处理
                                                if (attempts >= maxAttempts) {
                                                    clearInterval(checkInterval);
                                                    console.log('[DadaquProvider] TIMEOUT after', attempts, 'attempts');
                                                    console.log('[DadaquProvider] Debug - window.art:', !!window.art);
                                                    if (window.art && window.art.option) {
                                                        console.log('[DadaquProvider] Debug - art.option.url:', window.art.option.url);
                                                    }
                                                    console.log('[DadaquProvider] Debug - video element:', !!video);
                                                    if (video) {
                                                        console.log('[DadaquProvider] Debug - video.src:', video.src);
                                                        console.log('[DadaquProvider] Debug - video.currentSrc:', video.currentSrc);
                                                    }
                                                    window.location = 'cloudstream-intercept://TIMEOUT';
                                                }
                                            }, 500);
                                        })();
                                        """.trimIndent(),
                                interceptUrl = Regex("""cloudstream-intercept://(.*)"""),
                                timeout = 60_000L
                        )

                val interceptedUrl =
                        app.get(data, referer = mainUrl, interceptor = webViewResolver)
                                .url
                                .removePrefix("cloudstream-intercept://")

                if (interceptedUrl.isNotEmpty() &&
                                interceptedUrl != "TIMEOUT" &&
                                interceptedUrl.startsWith("http")
                ) {
                        Log.d("DadaquProvider", "Success! Video URL: $interceptedUrl")
                        callback.invoke(
                                newExtractorLink(
                                        name = this.name,
                                        source = this.name,
                                        url = interceptedUrl,
                                        type = INFER_TYPE
                                ) { this.referer = data }
                        )
                        return true
                } else {
                        Log.d("DadaquProvider", "Failed to extract video URL (timeout or empty)")
                        return false
                }
        }
}
