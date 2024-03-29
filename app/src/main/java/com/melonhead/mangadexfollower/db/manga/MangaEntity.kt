package com.melonhead.mangadexfollower.db.manga

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.melonhead.mangadexfollower.models.content.Manga

@Entity(tableName = "manga")
data class MangaEntity(
    @PrimaryKey(autoGenerate = false) val id: String,
    @ColumnInfo(name = "manga_title") val mangaTitle: String?,
    @ColumnInfo(name = "manga_cover_id") val mangaCoverId: String? = null,
    @ColumnInfo(name = "use_webview") val useWebview: Boolean = false,
) {
    companion object {
        fun from(manga: Manga): MangaEntity {
            return MangaEntity(
                id = manga.id,
                mangaTitle = manga.attributes.title.values.first(),
                mangaCoverId = manga.fileName
            )
        }
    }
}