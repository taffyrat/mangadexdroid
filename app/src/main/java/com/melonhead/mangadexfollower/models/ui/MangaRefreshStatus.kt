package com.melonhead.mangadexfollower.models.ui

sealed class MangaRefreshStatus {
    val text: String
        get() = when (this) {
            Following -> "Fetching Followed Manga..."
            MangaSeries -> "Fetching Series Info..."
            None -> ""
            ReadStatus -> "Fetching Read Status..."
        }
}

object Following: MangaRefreshStatus()
object MangaSeries: MangaRefreshStatus()
object None: MangaRefreshStatus()
object ReadStatus: MangaRefreshStatus()