package com.melonhead.mangadexfollower.routes

object HttpRoutes {
    private const val BASE_URL = "https://api.mangadex.org"

    private const val AUTH_ROUTE = "${BASE_URL}/auth"
    private const val USER_ROUTE = "${BASE_URL}/user"
    private const val MANGA_ROUTE = "${BASE_URL}/manga"
    private const val AT_HOME_ROUTE = "${BASE_URL}/at-home/server"

    const val LOGIN_URL = "${AUTH_ROUTE}/login"
    const val REFRESH_TOKEN_URL = "${AUTH_ROUTE}/refresh"

    const val MANGA_URL = "${MANGA_ROUTE}/"
    const val MANGA_READ_MARKERS_URL = "${MANGA_ROUTE}/read"

    const val ID_PLACEHOLDER = "{id}"
    const val MANGA_READ_CHAPTER_MARKERS_URL = "${MANGA_ROUTE}/$ID_PLACEHOLDER/read"

    const val USER_FOLLOW_CHAPTERS_URL = "${USER_ROUTE}/follows/manga/feed"
    const val USER_ME_URL = "${USER_ROUTE}/me"

    const val CHAPTER_DATA_URL = "${AT_HOME_ROUTE}/"
}