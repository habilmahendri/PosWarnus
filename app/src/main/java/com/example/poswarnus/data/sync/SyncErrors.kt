package com.example.poswarnus.data.sync

import com.example.poswarnus.data.remote.NoSessionException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException

object SyncErrors {
    private const val MAX_LENGTH = 200

    private fun isOffline(e: Throwable) =
        e is IOException || e is HttpRequestException || e is HttpRequestTimeoutException

    private fun isExpiredSession(e: Throwable) = e is RestException && e.statusCode == 401

    /** True for errors where retrying the next row is pointless: no network or no usable login. */
    fun stopsBatch(e: Throwable): Boolean = e is NoSessionException || isOffline(e) || isExpiredSession(e)

    /**
     * "<prefix>: <ClassName>: <detail>" cut to 200 chars. Never uses `RestException.message` (it embeds the request URL and
     * headers, including the bearer token) nor the message of Ktor network exceptions (they embed the URL).
     */
    fun describe(e: Throwable): String {
        val prefix = when {
            e is NoSessionException || isExpiredSession(e) -> "session"
            isOffline(e) -> "offline"
            else -> "rejected"
        }
        val name = e.javaClass.simpleName.ifEmpty { "Exception" }
        val detail = when (e) {
            is RestException -> listOfNotNull(e.error, e.description).joinToString(" ")
            is HttpRequestException, is HttpRequestTimeoutException -> ""
            else -> e.message.orEmpty()
        }
        val text = if (detail.isEmpty()) "$prefix: $name" else "$prefix: $name: $detail"
        return text.take(MAX_LENGTH)
    }
}
