package com.kolktech.kahawai.data.repository

import com.kolktech.kahawai.data.network.ApiClient
import com.kolktech.kahawai.testutil.buildTestApiService
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = AuthRepository()
        mockkObject(ApiClient)
        every { ApiClient.apiService() } returns buildTestApiService(server)
    }

    @After
    fun tearDown() {
        unmockkObject(ApiClient)
        server.shutdown()
    }

    @Test
    fun `logout hits the authenticated logout route with the api client mode and refresh token`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        repository.logout("ref1")

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v1/auth/logout", recorded.path)
        val bodyText = recorded.body.readUtf8()
        assertTrue(bodyText.let {
            it.contains(""""client":"api"""") && it.contains(""""refresh_token":"ref1"""")
        })
    }
}
