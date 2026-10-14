// use an integer for version numbers
version = 1


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "瓜子影视 (App私有协议)"
    language    = "zh"
    authors = listOf("nxovaeng")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 3 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    //  Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama,
    //  Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast
    tvTypes = listOf("Movie", "TvSeries", "Anime")

    isCrossPlatform = true
}
