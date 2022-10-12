package com.melonhead.mangadexfollower.repositories

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.melonhead.mangadexfollower.App
import com.melonhead.mangadexfollower.db.chapter.ChapterDao
import com.melonhead.mangadexfollower.db.chapter.ChapterEntity
import com.melonhead.mangadexfollower.db.manga.MangaDao
import com.melonhead.mangadexfollower.db.manga.MangaEntity
import com.melonhead.mangadexfollower.extensions.throttleLatest
import com.melonhead.mangadexfollower.logs.Clog
import com.melonhead.mangadexfollower.models.ui.*
import com.melonhead.mangadexfollower.notifications.NewChapterNotification
import com.melonhead.mangadexfollower.services.AppDataService
import com.melonhead.mangadexfollower.services.CoverService
import com.melonhead.mangadexfollower.services.MangaService
import com.melonhead.mangadexfollower.services.UserService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MangaRepository(
    private val externalScope: CoroutineScope,
    private val mangaService: MangaService,
    private val userService: UserService,
    private val appDataService: AppDataService,
    private val coverService: CoverService,
    private val loginFlow: Flow<LoginStatus>,
    private val chapterDb: ChapterDao,
    private val mangaDb: MangaDao,
    private val appContext: Context
) {
    private val refreshMangaThrottled: (Unit) -> Unit = throttleLatest(300L, externalScope, ::refreshManga)

    // combine all manga series and chapters
    val manga = mangaDb.allSeries().combine(chapterDb.allChapters()) { dbSeries, dbChapters ->
        generateUIManga(dbSeries, dbChapters)
    }.shareIn(externalScope, replay = 1, started = SharingStarted.WhileSubscribed())

    private val mutableRefreshStatus = MutableStateFlow<MangaRefreshStatus>(None)
    val refreshStatus = mutableRefreshStatus.shareIn(externalScope, replay = 1, started = SharingStarted.WhileSubscribed())

    init {
        externalScope.launch {
            // refresh manga on login
            loginFlow.collectLatest { if (it is LoginStatus.LoggedIn) refreshMangaThrottled(Unit) }
        }
    }

    suspend fun forceRefresh() {
        if (loginFlow.first() is LoginStatus.LoggedIn) refreshMangaThrottled(Unit)
    }

    private fun generateUIManga(dbSeries: List<MangaEntity>, dbChapters: List<ChapterEntity>): List<UIManga> {
        // map the series and chapters into UIManga, sorted from most recent to least
        val uiManga = dbSeries.mapNotNull { manga ->
            val chapters = dbChapters.filter { it.mangaId == manga.id }.map { chapter ->
                UIChapter(id = chapter.id, chapter = chapter.chapter, title = chapter.chapterTitle, createdDate = chapter.createdAt, read = chapter.readStatus)
            }
            if (chapters.isEmpty()) return@mapNotNull null
            UIManga(id = manga.id, manga.mangaTitle ?: "", chapters = chapters, manga.mangaCoverId)
        }
        if (uiManga.isEmpty()) return emptyList()

        // split into two categories, unread and read
        val hasUnread = uiManga.filter { it.chapters.any { it.read != true } }.sortedByDescending { it.chapters.first().createdDate }
        val allRead = uiManga.filter { it.chapters.all { it.read == true } }.sortedByDescending { it.chapters.first().createdDate }

        return hasUnread + allRead
    }

    // note: unit needs to be included as a param for the throttleLatest call above
    private fun refreshManga(@Suppress("UNUSED_PARAMETER") unit: Unit) = externalScope.launch {
        val token = appDataService.token.firstOrNull() ?: return@launch
        Clog.i("refreshManga")

        mutableRefreshStatus.value = Following
        // fetch chapters from server
        val chaptersResponse = userService.getFollowedChapters(token)
        val chapterEntities = chaptersResponse.map { ChapterEntity.from(it) }

        // add chapters to DB
        chapterDb.insertAll(*chapterEntities.toTypedArray())

        mutableRefreshStatus.value = MangaSeries

        // map app chapters into the manga ids
        val mangaIds = chaptersResponse.mapNotNull { chapters -> chapters.relationships?.firstOrNull { it.type == "manga" }?.id }.toSet()
        // fetch manga series
        val mangaSeries = mangaService.getManga(token, mangaIds.toList())

        mutableRefreshStatus.value = MangaCovers

        val newManga = mutableListOf<MangaEntity>()
        // fetch all covers
        val mangaCovers = coverService.getCovers(token, mangaSeries.map { it.id })

        mangaSeries.forEach { manga ->
            val coverFilename = mangaCovers.firstOrNull { it.mangaId == manga.id }?.fileName
            newManga.add(MangaEntity.from(manga, coverFilename))
        }

        // insert new series into local db
        mangaDb.insertAll(*newManga.toTypedArray())

        mutableRefreshStatus.value = ReadStatus

        // refresh read status for series
        refreshReadStatus(mangaDb.getAllSync(), chapterDb.getAllSync())

        mutableRefreshStatus.value = None

        appDataService.updateLastRefreshDate()
    }

    private suspend fun notifyOfNewChapters() {
        if ((appContext as App).inForeground) return
        val notificationManager = NotificationManagerCompat.from(appContext)
        if (!notificationManager.areNotificationsEnabled()) return
        val installDateSeconds = appDataService.installDateSeconds.firstOrNull() ?: 0L
        Clog.i("notifyOfNewChapters")

        val newChapters = chapterDb.getAllSync().filter { it.readStatus != true }
        val manga = mangaDb.getAllSync()
        val notifyChapters = generateUIManga(manga, newChapters)
        NewChapterNotification.post(appContext, notifyChapters, installDateSeconds)
    }

    private suspend fun refreshReadStatus(manga: List<MangaEntity>, chapters: List<ChapterEntity>) {
        // make sure we have a token
        val token = appDataService.token.firstOrNull() ?: return
        Clog.i("refreshReadStatus")

        val readChapters = mangaService.getReadChapters(manga.map { it.id }, token)
        val chaptersToUpdate = chapters
            .filter { it.readStatus != true && readChapters.contains(it.id) }
            // make a copy with the readStatus set to true
            .map { it.copy(readStatus = true) }

        if (chaptersToUpdate.isEmpty()) {
            notifyOfNewChapters()
            return
        }

        // update the db with the new entities
        chapterDb.update(*chaptersToUpdate.toTypedArray())

        // notify user of new chapters
        notifyOfNewChapters()
    }
}