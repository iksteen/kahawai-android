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

    /// Every test that reaches [ServerSettingsState.Loaded] needs two
    /// enqueued responses: `load()` fetches prefs, then the OpenSubtitles
    /// account's `configured` flag (kahawai commit 7835630).
    private fun enqueueLoad(prefsBody: String = """{"prefs":[]}""", configured: Boolean = false) {
        server.enqueue(MockResponse().setBody(prefsBody))
        server.enqueue(MockResponse().setBody("""{"configured":$configured}"""))
    }

    @Test
    fun `load succeeds and keeps only global-scope prefs`() = runTest {
        enqueueLoad(
            prefsBody = """{"prefs":[{"scope":"","key":"a","value":"1"},{"scope":"lib1","key":"b","value":"2"}]}""",
        )

        val viewModel = ServerSettingsViewModel()

        viewModel.state.test {
            var item = awaitItem()
            if (item is ServerSettingsState.Loading) item = awaitItem()
            val loaded = item as ServerSettingsState.Loaded
            assertEquals(mapOf("a" to "1"), loaded.values)
            assertFalse(loaded.openSubtitlesConfigured)
        }
    }

    @Test
    fun `load reflects an attached OpenSubtitles account`() = runTest {
        enqueueLoad(configured = true)

        val viewModel = ServerSettingsViewModel()

        viewModel.state.test {
            var item = awaitItem()
            if (item is ServerSettingsState.Loading) item = awaitItem()
            val loaded = item as ServerSettingsState.Loaded
            assertTrue(loaded.openSubtitlesConfigured)
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
    fun `load failure when the OpenSubtitles account check fails surfaces an error`() = runTest {
        server.enqueue(MockResponse().setBody("""{"prefs":[]}"""))
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val viewModel = ServerSettingsViewModel()

        viewModel.state.test {
            var item = awaitItem()
            if (item is ServerSettingsState.Loading) item = awaitItem()
            assertTrue(item is ServerSettingsState.Error)
        }
    }

    @Test
    fun `setPref success updates the value optimistically and invokes onSaved`() = runTest {
        enqueueLoad()
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
        enqueueLoad(prefsBody = """{"prefs":[{"scope":"","key":"k","value":"v"}]}""")
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
    fun `setPref preserves the OpenSubtitles configured flag`() = runTest {
        enqueueLoad(configured = true)
        val viewModel = ServerSettingsViewModel()

        viewModel.state.test {
            var item = awaitItem()
            if (item is ServerSettingsState.Loading) item = awaitItem()
            assertTrue((item as ServerSettingsState.Loaded).openSubtitlesConfigured)

            server.enqueue(MockResponse().setBody("""{"ok":true}"""))
            viewModel.setPref("k", "v")

            val next = awaitItem() as ServerSettingsState.Loaded
            assertTrue(next.openSubtitlesConfigured)
        }
    }

    @Test
    fun `setPref failure surfaces an error state and does not call onSaved`() = runTest {
        enqueueLoad()
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
    fun `saveOpenSubtitles success marks the account configured and invokes onSaved`() = runTest {
        enqueueLoad()
        val viewModel = ServerSettingsViewModel()

        viewModel.state.test {
            var item = awaitItem()
            if (item is ServerSettingsState.Loading) item = awaitItem()
            assertFalse((item as ServerSettingsState.Loaded).openSubtitlesConfigured)

            server.enqueue(MockResponse().setBody("""{"ok":true}"""))
            var saved = false
            viewModel.saveOpenSubtitles("user", "pass") { saved = true }

            val next = awaitItem() as ServerSettingsState.Loaded
            assertTrue(next.openSubtitlesConfigured)
            assertTrue(saved)
        }
    }

    @Test
    fun `saveOpenSubtitles failure surfaces an error state`() = runTest {
        enqueueLoad()
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

    @Test
    fun `disconnectOpenSubtitles clears the configured flag and invokes onSaved`() = runTest {
        enqueueLoad(configured = true)
        val viewModel = ServerSettingsViewModel()

        viewModel.state.test {
            var item = awaitItem()
            if (item is ServerSettingsState.Loading) item = awaitItem()
            assertTrue((item as ServerSettingsState.Loaded).openSubtitlesConfigured)

            server.enqueue(MockResponse().setBody("""{"ok":true}"""))
            var saved = false
            viewModel.disconnectOpenSubtitles { saved = true }

            val next = awaitItem() as ServerSettingsState.Loaded
            assertFalse(next.openSubtitlesConfigured)
            assertTrue(saved)
        }
    }

    @Test
    fun `disconnectOpenSubtitles failure surfaces an error state`() = runTest {
        enqueueLoad(configured = true)
        val viewModel = ServerSettingsViewModel()

        viewModel.state.test {
            var item = awaitItem()
            if (item is ServerSettingsState.Loading) item = awaitItem()
            assertTrue(item is ServerSettingsState.Loaded)

            server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
            viewModel.disconnectOpenSubtitles()

            val next = awaitItem()
            assertTrue(next is ServerSettingsState.Error)
        }
    }
}
