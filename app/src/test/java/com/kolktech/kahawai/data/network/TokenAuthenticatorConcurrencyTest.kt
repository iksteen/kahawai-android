package com.kolktech.kahawai.data.network

import com.kolktech.kahawai.testutil.FakeTokenStorage
import com.kolktech.kahawai.testutil.buildTestApiService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/// Exercises the actual race TokenAuthenticator.refreshLock guards
/// against: two threads both holding the pre-refresh access token get
/// 401'd around the same time, and only one of them should ever hit the
/// refresh endpoint (refresh tokens rotate server-side on every use, so
/// a second real refresh call would silently invalidate the first).
class TokenAuthenticatorConcurrencyTest {

    private lateinit var server: MockWebServer
    private val refreshCallCount = AtomicInteger(0)

    @Before
    fun setUp() {
        server = MockWebServer()
        refreshCallCount.set(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/v1/auth/refresh" -> {
                    refreshCallCount.incrementAndGet()
                    MockResponse()
                        .setResponseCode(200)
                        .setBodyDelay(300, TimeUnit.MILLISECONDS)
                        .setBody("""{"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600}""")
                }
                request.getHeader("Authorization") == "Bearer old-token" -> MockResponse().setResponseCode(401)
                else -> MockResponse().setResponseCode(200).setBody("{}")
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `concurrent 401s collapse into exactly one refresh call`() {
        val storage = FakeTokenStorage(accessToken = "old-token", refreshToken = "refresh-token")
        val plainApi = buildTestApiService(server)
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(storage))
            .authenticator(TokenAuthenticator(storage) { plainApi })
            .build()

        val executor = Executors.newFixedThreadPool(2)
        val calls = List(2) {
            executor.submit<Int> {
                client.newCall(Request.Builder().url(server.url("/api/v1/items")).build())
                    .execute()
                    .use { it.code }
            }
        }
        val codes = calls.map { it.get(10, TimeUnit.SECONDS) }
        executor.shutdown()

        assertEquals(listOf(200, 200), codes)
        assertEquals(1, refreshCallCount.get())
        assertEquals("new-access", storage.accessToken)
        assertEquals(1, storage.saveCallCount)
    }
}
