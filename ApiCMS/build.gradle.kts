// use an integer for version numbers
version = 9


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "TvSeries Anime and Movies"
    language    = "zh"
    authors = listOf("nxovaeng")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    //  Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama,
    //  Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast
    tvTypes = listOf("Movie", "TvSeries","AnimeMovie","Anime")
    iconUrl="https://www.moduzy1.com/favicon.ico"

    isCrossPlatform = true
}
