package com.melonhead.mangadexfollower.models.ui

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class UIChapter(val id: String, val chapter: String?, val title: String?, val createdDate: Instant, val read: Boolean?) {
    val webAddress: String = "https://mangadex.org/chapter/$id"
}