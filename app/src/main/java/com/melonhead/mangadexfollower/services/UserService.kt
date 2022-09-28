package com.melonhead.mangadexfollower.services

import com.melonhead.mangadexfollower.models.content.Chapter
import com.melonhead.mangadexfollower.models.auth.PaginatedResponse
import com.melonhead.mangadexfollower.routes.HttpRoutes
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

interface UserService {
    suspend fun getFollowedChapters(): PaginatedResponse<List<Chapter>>
}

class UserServiceImpl(
    private val client: HttpClient,
    private val tokenProvider: TokenProviderService
): UserService {
    override suspend fun getFollowedChapters(): PaginatedResponse<List<Chapter>> {
        // TODO: set a real error
        val token = tokenProvider.token ?: return PaginatedResponse(0, 0, 0, listOf())
        return client.get(HttpRoutes.USER_FOLLOW_CHAPTERS_URL) {
            headers {
                contentType(ContentType.Application.Json)
                bearerAuth(token.session)
            }
            url {
                encodedParameters.append("translatedLanguage[]", "en")
                parameters.append("order[readableAt]", "desc")
            }
        }.body()
    }
}