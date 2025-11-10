// use an integer for version numbers
version = 9


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Anime and Movies"
    language    = "zh"
    authors = listOf("Phisher98")

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
    tvTypes = listOf("AnimeMovie","Anime")
    iconUrl="https://i3.wp.com/animekhor.org/wp-content/uploads/2022/02/cropped-logo-180x180.png"

    isCrossPlatform = true
}
