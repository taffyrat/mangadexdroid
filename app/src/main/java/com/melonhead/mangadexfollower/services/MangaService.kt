package com.melonhead.mangadexfollower.services

import com.melonhead.mangadexfollower.routes.HttpRoutes.MANGA_URL
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*


interface MangaService {
    suspend fun getManga(): String
}

class MangaServiceImpl(
    private val client: HttpClient,
): MangaService {
    override suspend fun getManga(): String {
        return client.get(MANGA_URL) {
            headers {
                contentType(ContentType.Application.Json)
            }
        }.bodyAsText()
    }
}