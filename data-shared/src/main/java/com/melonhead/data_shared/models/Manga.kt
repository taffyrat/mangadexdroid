package com.melonhead.data_shared.models

@kotlinx.serialization.Serializable
data class Manga(val id: String, val attributes: MangaAttributes, val relationships: List<ChapterRelationships>) {
    private val coverArtRelationships: ChapterRelationships? = relationships.firstOrNull { it.type == "cover_art" }
    val fileName: String? = coverArtRelationships?.attributes?.fileName

    companion object
}
