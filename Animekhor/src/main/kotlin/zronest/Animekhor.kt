package zronest

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
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Element

/**
 * Animekhor provider
 */
open class Animekhor : MainAPI() {
    override var mainUrl = "https://animekhor.org"
    override var name = "Animekhor"
    override val hasMainPage = true
    override var lang = "zh"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime)

    override val mainPage = mainPageOf(
        "anime/?status=ongoing&type=&order=update" to "Recently Updated",
        "anime/?type=comic&order=update" to "Comic Recently Updated",
        "anime/?type=comic" to "Comic Series",
        "anime/?status=&type=ona&sub=&order=update" to "Donghua Recently Updated",
        "anime/?status=&type=ona" to "Donghua Series",
        "anime/?status=&sub=&order=latest" to "Latest Added",
        "anime/?status=&type=&order=popular" to "Popular",
        "anime/?status=completed&order=update" to "Completed",
    )

    /**
     * Retrieves the main page content.
     *
     * @param page The page number to retrieve.
     * @param request The main page request.
     * @return The home page response.
     */
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}&page=$page").document
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

    /**
     * Converts an HTML element to a search response.
     *
     * @return The search response.
     */
    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("div.bsx > a").attr("title")
        val href = fixUrl(this.select("div.bsx > a").attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("div.bsx > a img")?.getsrcAttribute())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    /**
     * Converts an HTML element to a search response for a search query.
     *
     * @return The search response.
     */
    private fun Element.toSearchquery(): SearchResponse {
        val title = this.select("div.bsx > a").attr("title")
        val href = fixUrl(this.select("div.bsx > a").attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("div.bsx > a img")?.getsrcAttribute())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }


    /**
     * Searches for content.
     *
     * @param query The search query.
     * @return A list of search responses.
     */
    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val document = app.get("${mainUrl}/page/$i/?s=$query").document

            val results = document.select("div.listupd > article").mapNotNull { it.toSearchquery() }

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }

        return searchResponse
    }

    /**
     * Loads content details.
     *
     * @param url The URL of the content to load.
     * @return The load response.
     */
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim().toString()
        val href = document.selectFirst(".eplister li > a")?.attr("href") ?: ""
        var poster = document.select("meta[property=og:image]").attr("content")
        val description = document.selectFirst("div.entry-content")?.text()?.trim()
        val type = document.selectFirst(".spe")?.text().toString()
        val tvtag = if (type.contains("Movie")) TvType.Movie else TvType.TvSeries
        return if (tvtag == TvType.TvSeries) {
            val Eppage = document.selectFirst(".eplister li > a")?.attr("href") ?: ""
            val doc = app.get(Eppage).document
            val epposter = doc.select("meta[property=og:image]").attr("content")
            val episodes = doc.select("div.episodelist > ul > li").map { info ->
                val href1 = info.select("a").attr("href")
                val episode =
                    info.select("a span").text().substringAfter("-").substringBeforeLast("-")
                newEpisode(href1)
                {
                    this.name = episode
                    this.posterUrl = epposter
                }
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
     * Loads extractor links for streaming.
     *
     * @param data The data to load links from.
     * @param isCasting Whether the content is being cast.
     * @param subtitleCallback A callback for subtitle files.
     * @param callback A callback for extractor links.
     * @return `true` if links were loaded successfully, `false` otherwise.
     */
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        coroutineScope {
            document.select(".mobius option").map { server ->
                async {
                    val base64 = server.attr("value")
                    if (base64.isEmpty()) return@async

                    val decodedUrl = base64Decode(base64)
                    val regex = Regex("""src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    val matchResult = regex.find(decodedUrl)
                    var url = matchResult?.groups?.get(1)?.value ?: return@async
                    if (url.startsWith("//")) {
                        url = httpsify(url)
                    }

                    // Filter out known bad hosts
                    val blacklistedHosts = listOf("short.icu", "upns.live", "p2pstream.vip")
                    try {
                        val host = java.net.URI(url).host
                        if (host != null && blacklistedHosts.any { host.contains(it) }) {
                            return@async
                        }
                    } catch (e: Exception) {
                        // Invalid URL
                        return@async
                    }

                    loadExtractor(url, referer = mainUrl, subtitleCallback) { link ->
                        // Filter for quality >= 720p or unknown
                        if (link.quality !in 1..<720) {
                            callback(link)
                        }
                    }
                }
            }.awaitAll()
        }
        return true
    }

    /**
     * Gets the source attribute from an element, preferring "data-src" over "src".
     *
     * @return The source URL.
     */
    private fun Element.getsrcAttribute(): String {
        val src = this.attr("src")
        val dataSrc = this.attr("data-src")

        return when {
            src.startsWith("http") -> src
            dataSrc.startsWith("http") -> dataSrc
            else -> ""
        }
    }

}
