package com.kolktech.kahawai.ui.setup

import app.cash.turbine.test
import com.kolktech.kahawai.R
import com.kolktech.kahawai.data.auth.ServerConfigStore
import com.kolktech.kahawai.data.auth.TokenStore
import com.kolktech.kahawai.data.network.ApiClient
import com.kolktech.kahawai.testutil.MainDispatcherRule
import com.kolktech.kahawai.testutil.buildTestApiService
import com.kolktech.kahawai.testutil.relaxedApplication
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ServerSetupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer
    private val application = relaxedApplication()
    private val serverConfigStore = mockk<ServerConfigStore>(relaxed = true)
    private val tokenStore = mockk<TokenStore>(relaxed = true)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        mockkObject(ApiClient)
        every { ApiClient.reset() } just Runs
        every { ApiClient.probeApiService(any()) } returns buildTestApiService(server)
    }

    @After
    fun tearDown() {
        unmockkObject(ApiClient)
        server.shutdown()
    }

    private fun vm() = ServerSetupViewModel(application, serverConfigStore, tokenStore)

    private fun enqueueBootstrap() {
        server.enqueue(MockResponse().setBody("""{"setup_required":false,"authenticated":false}"""))
    }

    @Test
    fun `init does not auto-check when no server is configured yet`() = runTest {
        every { serverConfigStore.baseUrl } returns null

        val viewModel = vm()

        assertEquals(ServerSetupState.Idle, viewModel.state.value)
        coVerify(exactly = 0) { ApiClient.resolveBaseUrl(any()) }
    }

    @Test
    fun `init auto-checks and reaches ReadyForLogin when a server is already configured`() = runTest {
        every { serverConfigStore.baseUrl } returns "http://old.local/"
        coEvery { ApiClient.resolveBaseUrl("http://old.local/") } returns "http://old.local/"
        enqueueBootstrap()

        val viewModel = vm()

        viewModel.state.test {
            var item = awaitItem()
            if (item is ServerSetupState.Loading) item = awaitItem()
            assertEquals(ServerSetupState.ReadyForLogin, item)
        }
    }

    @Test
    fun `submit with a blank address sets the enter-address error without probing`() = runTest {
        every { serverConfigStore.baseUrl } returns null
        val viewModel = vm()

        viewModel.state.test {
            assertEquals(ServerSetupState.Idle, awaitItem())
            viewModel.submit("   ")
            val error = awaitItem() as ServerSetupState.Error
            assertEquals("res:${R.string.setup_enter_address}", error.message)
        }
        coVerify(exactly = 0) { ApiClient.resolveBaseUrl(any()) }
    }

    @Test
    fun `submit with an unparseable address sets the invalid-address error without probing`() = runTest {
        every { serverConfigStore.baseUrl } returns null
        val viewModel = vm()

        viewModel.state.test {
            assertEquals(ServerSetupState.Idle, awaitItem())
            viewModel.submit("ftp://x")
            val error = awaitItem() as ServerSetupState.Error
            assertEquals("res:${R.string.setup_invalid_address}", error.message)
        }
        coVerify(exactly = 0) { ApiClient.resolveBaseUrl(any()) }
    }

    @Test
    fun `submit prefixes a schemeless host with http before probing`() = runTest {
        every { serverConfigStore.baseUrl } returns null
        val viewModel = vm()
        coEvery { ApiClient.resolveBaseUrl("http://myhub.local/") } returns "http://myhub.local/"
        enqueueBootstrap()

        viewModel.state.test {
            assertEquals(ServerSetupState.Idle, awaitItem())
            viewModel.submit("myhub.local")
            var item = awaitItem()
            if (item is ServerSetupState.Loading) item = awaitItem()
            assertEquals(ServerSetupState.ReadyForLogin, item)
        }
        coVerify(exactly = 1) { ApiClient.resolveBaseUrl("http://myhub.local/") }
    }

    @Test
    fun `submit success persists the resolved baseUrl, resets ApiClient, and reaches ReadyForLogin`() = runTest {
        every { serverConfigStore.baseUrl } returns null
        val viewModel = vm()
        coEvery { ApiClient.resolveBaseUrl(any()) } returns "http://hub.local/"
        enqueueBootstrap()

        viewModel.state.test {
            assertEquals(ServerSetupState.Idle, awaitItem())
            viewModel.submit("hub.local")
            var item = awaitItem()
            if (item is ServerSetupState.Loading) item = awaitItem()
            assertEquals(ServerSetupState.ReadyForLogin, item)
        }
        verify(exactly = 1) { serverConfigStore.baseUrl = "http://hub.local/" }
        verify(exactly = 1) { ApiClient.reset() }
    }

    @Test
    fun `submit success clears tokens when switching to a different hub`() = runTest {
        every { serverConfigStore.baseUrl } returns null
        val viewModel = vm()
        // Reflects the previously-configured hub only once submit() itself
        // reads it inside checkServer — kept null during construction so
        // init{}'s own auto-check doesn't also fire a clear.
        every { serverConfigStore.baseUrl } returns "http://previous.local/"
        coEvery { ApiClient.resolveBaseUrl(any()) } returns "http://newhub.local/"
        enqueueBootstrap()

        viewModel.state.test {
            assertEquals(ServerSetupState.Idle, awaitItem())
            viewModel.submit("newhub.local")
            var item = awaitItem()
            if (item is ServerSetupState.Loading) item = awaitItem()
            assertEquals(ServerSetupState.ReadyForLogin, item)
        }
        coVerify(exactly = 1) { tokenStore.clear() }
    }

    @Test
    fun `submit success does not clear tokens when the resolved URL matches the previous one`() = runTest {
        every { serverConfigStore.baseUrl } returns null
        val viewModel = vm()
        every { serverConfigStore.baseUrl } returns "http://hub.local"
        coEvery { ApiClient.resolveBaseUrl(any()) } returns "http://hub.local/"
        enqueueBootstrap()

        viewModel.state.test {
            assertEquals(ServerSetupState.Idle, awaitItem())
            viewModel.submit("hub.local")
            var item = awaitItem()
            if (item is ServerSetupState.Loading) item = awaitItem()
            assertEquals(ServerSetupState.ReadyForLogin, item)
        }
        coVerify(exactly = 0) { tokenStore.clear() }
    }

    @Test
    fun `submit failure surfaces the unreachable sentinel with the url and readable message`() = runTest {
        every { serverConfigStore.baseUrl } returns null
        val viewModel = vm()
        coEvery { ApiClient.resolveBaseUrl(any()) } throws Exception("timeout")

        viewModel.state.test {
            assertEquals(ServerSetupState.Idle, awaitItem())
            viewModel.submit("hub.local")
            var item = awaitItem()
            if (item is ServerSetupState.Loading) item = awaitItem()
            val error = item as ServerSetupState.Error
            assertEquals("res:${R.string.setup_unreachable}:http://hub.local/,timeout", error.message)
        }
    }
}
