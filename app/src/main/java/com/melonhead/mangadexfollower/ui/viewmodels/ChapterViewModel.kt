package com.melonhead.mangadexfollower.ui.viewmodels

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.melonhead.lib_logging.Clog
import com.melonhead.mangadexfollower.models.ui.UIChapter
import com.melonhead.mangadexfollower.models.ui.UIManga
import com.melonhead.mangadexfollower.repositories.MangaRepository
import com.melonhead.mangadexfollower.services.AppDataService
import com.melonhead.mangadexfollower.ui.scenes.chapter_reader.native_chapter_reader.ChapterActivity
import com.melonhead.mangadexfollower.ui.scenes.chapter_reader.web_chapter_reader.WebViewActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.Integer.max
import java.lang.Integer.min

class ChapterViewModel(
    private val mangaRepository: MangaRepository,
    appDataService: AppDataService
): ViewModel() {
    private val mutableChapterData = MutableStateFlow<List<String>?>(null)
    val chapterData = mutableChapterData.asStateFlow()

    val chapterTapAreaSize = appDataService.chapterTapAreaSize

    private val mutablePageIndex = MutableStateFlow(0)
    val currentPage = mutableChapterData.combine(mutablePageIndex) { data, index -> data?.getOrNull(index) }

    private lateinit var manga: UIManga
    private lateinit var chapter: UIChapter

    private val chapterLoadMutex = Mutex()

    private fun loadChapter(activity: Activity, chapterId: String) {
        viewModelScope.launch {
            chapterLoadMutex.withLock {
                if (mutableChapterData.value != null) return@withLock
                val pages = if (manga.useWebview) null else mangaRepository.getChapterData(chapterId)
                if (pages != null) {
                    mutableChapterData.value = pages
                } else {
                    // use secondary render style
                    if (!manga.useWebview) {
                        mangaRepository.setUseWebview(manga, true)
                    }
                    Clog.i("Falling back to webview")
                    // fallback to secondary render style
                    val intent = WebViewActivity.newIntent(activity, chapter, manga)
                    activity.finish()
                    activity.startActivity(intent)
                }
            }
        }
    }

    fun prevPage() {
        mutablePageIndex.value = max(0, mutablePageIndex.value - 1)
    }

    fun nextPage() {
        mutablePageIndex.value = min((mutableChapterData.value?.count()?.minus(1)) ?: 0, mutablePageIndex.value + 1)
        // TODO: close if at end and no following chapter?
    }

    @Suppress("DEPRECATION")
    fun parseIntent(activity: Activity, intent: Intent) {
        manga = intent.getParcelableExtra(ChapterActivity.EXTRA_UIMANGA)!!
        chapter = intent.getParcelableExtra(ChapterActivity.EXTRA_UICHAPTER)!!
        loadChapter(activity, chapter.id)
    }

    fun markAsRead() = viewModelScope.launch(Dispatchers.IO) {
        mangaRepository.markChapterRead(manga, chapter)
    }
}
