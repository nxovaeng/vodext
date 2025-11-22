// use an integer for version numbers
version = 1


cloudstream {
    language = "zh"
    // All of these properties are optional, you can safely remove them

    description = "中国IPTV直播源 - 支持央视、卫视等频道"
    authors = listOf("nxovaeng")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "Live",
    )

    iconUrl = "https://vod.zrocf.qzz.io/static/tvlogo.png"
}
