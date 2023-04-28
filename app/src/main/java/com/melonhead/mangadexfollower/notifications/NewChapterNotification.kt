package com.melonhead.mangadexfollower.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import com.melonhead.mangadexfollower.R
import com.melonhead.mangadexfollower.logs.Clog
import com.melonhead.mangadexfollower.models.ui.UIChapter
import com.melonhead.mangadexfollower.models.ui.UIManga
import com.melonhead.mangadexfollower.ui.scenes.MainActivity
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object NewChapterNotification {
    const val MANGA_EXTRA = "manga_extra"
    const val CHAPTER_EXTRA = "chapter_extra"
    private const val CHANNEL_ID = "new_chapters"
    private const val CHANNEL_NAME = "New Chapter"

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            val manager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun pendingIntent(context: Context, uiManga: UIManga, uiChapter: UIChapter): PendingIntent? {
        val mainActivityIntent = Intent(context, MainActivity::class.java)

        mainActivityIntent.putExtra(MANGA_EXTRA, Json.encodeToString(uiManga))
        mainActivityIntent.putExtra(CHAPTER_EXTRA, Json.encodeToString(uiChapter))

        return TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(mainActivityIntent)
            getPendingIntent(uiChapter.id.hashCode(), PendingIntent.FLAG_IMMUTABLE)
        }
    }

    private fun buildNotification(context: Context, pendingIntent: PendingIntent, uiManga: UIManga, uiChapter: UIChapter): Notification {
        val text = if (uiChapter.title.isNullOrBlank()) uiChapter.chapter else "${uiChapter.chapter} - ${uiChapter.title}"
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(uiManga.title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
    }

    suspend fun post(context: Context, series: List<UIManga>, installDateSeconds: Long) {
        // set up channel
        createNotificationChannel(context)

        val notificationManager = NotificationManagerCompat.from(context)

        Clog.i("post: New chapters for ${series.count()} manga")
        series.forEach { manga ->
            manga.chapters.filter { it.createdDate.epochSeconds >= installDateSeconds }.forEach chapters@{ uiChapter ->
                val pendingIntent = pendingIntent(context, manga, uiChapter) ?: return@chapters
                val notification = buildNotification(context, pendingIntent, manga, uiChapter)
                notificationManager.notify(notificationId(manga, uiChapter), notification)
                delay(1000) // ensures android actually posts all notifications
            }
        }
    }

    fun dismissNotification(context: Context, manga: UIManga, chapter: UIChapter) {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(notificationId(manga, chapter))
    }
    private fun notificationId(manga: UIManga, chapter: UIChapter): Int {
        return manga.id.hashCode() + chapter.id.hashCode()
    }
}