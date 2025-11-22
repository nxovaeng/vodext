package com.horis.cloudstreamplugins

// Episode
data class Episode(
    val complate: String,
    val ep: String,
    val id: String,
    val s: String,
    val t: String,
    val time: String
)

// EpisodesData
data class EpisodesData(
    val episodes: List<Episode>?,
    val nextPage: Int,
    val nextPageSeason: String,
    val nextPageShow: Int,
)

// MainPage
data class MainPage(
    val post: List<PostCategory>
)

// PlayList
class PlayList : ArrayList<PlayListItem>()

// PlayListItem
data class PlayListItem(
    val image: String,
    val sources: List<Source>,
    val tracks: List<Tracks>?,
    val title: String
)

// PostCategory
data class PostCategory(
    val ids: String,
    val cate: String
)

// PostData
data class PostData(
    val desc: String?,
    val director: String?,
    val ua: String?,
    val episodes: List<Episode?>,
    val genre: String?,
    val nextPage: Int?,
    val nextPageSeason: String?,
    val nextPageShow: Int?,
    val season: List<Season>?,
    val title: String,
    val year: String,
    val cast: String?,
    val match: String?,
    val runtime: String?,
    var suggest: List<Suggest>?,
)

// SearchData
data class SearchData(
    val head: String,
    val searchResult: List<SearchResult>,
    val type: Int
)

// SearchResult
data class SearchResult(
    val id: String,
    val t: String
)

// Season
data class Season(
    val ep: String,
    val id: String,
    val s: String,
    val sele: String
)

// Source
data class Source(
    val file: String,
    val label: String,
    val type: String
)

// Suggest
data class Suggest (
  var id : String
)

// Tracks
data class Tracks(
    val kind: String?,
    val file: String?,
    val label: String?,
)
