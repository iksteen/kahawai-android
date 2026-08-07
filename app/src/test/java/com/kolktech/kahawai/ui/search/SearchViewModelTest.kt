package com.kolktech.kahawai.ui.search

import app.cash.turbine.test
import com.kolktech.kahawai.data.repository.CatalogRepository
import com.kolktech.kahawai.testutil.MainDispatcherRule
import com.kolktech.kahawai.testutil.buildTestApiService
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/// [SearchViewModel.retry] and [SearchViewModel.refresh] both call the
/// private `search()` directly, bypassing the debounced query flow set up
/// in init{} — used here so these tests exercise the same search/state
/// logic deterministically, without depending on the 300ms debounce
/// actually elapsing under a test dispatcher.
class SearchViewModelTest {

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
    fun `retry with a blank query resets to idle without a network call`() = runTest {
        val viewModel = SearchViewModel(repo())

        viewModel.state.test {
            assertEquals(SearchState.Idle, awaitItem())
            viewModel.onQueryChange("")
            viewModel.retry()
            expectNoEvents()
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `retry with a query searches and reports results`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"items":[{"id":"i1","kind":"movie","title":"Arrival"}],"total":1,"limit":60,"offset":0}""",
            ),
        )
        val viewModel = SearchViewModel(repo())

        viewModel.state.test {
            assertEquals(SearchState.Idle, awaitItem())
            viewModel.onQueryChange("arrival")
            viewModel.retry()

            assertEquals(SearchState.Loading, awaitItem())
            val loaded = awaitItem() as SearchState.Loaded
            assertEquals("Arrival", loaded.items[0].title)
        }
    }

    @Test
    fun `retry failure with 401 reports auth error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val viewModel = SearchViewModel(repo())

        viewModel.state.test {
            assertEquals(SearchState.Idle, awaitItem())
            viewModel.onQueryChange("arrival")
            viewModel.retry()

            assertEquals(SearchState.Loading, awaitItem())
            val error = awaitItem() as SearchState.Error
            assertTrue(error.isAuthError)
        }
    }

    @Test
    fun `refresh re-runs the current query while results are already loaded`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"items":[{"id":"i1","kind":"movie","title":"Arrival"}],"total":1,"limit":60,"offset":0}""",
            ),
        )
        val viewModel = SearchViewModel(repo())

        viewModel.state.test {
            assertEquals(SearchState.Idle, awaitItem())
            viewModel.onQueryChange("arrival")
            viewModel.retry()
            assertEquals(SearchState.Loading, awaitItem())
            awaitItem() as SearchState.Loaded

            server.enqueue(
                MockResponse().setBody(
                    """{"items":[{"id":"i1","kind":"movie","title":"Arrival 2"}],"total":1,"limit":60,"offset":0}""",
                ),
            )
            viewModel.refresh()

            val refreshed = awaitItem() as SearchState.Loaded
            assertEquals("Arrival 2", refreshed.items[0].title)
        }
    }
}
