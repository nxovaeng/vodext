
# CloudStream3 中内置的类介绍

## Extractor 系统

### 内置的主要 Extractor 类型

// 视频网站解析器
StreamWishExtractor   // 支持 streamwish.to 等站点
VidHidePro           // 支持 vidhide.com 等加密视频
VidStack             // 支持 vidstack.stream 类型站点
VidhideExtractor     // 支持 vidhide 系列站点
Streamtape          // streamtape.com
DoodStream          // dood.watch 系列
Solidfiles          // solidfiles.com
MixDrop            // mixdrop.co
Mp4Upload          // mp4upload.com
StreamSB           // streamsb.net

### 如何判断是否可以复用

- 通过查看网页源码中的播放器特

// StreamWish 特征
player = jwplayer("vplayer");
player.setup({
    sources: [{file:"..."}],
    ...
})

// VidHide 特征
var player = new Plyr('#player', {...});

// VidStack 特征
const player = new Player({
    src: "...",
    ...
});

- 通过 URL 模式匹配：

StreamWish: watch.*.to/e/*, streamwish.*/e/*
VidHide: vh.*/e/*, vidhide.*/v/*
VidStack: vidstack.*/embed/*

- 举例说明如何复用：

// 1. 如果你的站点使用 StreamWish 播放器
class MyExtractor : StreamWishExtractor() {
    override var mainUrl = "<https://my-video-site.com>"
    override val requiresReferer = true  // 如果需要 referer
}

// 2. 如果使用 VidHide
class MyVidHide : VidhideExtractor() {
    override var name = "MyVidHide"
    override var mainUrl = "<https://my-vidhide.com>"
}

// 3. 如果使用 VidStack
class MyVidStack : VidStack() {
    override var mainUrl = "<https://my-vidstack.com>"
}

如何选择合适的 Extractor：

检查视频页面源码

寻找播放器特征

测试 URL 模式是否匹配

观察请求头和参数是否相似

测试是否能够正确提取视频地址

- 示例代码

 针对 DPlayer，可以尝试使用通用的解析方式:

class JisuExtractor : ExtractorApi() {
    override var name = "Jisu"
    override var mainUrl = "<https://vv.jisuzyv.com>"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // 类似 Rumble 的实现方式
        val response = app.get(url, referer = referer ?: "$mainUrl/")
        val document = response.document

        // 提取 DPlayer 配置
        val playerScript = document.selectFirst("script:containsData(DPlayer)")?.data()
            ?: return

        // 提取 m3u8 地址
        val m3u8Regex = """url:\s*['"]([^'"]+\.m3u8)['"]""".toRegex()
        val m3u8Match = m3u8Regex.find(playerScript)?.groupValues?.get(1)
            ?: return

        // 生成播放链接
        M3u8Helper.generateM3u8(
            name,
            m3u8Match,
            referer ?: mainUrl
        ).forEach(callback)
    }
}
