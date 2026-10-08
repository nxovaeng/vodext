package com.Donghuastream

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.Dailymotion
import com.lagradost.cloudstream3.extractors.EmturbovidExtractor
import com.lagradost.cloudstream3.extractors.Geodailymotion
import com.lagradost.cloudstream3.extractors.Mp4Upload

/** 动漫插件：合并 Animekhor + Donghuastream（4 个站点） */
@CloudstreamPlugin
class DonghuastreamPlugin : BasePlugin() {
    override fun load() {
        // Providers
        registerMainAPI(Donghuastream())   // donghuastream.org
        registerMainAPI(SeaTV())           // donghuafun.com
        registerMainAPI(Animekhor())       // animekhor.org
        registerMainAPI(Donghuaword())     // donghuaworld.com

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
