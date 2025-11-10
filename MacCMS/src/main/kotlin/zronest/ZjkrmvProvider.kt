package zronest

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse

/**
 * 追剧狂人影视站点实现
 */
class ZjkrmvProvider : BaseMacCmsProvider() {
    override var mainUrl = "https://www.zjkrmv.com" // 如果主站点不可用，可以切换到备用域名 www.zjkrmv.vip
    override var name = "追剧狂人"
    override val hasMainPage = true
    
    override val listSelector = "div.module-items, div.module-card-items" // 视频列表选择器
    override val itemTitleSelector = "a.module-item-title" // 标题选择器
    override val itemPosterSelector = "img.lazyload, img.lazy" // 海报图片选择器

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) mainUrl else "$mainUrl/index.php/vod/show/page/$page.html"
        val doc = app.get(url).document

        val items = doc.select(listSelector).flatMap { section ->
            section.select(itemTitleSelector).mapNotNull { el ->
                val href = fixUrl(el.attr("href"))
                val title = el.attr("title").ifEmpty { el.text().trim() }
                val poster = fixUrlNull(el.parent()?.selectFirst(itemPosterSelector)?.attr("data-original"))
                
                newMovieSearchResponse(title, href, TvType.Movie) { 
                    this.posterUrl = poster 
                }
            }
        }

        return newHomePageResponse(listOf(HomePageList(request.name, items)))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // 站点搜索页面
    val url = "$mainUrl/index.php/vod/search.html?wd=${java.net.URLEncoder.encode(query, "UTF-8") }"
        val doc = app.get(url).document
        
        return doc.select(listSelector).flatMap { section ->
            section.select(itemTitleSelector).mapNotNull { el ->
                val href = fixUrl(el.attr("href"))
                val title = el.attr("title").ifEmpty { el.text().trim() }
                val poster = fixUrlNull(el.parent()?.selectFirst(itemPosterSelector)?.attr("data-original"))
                
                newMovieSearchResponse(title, href, TvType.Movie) { 
                    this.posterUrl = poster 
                }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        
        val title = doc.selectFirst("h1.page-title")?.text()?.trim() 
            ?: doc.selectFirst(".video-info-header h1")?.text()?.trim()
            ?: throw ErrorLoadingException("No title found")
            
        val poster = fixUrlNull(doc.selectFirst(".module-item-pic img, .video-cover img")?.attr("data-original"))
        val plot = doc.selectFirst(".video-info-item .video-info-content, .content_desc")?.text()?.trim()
        
        // 查找播放列表
        val episodes = doc.select(".module-play-list a, .sort-item").map { el ->
            val epHref = fixUrl(el.attr("href"))
            val epName = el.text().trim()
            
            newEpisode(epHref) {
                this.name = epName
                this.posterUrl = poster
            }
        }

        return if (episodes.size > 1) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
            }
        } else {
            val videoUrl = episodes.firstOrNull()?.data ?: url
            newMovieLoadResponse(title, url, TvType.Movie, videoUrl) {
                this.posterUrl = poster
                this.plot = plot
            }
        }
    }
}