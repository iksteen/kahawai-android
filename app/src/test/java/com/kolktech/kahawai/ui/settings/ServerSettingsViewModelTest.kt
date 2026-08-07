package com.kolktech.kahawai.ui.settings

import app.cash.turbine.test
import com.kolktech.kahawai.data.network.ApiClient
import com.kolktech.kahawai.testutil.MainDispatcherRule
import com.kolktech.kahawai.testutil.buildTestApiService
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ServerSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        mockkObject(ApiClient)
        every { ApiClient.apiService() } returns buildTestApiService(server)
    }

    @After
    fun tearDown() {
        unmockkObject(ApiClient)
        server.shutdown()
    }

    @Test
    fun `load succeeds and keeps only global-scope prefs`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"prefs":[{"scope":"","key":"a","value":"1"},{"scope":"lib1","key":"b","value":"2"}]}""",
            ),
        )

        val viewModel = ServerSettingsViewModel()

        viewModel.state.test {
            var item = awaitItem()
            if (item is ServerSettingsState.Loading) item = awaitItem()
            val loaded = item as ServerSettingsState.Loaded
            assertEquals(mapOf("a" to "1"), loaded.values)
        }
    }

    @Test
    fun `load failure with 401 marks state as auth error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))

        val viewModel = ServerSettingsViewModel()

        viewModel.state.test {
            var item = awaitItem()
            if (item is ServerSettingsState.Loading) item = awaitItem()
            val error = item as ServerSettingsState.Error
            assertTrue(error.isAuthError)
        }
    }

    @Test
    fun `load failure with a generic error is not an auth error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val viewModel = ServerSettingsViewModel()

        viewModel.state.test {
            var item = awaitItem()
            if (item is ServerSettingsState.Loading) item = awaitItem()
            val error = item as ServerSettingsState.Error
            assertFalse(error.isAuthError)
        }
    }

    @Test
    fun `setPref success updates the value optimistically and invokes onSaved`() = runTest {
        server.enqueue(MockResponse().setBody("""{"prefs":[]}"""))
        val viewModel = ServerSettingsViewModel()

        viewModel.state.test {
            var item = awaitItem()
            if (item is ServerSettingsState.Loading) item = awaitItem()
            assertTrue(item is ServerSettingsState.Loaded)

            server.enqueue(MockResponse().setBody("""{"ok":true}"""))
            var saved = false
            viewModel.setPref("k", "v") { saved = true }

            val next = awaitItem() as ServerSettingsState.Loaded
            assertEquals("v", next.values["k"])
            assertTrue(saved)
        }
    }

    @Test
    fun `setPref with an empty value removes the key from state`() = runTest {
        server.enqueue(MockResponse().setBody("""{"prefs":[{"scope":"","key":"k","value":"v"}]}"""))
        val viewModel = ServerSettingsViewModel()

        viewModel.state.test {
            var item = awaitItem()
            if (item is ServerSettingsState.Loading) item = awaitItem()
            val loaded = item as ServerSettingsState.Loaded
            assertEquals("v", loaded.values["k"])

            server.enqueue(MockResponse().setBody("""{"ok":true}"""))
            viewModel.setPref("k", "")

            val next = awaitItem() as ServerSettingsState.Loaded
            assertFalse(next.values.containsKey("k"))
        }
    }

    @Test
    fun `setPref failure surfaces an error state and does not call onSaved`() = runTest {
        server.enqueue(MockResponse().setBody("""{"prefs":[]}"""))
        val viewModel = ServerSettingsViewModel()

        viewModel.state.test {
            var item = awaitItem()
            if (item is ServerSettingsState.Loading) item = awaitItem()
            assertTrue(item is ServerSettingsState.Loaded)

            server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
            var saved = false
            viewModel.setPref("k", "v") { saved = true }

            val next = awaitItem()
            assertTrue(next is ServerSettingsState.Error)
            assertFalse(saved)
        }
    }

    @Test
    fun `saveOpenSubtitles success sets both keys and invokes onSaved`() = runTest {
        server.enqueue(MockResponse().setBody("""{"prefs":[]}"""))
        val viewModel = ServerSettingsViewModel()

        viewModel.state.test {
            var item = awaitItem()
            if (item is ServerSettingsState.Loading) item = awaitItem()
            assertTrue(item is ServerSettingsState.Loaded)

            server.enqueue(MockResponse().setBody("""{"ok":true}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true}"""))
            var saved = false
            viewModel.saveOpenSubtitles("user", "pass") { saved = true }

            val next = awaitItem() as ServerSettingsState.Loaded
            assertEquals("user", next.values["opensubtitles.username"])
            assertEquals("pass", next.values["opensubtitles.password"])
            assertTrue(saved)
        }
    }

    @Test
    fun `saveOpenSubtitles with blank username or password removes that key instead of setting it`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"prefs":[{"scope":"","key":"opensubtitles.username","value":"olduser"},{"scope":"","key":"opensubtitles.password","value":"oldpass"}]}""",
            ),
        )
        val viewModel = ServerSettingsViewModel()

        viewModel.state.test {
            var item = awaitItem()
            if (item is ServerSettingsState.Loading) item = awaitItem()
            assertTrue(item is ServerSettingsState.Loaded)

            server.enqueue(MockResponse().setBody("""{"ok":true}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true}"""))
            viewModel.saveOpenSubtitles("", "")

            val next = awaitItem() as ServerSettingsState.Loaded
            assertFalse(next.values.containsKey("opensubtitles.username"))
            assertFalse(next.values.containsKey("opensubtitles.password"))
        }
    }

    @Test
    fun `saveOpenSubtitles failure surfaces an error state`() = runTest {
        server.enqueue(MockResponse().setBody("""{"prefs":[]}"""))
        val viewModel = ServerSettingsViewModel()

        viewModel.state.test {
            var item = awaitItem()
            if (item is ServerSettingsState.Loading) item = awaitItem()
            assertTrue(item is ServerSettingsState.Loaded)

            server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
            viewModel.saveOpenSubtitles("user", "pass")

            val next = awaitItem()
            assertTrue(next is ServerSettingsState.Error)
        }
    }
}
