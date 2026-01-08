package nxovaeng

import com.lagradost.api.Log
import java.net.URI
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * M3U8 广告过滤拦截器
 *
 * 功能：
 * 1. 基于 TVBox 规则的正则匹配过滤
 * 2. 智能特征识别（域名突变、时长异常、分辨率突变）
 * 3. 支持自定义规则扩展
 *
 * 使用方式：
 * ```kotlin
 * override fun getVideoInterceptor(extractor: VideoExtractor): Interceptor {
 *     return AdFilterInterceptor()
 * }
 * ```
 */
class AdFilterInterceptor : Interceptor {

    companion object {
        private const val TAG = "AdFilterInterceptor"
        private const val DEBUG = false // 调试模式，设为 false 可减少日志
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val url = request.url.toString()
        val contentType = response.header("Content-Type") ?: ""

        // 只处理 m3u8 文件
        if (!url.endsWith(".m3u8") && !contentType.contains("mpegurl")) {
            return response
        }

        try {
            val originalBody = response.body?.string() ?: return response

            if (DEBUG) {
                Log.d(TAG, "Processing m3u8: $url")
                Log.d(TAG, "Original lines: ${originalBody.lines().size}")
            }

            // 过滤广告切片
            val cleanedBody = filterAdSegments(originalBody, url)

            if (DEBUG) {
                val originalLines = originalBody.lines().size
                val cleanedLines = cleanedBody.lines().size
                val removedLines = originalLines - cleanedLines
                if (removedLines > 0) {
                    Log.d(TAG, "Filtered $removedLines lines from m3u8")
                }
            }

            // 返回清洗后的内容
            return response.newBuilder()
                    .body(cleanedBody.toResponseBody(response.body?.contentType()))
                    .build()
        } catch (e: Exception) {
            Log.e(TAG, "Error filtering m3u8: ${e.message}", e)
            return response
        }
    }

    /** 过滤广告切片的核心逻辑 */
    private fun filterAdSegments(m3u8Content: String, url: String): String {
        val lines = m3u8Content.lines()
        val filtered = mutableListOf<String>()

        // 获取匹配的规则
        val rule = getMatchingRule(url)

        // 用于特征检测
        val statistics = SegmentStatistics()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            // 保留头部信息和其他元数据
            if (shouldPreserveLine(line)) {
                filtered.add(line)
                i++
                continue
            }

            // 处理切片信息
            if (line.startsWith("#EXTINF")) {
                val nextLine = lines.getOrNull(i + 1) ?: ""

                // 提取切片信息用于统计
                val duration = extractDuration(line)
                statistics.addSegment(duration, nextLine)

                // 综合判断是否为广告
                val isAd =
                        isAdSegment(
                                extinfLine = line,
                                tsUrl = nextLine,
                                previousLines = lines.subList(maxOf(0, i - 5), i),
                                rule = rule,
                                statistics = statistics,
                                url = url
                        )

                if (!isAd) {
                    filtered.add(line)
                    if (nextLine.isNotEmpty()) {
                        filtered.add(nextLine)
                    }
                }

                i += 2
            } else {
                filtered.add(line)
                i++
            }
        }

        return filtered.joinToString("\n")
    }

    /** 判断某一行是否需要保留（元数据、注释等） */
    private fun shouldPreserveLine(line: String): Boolean {
        return line.startsWith("#EXTM3U") ||
                line.startsWith("#EXT-X-VERSION") ||
                line.startsWith("#EXT-X-TARGETDURATION") ||
                line.startsWith("#EXT-X-MEDIA-SEQUENCE") ||
                line.startsWith("#EXT-X-PLAYLIST-TYPE") ||
                line.startsWith("#EXT-X-ALLOW-CACHE") ||
                line.startsWith("#EXT-X-ENDLIST") ||
                line.startsWith("#EXT-X-KEY") ||
                line.startsWith("#EXT-X-MAP") ||
                line.startsWith("#EXT-X-PROGRAM-DATE-TIME") ||
                line.startsWith("#EXT-X-DATERANGE") ||
                line.isEmpty() ||
                (line.startsWith("#") &&
                        !line.startsWith("#EXTINF") &&
                        !line.startsWith("#EXT-X-DISCONTINUITY"))
    }

    /** 综合判断是否为广告切片 */
    private fun isAdSegment(
            extinfLine: String,
            tsUrl: String,
            previousLines: List<String>,
            rule: AdFilterRule?,
            statistics: SegmentStatistics,
            url: String
    ): Boolean {
        var adScore = 0

        // === 规则1: TVBox 正则规则匹配 ===
        if (rule != null && matchesRegexRules(extinfLine, tsUrl, rule)) {
            adScore += 50 // 正则匹配，高权重
            if (DEBUG) Log.d(TAG, "Regex match for: $tsUrl")
        }

        // === 规则2: DISCONTINUITY 检测 ===
        if (isInDiscontinuityBlock(previousLines)) {
            adScore += 30
            if (DEBUG) Log.d(TAG, "DISCONTINUITY block detected for: $tsUrl")
        }

        // === 规则3: 时长异常检测 ===
        val duration = extractDuration(extinfLine)
        if (statistics.isDurationAbnormal(duration)) {
            adScore += 20
            if (DEBUG)
                    Log.d(TAG, "Duration abnormal: $duration vs avg ${statistics.averageDuration}")
        }

        // === 规则4: URL 关键词检测 ===
        if (containsAdKeywords(tsUrl)) {
            adScore += 40
            if (DEBUG) Log.d(TAG, "Ad keyword found in: $tsUrl")
        }

        // === 规则5: 域名突变检测 ===
        if (statistics.isDomainChanged(tsUrl)) {
            adScore += 25
            if (DEBUG) Log.d(TAG, "Domain changed: $tsUrl")
        }

        // === 规则6: 文件大小异常（通过文件名模式）===
        if (hasAbnormalFilePattern(tsUrl)) {
            adScore += 15
            if (DEBUG) Log.d(TAG, "Abnormal file pattern: $tsUrl")
        }

        // === 规则7: 序号跳跃检测 ===
        if (statistics.hasSequenceGap(tsUrl)) {
            adScore += 10
            if (DEBUG) Log.d(TAG, "Sequence gap detected: $tsUrl")
        }

        // 阈值判断：分数超过 40 则判定为广告
        val isAd = adScore >= 40

        if (DEBUG && isAd) {
            Log.d(TAG, "AD DETECTED (score: $adScore): $tsUrl")
        }

        return isAd
    }

    /** 获取匹配的过滤规则 */
    private fun getMatchingRule(url: String): AdFilterRule? {
        return AD_FILTER_RULES.find { rule ->
            rule.hosts.any { host -> url.contains(host, ignoreCase = true) }
        }
    }

    /** 使用正则规则匹配 */
    private fun matchesRegexRules(extinfLine: String, tsUrl: String, rule: AdFilterRule): Boolean {
        val combined = "$extinfLine\n$tsUrl"
        return rule.regex.any { pattern ->
            try {
                Regex(pattern).containsMatchIn(combined)
            } catch (e: Exception) {
                false
            }
        }
    }

    /** 检测是否在 DISCONTINUITY 块内（广告插播） */
    private fun isInDiscontinuityBlock(previousLines: List<String>): Boolean {
        var discontinuityCount = 0
        for (line in previousLines.reversed()) {
            if (line.contains("#EXT-X-DISCONTINUITY")) {
                discontinuityCount++
                if (discontinuityCount == 2) {
                    // 在两个 DISCONTINUITY 之间，可能是广告
                    return true
                }
            }
        }
        // 只有一个 DISCONTINUITY，可能是广告开始
        return discontinuityCount == 1
    }

    /** 检测 URL 是否包含广告关键词 */
    private fun containsAdKeywords(url: String): Boolean {
        val adKeywords =
                listOf(
                        "ad",
                        "ads",
                        "adv",
                        "advertisement",
                        "adjump",
                        "sponsor",
                        "commercial",
                        "promo",
                        "promotion",
                        "tracking",
                        "adserver",
                        "doubleclick",
                        "p1ayer" // 混淆的 player
                )

        val lowerUrl = url.lowercase()
        return adKeywords.any { lowerUrl.contains(it) }
    }

    /** 检测文件名模式是否异常 */
    private fun hasAbnormalFilePattern(url: String): Boolean {
        // 广告切片文件名模式
        val patterns =
                listOf(
                        Regex("""ad[_-]\d+\.ts""", RegexOption.IGNORE_CASE),
                        Regex("""1171057.*\.ts"""), // 非凡广告特征
                        Regex("""6d7b077.*\.ts"""), // 非凡广告特征
                        Regex("""6718a403.*\.ts"""), // 非凡广告特征
                        Regex("""original.*\.ts""") // 索尼广告特征
                )

        return patterns.any { it.containsMatchIn(url) }
    }

    /** 提取切片时长 */
    private fun extractDuration(extinfLine: String): Double {
        val regex = Regex("""#EXTINF:([\d.]+)""")
        return regex.find(extinfLine)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    /** 切片统计信息，用于特征检测 */
    private class SegmentStatistics {
        private val durations = mutableListOf<Double>()
        private val domains = mutableListOf<String>()
        private val sequences = mutableListOf<Int>()

        var averageDuration: Double = 0.0
            private set

        fun addSegment(duration: Double, url: String) {
            durations.add(duration)

            // 提取域名
            try {
                val uri = URI(url)
                val domain = uri.host ?: ""
                if (domain.isNotEmpty()) {
                    domains.add(domain)
                }
            } catch (e: Exception) {
                // URL 可能是相对路径
            }

            // 提取序号
            extractSequenceNumber(url)?.let { sequences.add(it) }

            // 更新平均时长
            if (durations.size > 3) { // 至少有几个样本
                averageDuration = durations.takeLast(20).average()
            }
        }

        /** 判断时长是否异常 */
        fun isDurationAbnormal(duration: Double): Boolean {
            if (averageDuration == 0.0 || durations.size < 5) {
                // 样本不足，使用绝对阈值
                return duration < 2.0 || duration > 15.0
            }

            // 偏差超过 50%
            val deviation = kotlin.math.abs(duration - averageDuration) / averageDuration
            return deviation > 0.5
        }

        /** 判断域名是否突变 */
        fun isDomainChanged(url: String): Boolean {
            if (domains.size < 2) return false

            try {
                val uri = URI(url)
                val currentDomain = uri.host ?: return false
                val previousDomain = domains.getOrNull(domains.size - 1) ?: return false

                return currentDomain != previousDomain
            } catch (e: Exception) {
                return false
            }
        }

        /** 判断序号是否跳跃 */
        fun hasSequenceGap(url: String): Boolean {
            if (sequences.size < 2) return false

            val currentSeq = extractSequenceNumber(url) ?: return false
            val previousSeq = sequences.last()

            // 序号跳跃超过 1
            return kotlin.math.abs(currentSeq - previousSeq) > 1
        }

        /** 从 URL 中提取序号 */
        private fun extractSequenceNumber(url: String): Int? {
            // 尝试匹配常见的序号模式
            val patterns =
                    listOf(
                            Regex("""[-_](\d+)\.ts"""),
                            Regex("""/(\d+)\.ts"""),
                            Regex("""seg-(\d+)"""),
                            Regex("""segment(\d+)""")
                    )

            for (pattern in patterns) {
                val match = pattern.find(url)
                if (match != null) {
                    return match.groupValues[1].toIntOrNull()
                }
            }

            return null
        }
    }

    /** 广告过滤规则数据类 */
    data class AdFilterRule(val name: String, val hosts: List<String>, val regex: List<String>)

    /**
     * 广告过滤规则列表（整理自多个 TVBox 配置）
     *
     * 规则说明：
     * 1. DISCONTINUITY 模式：两个 #EXT-X-DISCONTINUITY 标签之间的内容通常是广告插播
     * 2. 时长匹配：广告切片有固定的时长特征（如 15.92、17.99 等秒数）
     * 3. URL 模式：广告切片的文件名或路径有特定模式（如 adjump、p1ayer 等）
     * 4. 哈希模式：某些广告切片使用特定长度的随机哈希命名
     */
    private val AD_FILTER_RULES =
            listOf(
                    // ========== 暴风资源 (bfzy) ==========
                    AdFilterRule(
                            name = "暴风",
                            hosts = listOf("bfzy", "bfbfvip", "bfengbf"),
                            regex =
                                    listOf(
                                            // 文件名包含 adjump 的切片
                                            """#EXTINF.*?\s+.*?adjump.*?\.ts""",
                                            // DISCONTINUITY 块，时长 3 秒
                                            """#EXT-X-DISCONTINUITY\r*\n*#EXTINF:3,[\s\S]*?#EXT-X-DISCONTINUITY"""
                                    )
                    ),

                    // ========== 量子资源 (lz) ==========
                    AdFilterRule(
                            name = "量子",
                            hosts = listOf("vip.lz", "hd.lz", ".cdnlz", "v.cdnlz"),
                            regex =
                                    listOf(
                                            // DISCONTINUITY 块，时长 7.166667 秒
                                            """#EXT-X-DISCONTINUITY\r*\n*#EXTINF:7\.166667,[\s\S]*?#EXT-X-DISCONTINUITY""",
                                            // DISCONTINUITY 块，时长 4.066667 秒
                                            """#EXT-X-DISCONTINUITY\r*\n*#EXTINF:4\.066667,[\s\S]*?#EXT-X-DISCONTINUITY""",
                                            // 特征时长：17.19 秒
                                            """17\.19""",
                                            // 特征时长：18.5333 秒
                                            """18\.5333""",
                                            // 长哈希文件名（18 位以上）
                                            """#EXTINF.*?\s+[a-z0-9]{18,}\.ts""",
                                            """#EXT-X-DISCONTINUITY\r*\n*#EXTINF.*?\s+[a-z0-9]{18,}\.ts[\s\S]*?#EXT-X-DISCONTINUITY"""
                                    )
                    ),

                    // ========== 非凡资源 (ffzy) ==========
                    AdFilterRule(
                            name = "非凡",
                            hosts =
                                    listOf(
                                            "vip.ffzy",
                                            "hd.ffzy",
                                            "super.ffzy",
                                            "svipsvip.ffzy",
                                            ".ffzy"
                                    ),
                            regex =
                                    listOf(
                                            // DISCONTINUITY 块，时长 6.4 秒
                                            """#EXT-X-DISCONTINUITY\r*\n*#EXTINF:6\.400000,[\s\S]*?#EXT-X-DISCONTINUITY""",
                                            // DISCONTINUITY 块，时长 6.666667 秒
                                            """#EXT-X-DISCONTINUITY\r*\n*#EXTINF:6\.666667,[\s\S]*?#EXT-X-DISCONTINUITY""",
                                            // 特定哈希特征：1171057
                                            """#EXTINF.*?\s+.*?1171(057).*?\.ts""",
                                            // 特定哈希特征：6d7b077
                                            """#EXTINF.*?\s+.*?6d7b(077).*?\.ts""",
                                            // 特定哈希特征：6718a403
                                            """#EXTINF.*?\s+.*?6718a(403).*?\.ts""",
                                            // 特征时长：17.99 秒
                                            """17\.99""",
                                            // 特征时长：14.45 秒
                                            """14\.45""",
                                            // 特征时长：25.1 秒
                                            """25\.1""",
                                            // DISCONTINUITY 块模式（8 行内容）
                                            """#EXT-X-DISCONTINUITY(?:\n.*?){8}\n#EXT-X-DISCONTINUITY""",
                                            // DISCONTINUITY 块模式（10 行内容）
                                            """#EXT-X-DISCONTINUITY(?:\n(?!#EXT-X-DISCONTINUITY).*){10}\n#EXT-X-DISCONTINUITY"""
                                    )
                    ),

                    // ========== 索尼资源 (suonizy) ==========
                    AdFilterRule(
                            name = "索尼",
                            hosts = listOf("suonizy"),
                            regex =
                                    listOf(
                                            // DISCONTINUITY 块，时长 1 秒
                                            """#EXT-X-DISCONTINUITY\r*\n*#EXTINF:1\.000000,[\s\S]*?#EXT-X-DISCONTINUITY""",
                                            // 文件名包含 p1ayer（混淆的 player）
                                            """#EXTINF.*?\s+.*?p1ayer.*?\.ts""",
                                            // 路径包含 /video/original/
                                            """#EXTINF.*?\s+.*?/video/original.*?\.ts""",
                                            // 特征时长：15.1666 秒
                                            """15\.1666""",
                                            // 特征时长：15.2666 秒
                                            """15\.2666"""
                                    )
                    ),

                    // ========== 快看资源 (kuaikan) ==========
                    AdFilterRule(
                            name = "快看",
                            hosts = listOf("kuaikan"),
                            regex =
                                    listOf(
                                            // 未加密的 DISCONTINUITY 块，时长 5 秒
                                            """#EXT-X-KEY:METHOD=NONE\r*\n*#EXTINF:5,[\s\S]*?#EXT-X-DISCONTINUITY""",
                                            // 未加密的 DISCONTINUITY 块，时长 2.4 秒
                                            """#EXT-X-KEY:METHOD=NONE\r*\n*#EXTINF:2\.4,[\s\S]*?#EXT-X-DISCONTINUITY""",
                                            // 未加密的 DISCONTINUITY 块，时长 1.467 秒
                                            """#EXT-X-KEY:METHOD=NONE\r*\n*#EXTINF:1\.467,[\s\S]*?#EXT-X-DISCONTINUITY""",
                                            // 通用未加密 DISCONTINUITY 块
                                            """#EXT-X-DISCONTINUITY\r*\n*#EXT-X-KEY:METHOD=NONE\r*\n*#EXTINF:.*?,[\s\S]*?#EXT-X-DISCONTINUITY"""
                                    )
                    ),

                    // ========== 乐视云 (leshiyuncdn) ==========
                    AdFilterRule(
                            name = "乐视云",
                            hosts = listOf("leshiyuncdn"),
                            regex =
                                    listOf(
                                            // 特征时长：15.92 秒（乐视云广告的典型时长）
                                            """15\.92"""
                                    )
                    ),

                    // ========== 1080 资源 ==========
                    AdFilterRule(
                            name = "1080zyk",
                            hosts = listOf("high24-playback", "high20-playback"),
                            regex =
                                    listOf(
                                            // 特征时长：16.63 秒
                                            """16\.63"""
                                    )
                    ),

                    // ========== 555 电影 ==========
                    AdFilterRule(
                            name = "555DM",
                            hosts = listOf("cqxfjz"),
                            regex =
                                    listOf(
                                            // 特征时长：10.56 秒
                                            """10\.56"""
                                    )
                    ),

                    // ========== 海外看 (haiwaikan) ==========
                    AdFilterRule(
                            name = "海外看",
                            hosts = listOf("haiwaikan"),
                            regex =
                                    listOf(
                                            // 多个特征时长
                                            """8\.16""",
                                            """8\.1748""",
                                            """10\.0099""",
                                            """10\.3333""",
                                            """10\.85""",
                                            """12\.33""",
                                            """16\.0599"""
                                    )
                    ),

                    // ========== 星星资源 ==========
                    AdFilterRule(
                            name = "星星",
                            hosts = listOf("aws.ulivetv.net"),
                            regex =
                                    listOf(
                                            // DISCONTINUITY 块，时长 8 秒
                                            """#EXT-X-DISCONTINUITY\r*\n*#EXTINF:8,[\s\S]*?#EXT-X-DISCONTINUITY"""
                                    )
                    ),

                    // ========== 奇虎资源 (qihubf) ==========
                    AdFilterRule(
                            name = "奇虎",
                            hosts = listOf("qihubf"),
                            regex =
                                    listOf(
                                            // 加密方式切换：NONE -> AES-128
                                            """#EXT-X-DISCONTINUITY\r*\n*#EXT-X-KEY:METHOD=NONE\r*\n*#EXTINF:2,[\s\S]*?#EXT-X-DISCONTINUITY\r*\n*#EXT-X-KEY:METHOD=AES-128""",
                                            // 未加密的 DISCONTINUITY 块，时长 2 秒
                                            """#EXT-X-DISCONTINUITY\r*\n*#EXT-X-KEY:METHOD=NONE\r*\n*#EXTINF:2,[\s\S]*?#EXT-X-DISCONTINUITY"""
                                    )
                    ),

                    // ========== U酷资源 (ukzy) ==========
                    AdFilterRule(
                            name = "U酷",
                            hosts = listOf("ukzy"),
                            regex =
                                    listOf(
                                            // 未加密的 DISCONTINUITY 块
                                            """#EXT-X-DISCONTINUITY\r*\n*#EXT-X-KEY:METHOD=NONE\r*\n*#EXTINF:.*?,[\s\S]*?#EXT-X-DISCONTINUITY"""
                                    )
                    ),

                    // ========== ikun 资源 ==========
                    AdFilterRule(
                            name = "ikun",
                            hosts = listOf("bfikuncdn"),
                            regex =
                                    listOf(
                                            // 未加密的 DISCONTINUITY 块
                                            """#EXT-X-DISCONTINUITY\r*\n*#EXT-X-KEY:METHOD=NONE\r*\n*#EXTINF:.*?,[\s\S]*?#EXT-X-DISCONTINUITY"""
                                    )
                    ),

                    // ========== 卧龙资源 ==========
                    AdFilterRule(
                            name = "卧龙",
                            hosts = listOf("cdn.wl"),
                            regex =
                                    listOf(
                                            // 通用 DISCONTINUITY 块
                                            """#EXT-X-DISCONTINUITY\r*\n*#EXTINF:.*?,[\s\S]*?#EXT-X-DISCONTINUITY"""
                                    )
                    )
            )
}
