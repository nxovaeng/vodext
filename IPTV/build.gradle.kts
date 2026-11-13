// use an integer for version numbers
version = 5


cloudstream {
    language = "zh"
    // All of these properties are optional, you can safely remove them

    description = "中国IPTV直播源 - 直连极速起播，支持同频道多源切换与实时EPG"
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
        "Movie",
        "TvSeries"
    )

    iconUrl = "https://vod.zrocf.qzz.io/static/tvlogo.png"
}
