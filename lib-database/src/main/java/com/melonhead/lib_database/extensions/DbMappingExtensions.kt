package com.melonhead.lib_database.extensions

import com.melonhead.lib_database.chapter.ChapterEntity
import com.melonhead.lib_database.manga.MangaEntity
import com.melonhead.lib_database.manga.MangaTag
import com.melonhead.data_shared.models.Chapter
import com.melonhead.data_shared.models.Manga

fun ChapterEntity.Companion.from(chapter: Chapter): ChapterEntity {
    return ChapterEntity(
        id = chapter.id,
        mangaId = chapter.relationships?.firstOrNull { it.type == "manga" }!!.id,
        chapterTitle = chapter.attributes.title,
        chapter = chapter.attributes.chapter ?: "1",
        createdAt = chapter.attributes.createdAt,
        externalUrl = chapter.attributes.externalUrl,
    )
}

fun MangaEntity.Companion.from(manga: Manga, chosenTitle: String?): MangaEntity {
    val titles = manga.attributes.getEnglishTitles()
    return MangaEntity(
        id = manga.id,
        mangaTitles = titles,
        chosenTitle = chosenTitle ?: titles.last(),
        mangaCoverId = manga.fileName,
        status = manga.attributes.status,
        tags = manga.attributes.tags.mapNotNull {
            val name = it.attributes.name["en"] ?: return@mapNotNull null
            MangaTag(it.id, name)
        },
        contentRating = manga.attributes.contentRating,
        lastChapter = manga.attributes.lastChapter,
        description = manga.attributes.getEnglishDescription(),
    )
}
