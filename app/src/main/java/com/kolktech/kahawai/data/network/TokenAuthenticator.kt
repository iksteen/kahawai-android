package com.kolktech.kahawai.data.network

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import com.kolktech.kahawai.data.auth.TokenStore
import com.kolktech.kahawai.data.network.dto.RefreshRequest

/// 401 -> refresh -> retry once, mirroring the web client's
/// refreshInFlight guard (web/src/api.ts:53-79): refresh tokens rotate
/// server-side on every use, so concurrent 401s must share ONE refresh —
/// the losers of a naive race would send the already-rotated token,
/// fail, and wipe a session that was actually fine. `authenticate` runs
/// on an OkHttp dispatcher thread, never the main thread, so blocking
/// inside the lock (via runBlocking) is safe here.
class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val plainApiService: () -> ApiService,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null // already retried once; give up

        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        synchronized(refreshLock) {
            val current = tokenStore.accessToken
            // Another thread already refreshed while we waited for the lock.
            if (current != null && current != failedToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $current")
                    .build()
            }

            val refreshToken = tokenStore.refreshToken ?: return null
            val newTokens = try {
                runBlocking {
                    val tokens = plainApiService().refresh(RefreshRequest(refreshToken))
                    tokenStore.save(tokens)
                    tokens
                }
            } catch (e: Exception) {
                null
            }
            if (newTokens == null) {
                runBlocking { tokenStore.clear() }
                return null
            }
            return response.request.newBuilder()
                .header("Authorization", "Bearer ${newTokens.accessToken}")
                .build()
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        val refreshLock = Any()
    }
}
