package com.kolktech.kahawai.ui.home

import app.cash.turbine.test
import com.kolktech.kahawai.data.repository.CatalogRepository
import com.kolktech.kahawai.testutil.MainDispatcherRule
import com.kolktech.kahawai.testutil.buildTestApiService
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

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun repo() = CatalogRepository({ buildTestApiService(server) })

    @Test
    fun `load succeeds and produces one row per non-empty library`() = runTest {
        server.enqueue(MockResponse().setBody("""{"libraries":[{"id":"lib1","name":"Movies","media_type":"video"}]}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"items":[{"id":"i1","kind":"movie","title":"Arrival"}],"total":1,"limit":20,"offset":0}""",
            ),
        )

        val viewModel = HomeViewModel(repo())

        viewModel.state.test {
            var item = awaitItem()
            if (item is HomeState.Loading) item = awaitItem()
            val loaded = item as HomeState.Loaded
            assertEquals(1, loaded.rows.size)
            assertEquals("Arrival", loaded.rows[0].items[0].title)
        }
    }

    @Test
    fun `load failure with 401 marks state as auth error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))

        val viewModel = HomeViewModel(repo())

        viewModel.state.test {
            var item = awaitItem()
            if (item is HomeState.Loading) item = awaitItem()
            val error = item as HomeState.Error
            assertTrue(error.isAuthError)
        }
    }

    @Test
    fun `load failure with a generic error is not an auth error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val viewModel = HomeViewModel(repo())

        viewModel.state.test {
            var item = awaitItem()
            if (item is HomeState.Loading) item = awaitItem()
            val error = item as HomeState.Error
            assertFalse(error.isAuthError)
        }
    }

    @Test
    fun `refresh re-fetches rows while already loaded`() = runTest {
        server.enqueue(MockResponse().setBody("""{"libraries":[{"id":"lib1","name":"Movies","media_type":"video"}]}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"items":[{"id":"i1","kind":"movie","title":"Arrival"}],"total":1,"limit":20,"offset":0}""",
            ),
        )
        val viewModel = HomeViewModel(repo())

        viewModel.state.test {
            var item = awaitItem()
            if (item is HomeState.Loading) item = awaitItem()
            assertTrue(item is HomeState.Loaded)

            server.enqueue(MockResponse().setBody("""{"libraries":[{"id":"lib1","name":"Movies","media_type":"video"}]}"""))
            server.enqueue(
                MockResponse().setBody(
                    """{"items":[{"id":"i1","kind":"movie","title":"Arrival 2"}],"total":1,"limit":20,"offset":0}""",
                ),
            )
            viewModel.refresh()

            val refreshing = awaitItem() as HomeState.Loaded
            assertTrue(refreshing.isRefreshing)

            val refreshed = awaitItem() as HomeState.Loaded
            assertEquals("Arrival 2", refreshed.rows[0].items[0].title)
        }
    }

    @Test
    fun `refresh failure leaves previously loaded rows untouched`() = runTest {
        server.enqueue(MockResponse().setBody("""{"libraries":[{"id":"lib1","name":"Movies","media_type":"video"}]}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"items":[{"id":"i1","kind":"movie","title":"Arrival"}],"total":1,"limit":20,"offset":0}""",
            ),
        )
        val viewModel = HomeViewModel(repo())

        viewModel.state.test {
            var item = awaitItem()
            if (item is HomeState.Loading) item = awaitItem()
            val loaded = item as HomeState.Loaded

            server.enqueue(MockResponse().setResponseCode(500))
            viewModel.refresh()

            val refreshing = awaitItem() as HomeState.Loaded
            assertTrue(refreshing.isRefreshing)

            val settled = awaitItem() as HomeState.Loaded
            assertEquals(loaded, settled)
            expectNoEvents()
        }
    }
}
