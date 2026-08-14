package com.kolktech.kahawai.ui.login

import app.cash.turbine.test
import com.kolktech.kahawai.R
import com.kolktech.kahawai.data.auth.TokenStore
import com.kolktech.kahawai.data.network.ApiClient
import com.kolktech.kahawai.testutil.MainDispatcherRule
import com.kolktech.kahawai.testutil.buildTestApiService
import com.kolktech.kahawai.testutil.relaxedApplication
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer
    private val application = relaxedApplication()
    private val tokenStore = mockk<TokenStore>(relaxed = true)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        mockkObject(ApiClient)
        every { ApiClient.plainApiService() } returns buildTestApiService(server)
    }

    @After
    fun tearDown() {
        unmockkObject(ApiClient)
        server.shutdown()
    }

    private fun vm() = LoginViewModel(application, tokenStore)

    @Test
    fun `login with blank username sets error state without calling the network or saving tokens`() = runTest {
        val viewModel = vm()

        viewModel.state.test {
            assertEquals(LoginState.Idle, awaitItem())
            viewModel.login("", "pass")
            val error = awaitItem() as LoginState.Error
            assertEquals("res:${R.string.login_enter_username_password}", error.message)
        }
        assertEquals(0, server.requestCount)
        coVerify(exactly = 0) { tokenStore.save(any()) }
    }

    @Test
    fun `login with empty password sets error state without calling the network`() = runTest {
        val viewModel = vm()

        viewModel.state.test {
            assertEquals(LoginState.Idle, awaitItem())
            viewModel.login("user", "")
            val error = awaitItem() as LoginState.Error
            assertEquals("res:${R.string.login_enter_username_password}", error.message)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `login success saves tokens and transitions to LoggedIn`() = runTest {
        server.enqueue(MockResponse().setBody("""{"access_token":"tok1","refresh_token":"ref1","expires_in":3600}"""))
        val viewModel = vm()

        viewModel.state.test {
            assertEquals(LoginState.Idle, awaitItem())
            viewModel.login("user", "pass")
            var item = awaitItem()
            if (item is LoginState.Loading) item = awaitItem()
            assertEquals(LoginState.LoggedIn, item)
        }
        coVerify(exactly = 1) { tokenStore.save(match { it.accessToken == "tok1" && it.refreshToken == "ref1" }) }
    }

    @Test
    fun `login failure surfaces the formatted error sentinel with the readable message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val viewModel = vm()

        viewModel.state.test {
            assertEquals(LoginState.Idle, awaitItem())
            viewModel.login("user", "pass")
            var item = awaitItem()
            if (item is LoginState.Loading) item = awaitItem()
            val error = item as LoginState.Error
            assertEquals("res:${R.string.login_failed}:boom", error.message)
        }
    }
}
