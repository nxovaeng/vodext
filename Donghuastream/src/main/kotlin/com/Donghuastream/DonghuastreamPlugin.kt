package com.Donghuastream

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.Dailymotion
import com.lagradost.cloudstream3.extractors.EmturbovidExtractor
import com.lagradost.cloudstream3.extractors.Geodailymotion
import com.lagradost.cloudstream3.extractors.Mp4Upload

/** 动漫插件：国漫 2K/4K 聚合（Donghuastream + Animekhor + SeaTV） */
@CloudstreamPlugin
class DonghuastreamPlugin : BasePlugin() {
    override fun load() {
        // 主聚合 Provider（多源聚合，支持 1440p / 2K / 4K，从播放列表标题解析集数）
        registerMainAPI(DonghuaAggregatorProvider())

        // 单站点 Provider（备用，如需在列表展示可取消注释）
        // registerMainAPI(Donghuastream())   // donghuastream.org
        // registerMainAPI(SeaTV())           // donghuafun.com
        // registerMainAPI(Animekhor())       // animekhor.org
        // 注意：donghuaworld.com 增加了 Cloudflare 验证盾，已移除

        // Donghuastream 系 extractors
        registerExtractorAPI(Vtbe())
        registerExtractorAPI(waaw())
        registerExtractorAPI(wishfast())
        registerExtractorAPI(FileMoonSx())
        registerExtractorAPI(Ultrahd())
        registerExtractorAPI(PlayStreamplay())

        // Animekhor 系 extractors
        registerExtractorAPI(embedwish())
        registerExtractorAPI(Filelions())
        registerExtractorAPI(VidHidePro5())
        registerExtractorAPI(Swhoi())
        registerExtractorAPI(PlayerDonghuaworld())
        registerExtractorAPI(P2pstream())

        // 共用 / 上游 extractors
        registerExtractorAPI(Rumble())
        registerExtractorAPI(Dailymotion())
        registerExtractorAPI(Geodailymotion())
        registerExtractorAPI(EmturbovidExtractor())
        registerExtractorAPI(Mp4Upload())
    }
}
