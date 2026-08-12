package com.kolktech.kahawai.ui.home

import app.cash.turbine.test
import com.kolktech.kahawai.data.repository.CatalogRepository
import com.kolktech.kahawai.testutil.MainDispatcherRule
import com.kolktech.kahawai.testutil.buildTestApiService
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
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

    /// [HomeViewModel.fetchHome] fires the continue-watching and libraries
    /// requests concurrently (real sockets, real threads — this is an
    /// integration-style test), so which one a plain FIFO
    /// [okhttp3.mockwebserver.QueueDispatcher] hands the next enqueued
    /// response to is a genuine race, not a fixed order. Routing by path
    /// instead makes each response deterministic regardless of arrival
    /// order.
    private fun routeBy(vararg routes: Pair<(RecordedRequest) -> Boolean, MockResponse>) =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                routes.firstOrNull { (matches, _) -> matches(request) }?.second
                    ?: MockResponse().setResponseCode(404)
        }

    private val noneInProgress = MockResponse().setBody("""{"items":[],"total":0,"limit":12,"offset":0}""")
    private val oneLibrary =
        MockResponse().setBody("""{"libraries":[{"id":"lib1","name":"Movies","media_type":"video"}]}""")

    private fun libraryItems(title: String) = MockResponse().setBody(
        """{"items":[{"id":"i1","kind":"movie","title":"$title"}],"total":1,"limit":20,"offset":0}""",
    )

    private fun homeDispatcher(libraryResponse: MockResponse) = routeBy(
        { r: RecordedRequest -> r.path?.contains("in_progress=true") == true } to noneInProgress,
        { r: RecordedRequest -> r.path == "/api/v1/libraries" } to oneLibrary,
        { r: RecordedRequest -> r.path?.contains("library=lib1") == true } to libraryResponse,
    )

    @Test
    fun `load succeeds and produces one row per non-empty library`() = runTest {
        server.dispatcher = homeDispatcher(libraryItems("Arrival"))

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
        server.dispatcher = routeBy({ _: RecordedRequest -> true } to MockResponse().setResponseCode(401).setBody("unauthorized"))

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
        server.dispatcher = routeBy({ _: RecordedRequest -> true } to MockResponse().setResponseCode(500).setBody("boom"))

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
        server.dispatcher = homeDispatcher(libraryItems("Arrival"))
        val viewModel = HomeViewModel(repo())

        viewModel.state.test {
            var item = awaitItem()
            if (item is HomeState.Loading) item = awaitItem()
            assertTrue(item is HomeState.Loaded)

            server.dispatcher = homeDispatcher(libraryItems("Arrival 2"))
            viewModel.refresh(showIndicator = true)

            val refreshing = awaitItem() as HomeState.Loaded
            assertTrue(refreshing.isRefreshing)

            val refreshed = awaitItem() as HomeState.Loaded
            assertEquals("Arrival 2", refreshed.rows[0].items[0].title)
        }
    }

    @Test
    fun `refresh failure leaves previously loaded rows untouched`() = runTest {
        server.dispatcher = homeDispatcher(libraryItems("Arrival"))
        val viewModel = HomeViewModel(repo())

        viewModel.state.test {
            var item = awaitItem()
            if (item is HomeState.Loading) item = awaitItem()
            val loaded = item as HomeState.Loaded

            server.dispatcher = routeBy({ _: RecordedRequest -> true } to MockResponse().setResponseCode(500))
            viewModel.refresh(showIndicator = true)

            val refreshing = awaitItem() as HomeState.Loaded
            assertTrue(refreshing.isRefreshing)

            val settled = awaitItem() as HomeState.Loaded
            assertEquals(loaded, settled)
            expectNoEvents()
        }
    }
}
