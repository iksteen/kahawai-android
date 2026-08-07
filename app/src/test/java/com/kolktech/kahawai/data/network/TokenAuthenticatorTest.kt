package com.kolktech.kahawai.data.network

import com.kolktech.kahawai.data.network.dto.RefreshRequest
import com.kolktech.kahawai.data.network.dto.TokenPair
import com.kolktech.kahawai.testutil.FakeTokenStorage
import com.kolktech.kahawai.testutil.httpException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenAuthenticatorTest {

    private fun request(token: String?): Request =
        Request.Builder()
            .url("http://localhost/api/v1/items")
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()

    private fun response401(req: Request, prior: Response? = null): Response =
        Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .apply { prior?.let { priorResponse(it) } }
            .build()

    @Test
    fun `gives up when already retried once`() {
        val api = mockk<ApiService>()
        val storage = FakeTokenStorage(accessToken = "expired", refreshToken = "refresh")
        val authenticator = TokenAuthenticator(storage) { api }

        val firstResp = response401(request("expired"))
        val secondResp = response401(request("expired"), prior = firstResp)

        val result = authenticator.authenticate(null, secondResp)

        assertNull(result)
        coVerify(exactly = 0) { api.refresh(any()) }
    }

    @Test
    fun `returns null when no refresh token is available`() {
        val api = mockk<ApiService>()
        val storage = FakeTokenStorage(accessToken = "expired", refreshToken = null)
        val authenticator = TokenAuthenticator(storage) { api }

        val result = authenticator.authenticate(null, response401(request("expired")))

        assertNull(result)
        coVerify(exactly = 0) { api.refresh(any()) }
    }

    @Test
    fun `retries with already-rotated token without calling refresh`() {
        val api = mockk<ApiService>()
        val storage = FakeTokenStorage(accessToken = "already-new", refreshToken = "refresh")
        val authenticator = TokenAuthenticator(storage) { api }

        val result = authenticator.authenticate(null, response401(request("expired")))

        assertEquals("Bearer already-new", result?.header("Authorization"))
        coVerify(exactly = 0) { api.refresh(any()) }
    }

    @Test
    fun `successful refresh retries with new token and saves it`() {
        val api = mockk<ApiService>()
        val newTokens = TokenPair("new-access", "new-refresh", 3600)
        coEvery { api.refresh(RefreshRequest("refresh")) } returns newTokens
        val storage = FakeTokenStorage(accessToken = "expired", refreshToken = "refresh")
        val authenticator = TokenAuthenticator(storage) { api }

        val result = authenticator.authenticate(null, response401(request("expired")))

        assertEquals("Bearer new-access", result?.header("Authorization"))
        assertEquals("new-access", storage.accessToken)
        assertEquals("new-refresh", storage.refreshToken)
        assertEquals(1, storage.saveCallCount)
    }

    @Test
    fun `refresh 4xx clears tokens and returns null`() {
        val api = mockk<ApiService>()
        coEvery { api.refresh(any()) } throws httpException(401, "invalid refresh token")
        val storage = FakeTokenStorage(accessToken = "expired", refreshToken = "refresh")
        val authenticator = TokenAuthenticator(storage) { api }

        val result = authenticator.authenticate(null, response401(request("expired")))

        assertNull(result)
        assertNull(storage.accessToken)
        assertNull(storage.refreshToken)
        assertEquals(1, storage.clearCallCount)
    }

    @Test
    fun `refresh 5xx preserves tokens and returns null`() {
        val api = mockk<ApiService>()
        coEvery { api.refresh(any()) } throws httpException(503)
        val storage = FakeTokenStorage(accessToken = "expired", refreshToken = "refresh")
        val authenticator = TokenAuthenticator(storage) { api }

        val result = authenticator.authenticate(null, response401(request("expired")))

        assertNull(result)
        assertEquals("expired", storage.accessToken)
        assertEquals("refresh", storage.refreshToken)
        assertEquals(0, storage.clearCallCount)
    }

    @Test
    fun `refresh IOException preserves tokens and returns null`() {
        val api = mockk<ApiService>()
        coEvery { api.refresh(any()) } throws IOException("network down")
        val storage = FakeTokenStorage(accessToken = "expired", refreshToken = "refresh")
        val authenticator = TokenAuthenticator(storage) { api }

        val result = authenticator.authenticate(null, response401(request("expired")))

        assertNull(result)
        assertEquals("expired", storage.accessToken)
        assertEquals("refresh", storage.refreshToken)
        assertEquals(0, storage.clearCallCount)
    }
}
