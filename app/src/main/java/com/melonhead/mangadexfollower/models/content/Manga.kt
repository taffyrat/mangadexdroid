package com.melonhead.mangadexfollower.models.content

@kotlinx.serialization.Serializable
data class MangaAttributes(val title: Map<String, String>)

@kotlinx.serialization.Serializable
data class Manga(val id: String, val attributes: MangaAttributes)

@kotlinx.serialization.Serializable
data class MangaResponse(val data: Manga)