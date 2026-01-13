package com.mc2soft.ontv.common.stalker_portal.entities

@kotlinx.serialization.Serializable
data class NetGenre(
    val id: String,
    var title: String,
    //val number: Int? = null,
    //val alias: String? = null,
    //val censored: Int? = null,
    //val modified: String? = null
) {
    companion object {
        const val ALL_ID = "*"
        const val SEARCH_ID = "-search-"
        val favorites = NetGenre("-favorites-", "Favorites")
        val history = NetGenre("-history-", "History")
        fun makeSearch(query: String): NetGenre {
            return NetGenre(SEARCH_ID, query)
        }
    }
    val isSearch: Boolean
        get() = id == SEARCH_ID
}
