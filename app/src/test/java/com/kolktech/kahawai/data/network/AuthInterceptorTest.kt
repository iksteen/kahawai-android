package com.kolktech.kahawai.data.network

import com.kolktech.kahawai.testutil.FakeTokenStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Test

class AuthInterceptorTest {

    private fun request(): Request = Request.Builder().url("http://localhost/api/v1/items").build()

    private fun fakeResponse(req: Request): Response =
        Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()

    @Test
    fun `adds bearer header when token present`() {
        val storage = FakeTokenStorage(accessToken = "abc123")
        val interceptor = AuthInterceptor(storage)
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request()
        every { chain.proceed(any()) } answers { fakeResponse(firstArg()) }

        interceptor.intercept(chain)

        verify { chain.proceed(match { it.header("Authorization") == "Bearer abc123" }) }
    }

    @Test
    fun `passes request through unmodified when no token`() {
        val storage = FakeTokenStorage(accessToken = null)
        val interceptor = AuthInterceptor(storage)
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request()
        every { chain.proceed(any()) } answers { fakeResponse(firstArg()) }

        interceptor.intercept(chain)

        verify { chain.proceed(match { it.header("Authorization") == null }) }
    }
}
