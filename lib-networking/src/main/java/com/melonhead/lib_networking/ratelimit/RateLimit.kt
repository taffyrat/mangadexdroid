package com.melonhead.lib_networking.ratelimit

import com.melonhead.lib_networking.ratelimit.core.RateLimitRule
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

internal class RateLimit(private val rules: List<RateLimitRule>) {
    suspend fun handleRequest(request: HttpRequestBuilder) {
        rules.firstOrNull { rule -> rule.isMatch(request) }?.onSendRequest(request)
    }

    class Config {
        private val rules = ArrayList<RateLimitRule>()

        fun rule(rule: RateLimitRule) {
            rules.add(rule)
        }

        internal fun build() = rules.toList()
    }

    companion object Plugin : HttpClientPlugin<Config, RateLimit> {
        override val key = AttributeKey<RateLimit>("com.melonhead.mangadexfollower.ratelimit.RateLimit")

        override fun prepare(block: Config.() -> Unit): RateLimit {
            return RateLimit(Config().also(block).build())
        }

        @Suppress("LocalVariableName")
        override fun install(plugin: RateLimit, scope: HttpClient) {
            val BeforeSend = PipelinePhase("BeforeSend")
            scope.sendPipeline.insertPhaseBefore(HttpSendPipeline.Monitoring, BeforeSend)
            scope.sendPipeline.intercept(BeforeSend) {
                plugin.handleRequest(context)
            }
        }
    }
}
