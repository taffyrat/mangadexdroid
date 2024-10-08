package com.melonhead.feature_manga_list

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.melonhead.data_at_home.AtHomeService
import com.melonhead.data_manga.models.ReadingStatus
import com.melonhead.data_manga.services.MangaService
import com.melonhead.data_rating.services.RatingService
import com.melonhead.data_shared.models.ui.*
import com.melonhead.data_user.services.UserService
import com.melonhead.lib_app_context.AppContext
import com.melonhead.lib_app_data.AppData
import com.melonhead.lib_app_events.AppEventsRepository
import com.melonhead.lib_app_events.events.*
import com.melonhead.lib_chapter_cache.ChapterCache
import com.melonhead.lib_core.extensions.throttleLatest
import com.melonhead.lib_database.chapter.ChapterDao
import com.melonhead.lib_database.chapter.ChapterEntity
import com.melonhead.lib_database.extensions.from
import com.melonhead.lib_database.manga.MangaDao
import com.melonhead.lib_database.manga.MangaEntity
import com.melonhead.lib_database.readmarkers.ReadMarkerDao
import com.melonhead.lib_database.readmarkers.ReadMarkerEntity
import com.melonhead.lib_logging.Clog
import com.melonhead.lib_notifications.NewChapterNotificationChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture

internal interface MangaRepository {
    val manga: Flow<List<UIManga>>
    val refreshStatus: Flow<MangaRefreshStatus>
    fun rateManga(mangaId: String, rating: Int)
    suspend fun getChapterData(mangaId: String, chapterId: String): List<String>?
}

internal class MangaRepositoryImpl(
    private val externalScope: CoroutineScope,
    private val userService: UserService,
    private val appData: AppData,
    private val atHomeService: AtHomeService,
    private val chapterDb: ChapterDao,
    private val mangaDb: MangaDao,
    private val readMarkerDb: ReadMarkerDao,
    private val context: Context,
    private val chapterCache: ChapterCache,
    private val appEventsRepository: AppEventsRepository,
    private val newChapterNotificationChannel: NewChapterNotificationChannel,
    private val appContext: AppContext,
    private val mangaService: MangaService,
    private val ratingService: RatingService,
): MangaRepository {
    private val refreshMangaThrottled: (AppEvent) -> Unit = throttleLatest(300L, externalScope) { event ->
        refreshManga((event as? UserEvent.RefreshManga)?.completionJob)
    }

    // combine all manga series and chapters
    override val manga = combine(mangaDb.allSeries(), chapterDb.allChapters(), readMarkerDb.allMarkers(), chapterCache.cachingStatus) { dbSeries, dbChapters, _, cacheStatus ->
        generateUIManga(dbSeries, dbChapters)
    }.shareIn(externalScope, replay = 1, started = SharingStarted.WhileSubscribed())

    private val mutableRefreshStatus = MutableStateFlow<MangaRefreshStatus>(None)
    override val refreshStatus = mutableRefreshStatus.shareIn(externalScope, replay = 0, started = SharingStarted.WhileSubscribed())

    private var isLoggedIn: Boolean = false

    init {
        Clog.i("MangaRepository init")
        externalScope.launch {
            // refresh manga on login
            try {
                // TODO: it's easy to miss necessary events with this pattern, it would be better to include a way to pass in the list of expected events
                appEventsRepository.events.collectLatest { event ->
                    launch {
                        when (event) {
                            is AuthenticationEvent.LoggedIn -> {
                                if (!isLoggedIn) {
                                    isLoggedIn = true
                                    refreshMangaThrottled(event)
                                }
                            }
                            is AuthenticationEvent.LoggedOut -> {
                                isLoggedIn = false
                            }
                            is AppLifecycleEvent.AppForegrounded -> {
                                refreshMangaThrottled(event)
                            }
                            is UserEvent.RefreshManga -> {
                                refreshMangaThrottled(event)
                            }
                            is UserEvent.SetMarkChapterRead -> {
                                markChapterRead(event.mangaId, event.chapterId, event.read)
                            }
                            is UserEvent.SetUseWebView -> {
                                setUseWebview(event.mangaId, event.useWebView)
                            }
                            is UserEvent.UpdateChosenMangaTitle -> {
                                updateChosenTitle(event.mangaId, event.title)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        refreshMangaThrottled(AppLifecycleEvent.AppForegrounded)
    }

    private fun generateUIManga(dbSeries: List<MangaEntity>, dbChapters: List<ChapterEntity>): List<UIManga> {
        // map the series and chapters into UIManga, sorted from most recent to least
        val uiManga = dbSeries.mapNotNull { manga ->
            var hasExternalChapters = false
            val chapters = dbChapters.filter { it.mangaId == manga.id }.map { chapter ->
                val read = readMarkerDb.getEntityByChapter(chapter.mangaId, chapter.chapter)?.readStatus == true
                hasExternalChapters = hasExternalChapters || chapter.externalUrl != null
                UIChapter(
                    id = chapter.id,
                    chapter = chapter.chapter,
                    title = chapter.chapterTitle,
                    createdDate = chapter.createdAt.epochSeconds,
                    read = read,
                    externalUrl = chapter.externalUrl,
                    cachedPages = chapterCache.getChapterPageCountFromCache(manga.id, chapter.id)
                )
            }
            if (chapters.isEmpty()) return@mapNotNull null
            UIManga(
                id = manga.id,
                manga.chosenTitle ?: "",
                chapters = chapters,
                manga.mangaCoverId,
                useWebview = hasExternalChapters || manga.useWebview,
                altTitles = manga.mangaTitles,
                tags = manga.tags.sortedBy { it.id }.map { it.name },
                status = manga.status,
                contentRating = manga.contentRating,
                lastChapter = manga.lastChapter,
                description = manga.description,
            )
        }
        if (uiManga.isEmpty()) return emptyList()

        // split into two categories, unread and read
        val hasUnread = uiManga.filter { it.chapters.any { !it.read } }.sortedByDescending { it.chapters.first().createdDate }
        val allRead = uiManga.filter { it.chapters.all { it.read } }.sortedByDescending { it.chapters.first().createdDate }

        return hasUnread + allRead
    }

    // note: unit needs to be included as a param for the throttleLatest call above
    private fun refreshManga(refreshCompletable: (CompletableFuture<Unit>)?) = externalScope.launch {
        // wait for refresh token to complete
        val refreshCompletionJob = CompletableFuture<Unit>()
        appEventsRepository.postEvent(AuthenticationEvent.RefreshToken(completionJob = refreshCompletionJob))
        refreshCompletionJob.await()

        val token = appData.getToken()
        if (token == null) {
            Clog.i("Failed to refresh token")
            return@launch
        }

        Clog.i("refreshManga")

        mutableRefreshStatus.value = Following
        // fetch chapters from server
        val chaptersResponse = userService.getFollowedChapters()
        val chapterEntities = chaptersResponse.map { ChapterEntity.from(it) }
        val newChapters = chapterEntities.filter { !chapterDb.containsChapter(it.id) }

        Clog.i("New chapters: ${newChapters.count()}")

        if (newChapters.isNotEmpty()) {
            mutableRefreshStatus.value = MangaSeries
            // add chapters to DB
            chapterDb.insertAll(*newChapters.toTypedArray())

            // map app chapters into the manga ids
            val mangaIds = chaptersResponse.mapNotNull { chapters -> chapters.relationships?.firstOrNull { it.type == "manga" }?.id }.toSet()

            // fetch manga series info
            Clog.i("New manga: ${mangaIds.count()}")

            if (mangaIds.isNotEmpty()) {
                val mangaSeries = mangaService.getManga(mangaIds.toList())
                val manga = mangaSeries.map {
                    // grab the chosen title from the DB
                    MangaEntity.from(it, mangaDb.getMangaByIdAsync(it.id).first()?.chosenTitle)
                }

                // insert new series into local db
                mangaDb.insertAll(*manga.toTypedArray())
            }
        }

        mutableRefreshStatus.value = ReadStatus
        // refresh read status for series
        refreshReadStatus()

        mutableRefreshStatus.value = None
        appData.updateLastRefreshDate()

        // mark refresh as completed
        refreshCompletable?.complete(Unit)
    }

    private suspend fun handleUnreadChapters() {
        mutableRefreshStatus.value = FetchingChapters
        val manga = mangaDb.getAllSync()
        val newChapters = chapterDb.getAllSync().filter { readMarkerDb.isRead(it.mangaId, it.chapter) != true }
        chapterCache.cacheImagesForChapters(manga, newChapters)

        if (appContext.isInForeground) return
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return
        val installDateSeconds = appData.installDateSeconds.firstOrNull() ?: 0L
        Clog.i("Posting notification for new chapters")

        val notifyChapters = generateUIManga(manga, newChapters)
        newChapterNotificationChannel.post(context, notifyChapters, installDateSeconds)
    }

    private suspend fun refreshReadStatus() {
        Clog.i("refreshReadStatus")
        val manga = mangaDb.getAllSync()
        val chapters = chapterDb.getAllSync()

        // ensure all chapters have read markers
        val readMarkers = chapters.map { ReadMarkerEntity.from(it, null) }
        readMarkerDb.insertAll(*readMarkers.toTypedArray())

        val readChapters = mangaService.getReadChapters(manga.map { it.id })
        val chaptersToUpdate = chapters
            // filter out chapters already marked as read in the db
            .filter {
                val readStatus = readMarkerDb.isRead(it.mangaId, it.chapter)
                readStatus == null && readChapters.contains(it.id)
            }

        if (chaptersToUpdate.isEmpty()) {
            handleUnreadChapters()
            return
        }

        // update the db with the new entities
        chapterDb.update(*chaptersToUpdate.toTypedArray())

        val readMarkersToUpdate = chaptersToUpdate
            .filter {
                val readStatus = readMarkerDb.isRead(it.mangaId, it.chapter)
                readStatus == null
            }
            .map { ReadMarkerEntity.from(it, true) }
        readMarkerDb.update(*readMarkersToUpdate.toTypedArray())

        // notify user of new chapters
        handleUnreadChapters()
    }

    // currently trying to deprecate this function, and use chapterCache directly
    override suspend fun getChapterData(mangaId: String, chapterId: String): List<String>? {
        val chapterFiles = chapterCache.getChapterFromCache(mangaId, chapterId)
        if (chapterFiles.isNotEmpty()) return chapterFiles

        Clog.i("Chapter not found in cache: $mangaId, $chapterId")
        Clog.e("Chapter not found in cache", RuntimeException("Chapter not found in cache"))
        val chapterData = atHomeService.getChapterData(chapterId)
        return if (appData.useDataSaver) {
            chapterData?.pagesDataSaver()
        } else {
            chapterData?.pages()
        }
    }

    override fun rateManga(mangaId: String, rating: Int) {
        externalScope.launch {
            ratingService.setRating(mangaId, rating)
        }
    }

    private fun markChapterRead(mangaId: String, chapterId: String, read: Boolean) {
        externalScope.launch {
            val manga = mangaDb.getMangaById(mangaId)
            val chapter = chapterDb.getChapterForId(chapterId)
            val entity = readMarkerDb.getEntityByChapter(
                mangaId = mangaId,
                chapter = chapter.chapter
            ) ?: return@launch
            if (read) {
                newChapterNotificationChannel.dismissNotification(context, mangaId, chapterId)
                chapterCache.clearChapterFromCache(mangaId = mangaId, chapterId = chapterId)
            }
            readMarkerDb.update(entity.copy(readStatus = read))
            mangaService.changeReadStatus(
                mangaId = mangaId,
                chapterId = chapterId,
                readStatus = read
            )

            val readingStatus = mangaService.getSeriesReadingStatus(mangaId) ?: return@launch
            when (readingStatus) {
                ReadingStatus.ReReading,
                ReadingStatus.Reading,
                -> {
                    if (read && manga?.lastChapter == chapter.chapter && appData.autoMarkMangaCompleted.firstOrNull() == true) {
                        mangaService.changeSeriesReadingStatus(mangaId, ReadingStatus.Completed)
                        appEventsRepository.postEvent(SystemLogicEvents.PromptMangaRating(mangaId))
                    }
                }

                ReadingStatus.OnHold -> {
                    if (read && appData.autoMarkMangaReading.firstOrNull() == true) {
                        mangaService.changeSeriesReadingStatus(mangaId, ReadingStatus.Reading)
                    }
                }

                ReadingStatus.Completed,
                ReadingStatus.PlanToRead,
                ReadingStatus.Dropped -> {
                    // no-op
                }
            }
        }
    }

    private fun setUseWebview(mangaId: String, useWebView: Boolean) {
        externalScope.launch {
            val entity = mangaDb.mangaByIdAsyncDistinct(mangaId).first() ?: return@launch
            mangaDb.update(entity.copy(useWebview = useWebView))
        }
    }

    private fun updateChosenTitle(mangaId: String, chosenTitle: String) {
        externalScope.launch {
            val entity = mangaDb.mangaByIdAsyncDistinct(mangaId).first() ?: return@launch
            if (!entity.mangaTitles.contains(chosenTitle)) return@launch
            mangaDb.update(entity.copy(chosenTitle = chosenTitle))
        }
    }
}
