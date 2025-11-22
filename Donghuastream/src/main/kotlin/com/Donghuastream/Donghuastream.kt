package com.Donghuastream


import com.lagradost.api.Log
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Donghuastream provider
 */
open class Donghuastream : MainAPI() {
    override var mainUrl = "https://donghuastream.org"
    override var name = "Donghuastream"
    override val hasMainPage = true
    override var lang = "zh"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        "anime/?status=&type=&order=update&page=" to "Recently Updated",
        "anime/?status=completed&type=&order=update" to "Completed",
        "anime/?status=&type=special&sub=&order=update" to "Special Anime",
    )

    /**
     * Retrieves the main page content.
     *
     * @param page The page number to retrieve.
     * @param request The request object.
     * @return The response containing the home page data.
     */
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}$page").document
        val home = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("div.bsx > a").attr("title")
        val href = fixUrl(this.select("div.bsx > a").attr("href"))
        val posterUrl = fixUrlNull(this.select("div.bsx a img").attr("data-src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }


    /**
     * Searches for content.
     *
     * @param query The search query.
     * @return A list of search results.
     */
    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=$query").document

        val results =
            document.select("div.listupd > article").mapNotNull { it.toSearchResult() }

        return results
    }

    /**
     * Loads the details for a given URL.
     *
     * @param url The URL to load.
     * @return The response containing the loaded data.
     */
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim().toString()
        val href = document.selectFirst(".eplister li > a")?.attr("href") ?: ""
        var poster = document.select("div.ime > img").attr("data-src")
        val description = document.selectFirst("div.entry-content")?.text()?.trim()
        val type = document.selectFirst(".spe")?.text().toString()
        val tvtag = if (type.contains("Movie")) TvType.Movie else TvType.TvSeries
        return if (tvtag == TvType.TvSeries) {
            val Eppage = document.selectFirst(".eplister li > a")?.attr("href") ?: ""
            val doc = app.get(Eppage).document
            val episodes = doc.select("div.episodelist > ul > li").map { info ->
                val href1 = info.select("a").attr("href")
                val episode =
                    info.select("a span").text().substringAfter("-").substringBeforeLast("-")
                val posterr = info.selectFirst("a img")?.attr("data-src") ?: ""
                newEpisode(href1)
                {
                    this.name = episode.replace(title, "", ignoreCase = true)
                    this.episode = episode.toIntOrNull()
                    this.posterUrl = posterr
                }
            }
            if (poster.isEmpty()) {
                poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim()
                    .toString()
            }
            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes.reversed()) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            if (poster.isEmpty()) {
                poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim()
                    .toString()
            }
            newMovieLoadResponse(title, url, TvType.Movie, href) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    /**
     * Loads the links for a given data.
     *
     * @param data The data to load the links from.
     * @param isCasting Whether the links are for casting.
     * @param subtitleCallback The callback for subtitles.
     * @param callback The callback for extractor links.
     * @return Whether the links were loaded successfully.
     */
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = app.get(data).document

        val options = html.select("option[data-index]")

        coroutineScope {
            options.forEach { option ->
                launch {
                    val base64 = option.attr("value")
                    if (base64.isBlank()) return@launch
                    val label = option.text().trim()
                    val decodedHtml = try {
                        base64Decode(base64)
                    } catch (_: Exception) {
                        Log.w("Error", "Base64 decode failed: $base64")
                        return@launch
                    }

                    val iframeUrl =
                        Jsoup.parse(decodedHtml).selectFirst("iframe")?.attr("src")
                            ?.let(::httpsify)
                    if (iframeUrl.isNullOrEmpty()) return@launch
                    when {
                        "vidmoly" in iframeUrl -> {
                            val cleanedUrl =
                                "http:" + iframeUrl.substringAfter("=\"").substringBefore("\"")
                            loadExtractor(cleanedUrl, referer = iframeUrl, subtitleCallback) { link ->
                                // Filter for quality >= 720p or unknown
                                if (link.quality >= 720 || link.quality <= 0) {
                                    callback(link)
                                }
                            }
                        }

                        iframeUrl.endsWith(".mp4") -> {
                            callback(
                                newExtractorLink(
                                    label,
                                    label,
                                    url = iframeUrl,
                                    INFER_TYPE
                                ) {
                                    this.referer = ""
                                    this.quality = getQualityFromName(label)
                                }
                            )
                        }

                        else -> {
                            loadExtractor(iframeUrl, referer = iframeUrl, subtitleCallback) { link ->
                                // Filter for quality >= 720p or unknown
                                if (link.quality >= 720 || link.quality <= 0) {
                                    callback(link)
                                }
                            }
                        }
                    }
                }
            }
        }

        return true
    }
}
