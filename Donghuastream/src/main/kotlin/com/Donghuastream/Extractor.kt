package com.Donghuastream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import org.json.JSONObject
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.newExtractorLink

// ========== Donghuastream 系 ==========

open class Vtbe : ExtractorApi() {
    override var name = "Vtbe"
    override var mainUrl = "https://vtbe.to"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url, referer = mainUrl).document
        val extractedpack =
                response.selectFirst("script:containsData(function(p,a,c,k,e,d))")
                        ?.data()
                        .toString()
        JsUnpacker(extractedpack).unpack()?.let { unPacked ->
            Regex("sources:\\[\\{file:\"(.*?)\"").find(unPacked)?.groupValues?.get(1)?.let { link ->
                return listOf(
                        newExtractorLink(this.name, this.name, url = link, ExtractorLinkType.M3U8) {
                            this.referer = referer ?: ""
                            this.quality = Qualities.Unknown.value
                        }
                )
            }
        }
        return null
    }
}

class wishfast : StreamWishExtractor() {
    override var mainUrl = "https://wishfast.top"
    override var name = "StreamWish"
}

class waaw : StreamSB() {
    override var mainUrl = "https://waaw.to"
}

class FileMoonSx : Filesim() {
    override val mainUrl = "https://filemoon.sx"
    override val name = "FileMoonSx"
}

open class Ultrahd : ExtractorApi() {
    override var name = "Ultrahd Streamplay"
    override var mainUrl = "https://ultrahd.streamplay.co.in"
    override val requiresReferer = false

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = mainUrl).document
        val extractedpack = response.toString()
        Regex("\\\$\\.\\s*ajax\\(\\s*\\{\\s*url:\\s*\"(.*?)\"")
                .find(extractedpack)
                ?.groupValues
                ?.get(1)
                ?.let { link ->
                    app.get(link).parsedSafe<Root>()?.sources?.map {
                        val m3u8 = httpsify(it.file)
                        if (m3u8.contains(".mp4")) {
                            callback.invoke(
                                    newExtractorLink(
                                            "Ultrahd Streamplay",
                                            "Ultrahd Streamplay",
                                            url = m3u8,
                                            INFER_TYPE
                                    ) {
                                        this.referer = ""
                                        this.quality = getQualityFromName("")
                                    }
                            )
                        } else {
                            M3u8Helper.generateM3u8(
                                            this.name,
                                            m3u8,
                                            "$referer",
                                    )
                                    .forEach(callback)
                        }
                    }
                    app.get(link).parsedSafe<Root>()?.tracks?.map {
                        val langurl = it.file
                        val lang = it.label
                        subtitleCallback.invoke(
                                newSubtitleFile(
                                        lang, // Use label for the name
                                        langurl // Use extracted URL
                                )
                        )
                    }
                }
    }
}

open class PlayStreamplay : ExtractorApi() {
    override var name = "All sub player"
    override var mainUrl = "https://play.streamplay.co.in"
    override val requiresReferer = false

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url, timeout = 10000).document
        val packedScript =
                doc.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data() ?: return
        val evalRegex = Regex("""eval\(.*?\)\)\)""", RegexOption.DOT_MATCHES_ALL)
        val packedCode = evalRegex.find(packedScript)?.value ?: return
        val unpackedJs = JsUnpacker(packedCode).unpack() ?: return
        val token = Regex("""kaken="(.*?)"""").find(unpackedJs)?.groupValues?.getOrNull(1) ?: return
        val apiUrl = "$mainUrl/api/?$token"
        val response = app.get(apiUrl, timeout = 10000).parsedSafe<Response>() ?: return

        val m3u8Url = response.sources.find { it.file.isNotBlank() }?.file
        if (!m3u8Url.isNullOrEmpty()) {
            val headers =
                    mapOf(
                            "pragma" to "no-cache",
                            "priority" to "u=0, i",
                            "sec-ch-ua" to
                                    "\"Not)A;Brand\";v=\"8\", \"Chromium\";v=\"138\", \"Google Chrome\";v=\"138\"",
                            "sec-ch-ua-mobile" to "?0",
                            "sec-ch-ua-platform" to "\"Windows\"",
                            "sec-fetch-dest" to "document",
                            "sec-fetch-mode" to "navigate",
                            "sec-fetch-site" to "none",
                            "sec-fetch-user" to "?1",
                            "upgrade-insecure-requests" to "1",
                            "user-agent" to
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"
                    )
            try {
                M3u8Helper.generateM3u8(name, m3u8Url, mainUrl, headers = headers).forEach(callback)
            } catch (_: Exception) {
                callback(
                    newExtractorLink(name, name, m3u8Url, ExtractorLinkType.M3U8) {
                        this.referer = mainUrl
                        this.quality = Qualities.P1080.value
                        this.headers = headers
                    }
                )
            }
        }

        response.tracks.forEach { subtitle ->
            subtitleCallback(newSubtitleFile(lang = subtitle.label, url = subtitle.file))
        }
    }

    data class Response(
            val query: Query,
            val status: String,
            val message: String,
            @param:JsonProperty("embed_url") val embedUrl: String,
            @param:JsonProperty("download_url") val downloadUrl: String,
            val title: String,
            val poster: String,
            val filmstrip: String,
            val sources: List<Source>,
            val tracks: List<Track>,
    )

    data class Query(
            val source: String,
            val id: String,
            val download: String,
    )

    data class Source(
            val file: String,
            val type: String,
            val label: String,
            val default: Boolean,
    )

    data class Track(
            val file: String,
            val label: String,
            val default: Boolean?,
    )
}

// ========== Animekhor 系 ==========

class embedwish : StreamWishExtractor() {
    override var mainUrl = "https://embedwish.com"
}

class P2pstream : VidStack() {
    override var mainUrl = "https://animekhor.p2pstream.vip"
}

class Filelions : VidhideExtractor() {
    override var name = "Filelions"
    override var mainUrl = "https://filelions.live"
}

class Swhoi : StreamWishExtractor() {
    override var mainUrl = "https://swhoi.com"
    override val requiresReferer = true
}

class VidHidePro5 : VidHidePro() {
    override val mainUrl = "https://vidhidevip.com"
    override val requiresReferer = true
}

class PlayerDonghuaworld : Rumble() {
    override var mainUrl = "https://player.donghuaworld.in"
    override val requiresReferer = true
}

open class Rumble : ExtractorApi() {
    override var name = "Rumble"
    override var mainUrl = "https://rumble.com"
    override val requiresReferer = true

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val rumbleHeaders = mapOf(
            "Referer" to "https://rumble.com/",
            "Origin" to "https://rumble.com"
        )
        // 支持传入 embed URL（如 https://rumble.com/embed/v7ed06s）或普通 URL
        val embedUrl = if (url.contains("/embed/")) {
            url
        } else {
            val videoId = url.substringAfterLast("/").substringBefore("?").substringBefore(".html")
            "https://rumble.com/embed/$videoId"
        }

        val response = try {
            app.get(embedUrl, referer = "https://rumble.com/", headers = rumbleHeaders)
        } catch (_: Exception) {
            try {
                app.get(url, referer = referer ?: "https://rumble.com/", headers = rumbleHeaders)
            } catch (_: Exception) {
                return
            }
        }
        val html = response.text
        val unescaped = html.replace("\\/", "/")

        // 1. 提取所有 HLS m3u8 (现代 Rumble embed 在 p.f 中包含 master m3u8: https://rumble.com/hls-vod/.../playlist.m3u8)
        val hlsUrls = Regex("""https?://[^"\s\\]*?/hls-vod/[^"\s\\]*?\.m3u8""")
            .findAll(unescaped)
            .map { it.value }
            .distinct()
            .toList()

        var hasFoundHls = false
        for (hlsUrl in hlsUrls) {
            hasFoundHls = true
            try {
                M3u8Helper.generateM3u8(
                    name,
                    hlsUrl,
                    "https://rumble.com/",
                    headers = rumbleHeaders
                ).forEach(callback)
            } catch (_: Exception) {
                callback(
                    newExtractorLink(
                        name,
                        name,
                        hlsUrl,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = "https://rumble.com/"
                        this.quality = Qualities.P1080.value
                        this.headers = rumbleHeaders
                    }
                )
            }
        }

        // 2. 如果未扫到 HLS，根据 embedId 构造回退 playlist.m3u8
        if (!hasFoundHls) {
            val embedId = url.substringAfter("/embed/").substringBefore("/").substringBefore("?").ifEmpty {
                url.substringAfterLast("/").substringBefore("?").substringBefore(".html")
            }
            if (embedId.isNotEmpty()) {
                val fallbackHls = "https://rumble.com/hls-vod/$embedId/playlist.m3u8"
                try {
                    M3u8Helper.generateM3u8(
                        name,
                        fallbackHls,
                        "https://rumble.com/",
                        headers = rumbleHeaders
                    ).forEach(callback)
                } catch (_: Exception) {
                    callback(
                        newExtractorLink(
                            name,
                            name,
                            fallbackHls,
                            ExtractorLinkType.M3U8
                        ) {
                            this.referer = "https://rumble.com/"
                            this.quality = Qualities.P1080.value
                            this.headers = rumbleHeaders
                        }
                    )
                }
            }
        }

        // 3. 提取直接 MP4 视频
        val mp4Urls = Regex("""https?://[^"\s\\]*?\.mp4""")
            .findAll(unescaped)
            .map { it.value }
            .filter { !it.contains("timeline") && !it.contains("preview") && !it.endsWith(".Faa.mp4") }
            .distinct()
            .toList()

        for ((index, mp4Url) in mp4Urls.withIndex()) {
            callback(
                newExtractorLink(
                    name,
                    "$name MP4 ${index + 1}",
                    mp4Url,
                    INFER_TYPE
                ) {
                    this.referer = "https://rumble.com/"
                    this.quality = Qualities.Unknown.value
                    this.headers = rumbleHeaders
                }
            )
        }

        // 4. 提取字幕 tracks
        val subMatches = Regex(""""language"\s*:\s*"([^"]+)"\s*,\s*"path"\s*:\s*"(https:[^"]+?\.vtt)"""")
            .findAll(unescaped)
        for (m in subMatches) {
            val label = m.groupValues[1]
            val vttUrl = m.groupValues[2]
            subtitleCallback(newSubtitleFile(label, vttUrl))
        }

        // 兼容旧版 jwplayer 格式字幕
        val jwTracks = Regex(""""file"\s*:\s*"(https:[^"]+\.vtt[^"]*)"\s*,\s*"label"\s*:\s*"([^"]+)"""")
            .findAll(unescaped)
        for (m in jwTracks) {
            val vttUrl = m.groupValues[1]
            val label = m.groupValues[2]
            subtitleCallback(newSubtitleFile(label, vttUrl))
        }
    }
}

open class OkRu : ExtractorApi() {
    override var name = "OkRu"
    override var mainUrl = "https://ok.ru"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val embedUrl = if (url.startsWith("//")) "https:$url" else url
        val response = try {
            app.get(embedUrl, referer = referer ?: "$mainUrl/")
        } catch (_: Exception) {
            return
        }
        val document = response.document
        val dataOptions = document.selectFirst("div[data-options]")?.attr("data-options") ?: return
        try {
            val json = JSONObject(dataOptions)
            val flashvars = json.optJSONObject("flashvars") ?: return
            val metadata = flashvars.optJSONObject("metadata")
                ?: flashvars.optString("metadata").takeIf { it.isNotBlank() }?.let { JSONObject(it) }
                ?: return

            // 1. 提取 HLS m3u8
            val hls = metadata.optString("hlsManifestUrl")
            if (hls.isNotBlank()) {
                try {
                    M3u8Helper.generateM3u8(name, hls, mainUrl).forEach(callback)
                } catch (_: Exception) {
                    callback(
                        newExtractorLink(
                            name,
                            name,
                            hls,
                            ExtractorLinkType.M3U8
                        ) {
                            this.referer = mainUrl
                            this.quality = Qualities.P1080.value
                        }
                    )
                }
            }

            // 2. 提取不同清晰度直接 MP4 视频 (ultra/quad/full/hd/sd/low 等)
            val videos = metadata.optJSONArray("videos")
            if (videos != null) {
                for (i in 0 until videos.length()) {
                    val vid = videos.getJSONObject(i)
                    val qualityName = vid.optString("name")
                    val vidUrl = vid.optString("url")
                    if (vidUrl.isNotBlank()) {
                        val quality = when (qualityName.lowercase()) {
                            "ultra", "quad" -> Qualities.P2160.value
                            "full" -> Qualities.P1080.value
                            "hd" -> Qualities.P720.value
                            "sd" -> Qualities.P480.value
                            "low" -> Qualities.P360.value
                            "lowest" -> Qualities.P240.value
                            "mobile" -> Qualities.P144.value
                            else -> Qualities.Unknown.value
                        }
                        callback(
                            newExtractorLink(
                                name,
                                "$name $qualityName",
                                vidUrl,
                                INFER_TYPE
                            ) {
                                this.referer = mainUrl
                                this.quality = quality
                            }
                        )
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
