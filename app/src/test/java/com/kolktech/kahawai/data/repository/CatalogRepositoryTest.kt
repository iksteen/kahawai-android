package com.kolktech.kahawai.data.repository

import com.kolktech.kahawai.data.network.ApiClient
import com.kolktech.kahawai.data.network.dto.CapabilityProfile
import com.kolktech.kahawai.data.network.dto.TargetDuration
import com.kolktech.kahawai.testutil.buildTestApiService
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

class CatalogRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: CatalogRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = CatalogRepository(buildTestApiService(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `libraries deserializes snake_case response and hits the right path`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"libraries":[{"id":"1","name":"Movies","media_type":"video"}]}""",
            ),
        )

        val result = repository.libraries()

        assertEquals(1, result.size)
        assertEquals("Movies", result[0].name)
        assertEquals("video", result[0].mediaType)
        assertEquals("/api/v1/libraries", server.takeRequest().path)
    }

    @Test
    fun `items sends query params and deserializes response`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"items":[{"id":"1","kind":"movie","title":"Arrival"}],"total":1,"limit":20,"offset":0}""",
            ),
        )

        val result = repository.items(library = "lib1", q = "arrival", sort = "title", limit = 20, offset = 0)

        assertEquals(1, result.total)
        assertEquals("Arrival", result.items[0].title)
        val recorded = server.takeRequest()
        assertEquals("/api/v1/items?library=lib1&q=arrival&sort=title&limit=20&offset=0", recorded.path)
    }

    @Test
    fun `item deserializes nested snake_case fields`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "id":"ep1","kind":"episode","title":"Pilot","season":1,"episode":1,
                  "parent_id":"show1","parent_title":"Show","art_version":42,
                  "resume_position_ms":120000,"resume_duration_ms":2400000,
                  "played":false,"play_count":0
                }
                """.trimIndent(),
            ),
        )

        val result = repository.item("ep1")

        assertEquals("/api/v1/items/ep1", server.takeRequest().path)
        assertEquals(1, result.season)
        assertEquals(120000L, result.resumePositionMs)
        assertEquals(42L, result.artVersion)
        assertEquals("show1", result.parentId)
    }

    @Test
    fun `queryItem sends QUERY method with request body and parses negotiated`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "id":"ep1","kind":"episode","title":"Pilot",
                  "negotiated":{"mode":"direct","cost":"free","subtitles":[]}
                }
                """.trimIndent(),
            ),
        )
        val profile = CapabilityProfile(
            containers = listOf("mp4"),
            video = emptyList(),
            audio = listOf("aac"),
            maxAudioChannels = 2,
            hdr = false,
            assRender = false,
            graphicsOverlay = false,
            targetDuration = TargetDuration.Accurate,
        )

        val result = repository.queryItem("ep1", profile, audioTrack = 1, videoTrack = 0, subtitleTrack = 7L)

        val recorded = server.takeRequest()
        assertEquals("QUERY", recorded.method)
        assertEquals("/api/v1/items/ep1", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"audio_track\":1"))
        assertTrue(body.contains("\"subtitle_track\":7"))
        assertEquals("direct", result.negotiated?.mode)
    }

    @Test
    fun `children deserializes list`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"children":[{"id":"ep1","kind":"episode","title":"Pilot"},{"id":"ep2","kind":"episode","title":"Two"}]}""",
            ),
        )

        val result = repository.children("show1")

        assertEquals("/api/v1/items/show1/children", server.takeRequest().path)
        assertEquals(listOf("ep1", "ep2"), result.map { it.id })
    }

    @Test
    fun `item propagates HttpException on error response`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))

        val exception = assertThrows(HttpException::class.java) {
            kotlinx.coroutines.runBlocking { repository.item("missing") }
        }
        assertEquals(404, exception.code())
    }

    @Test
    fun `artworkUrl composes query string from size and version`() {
        mockkObject(ApiClient)
        every { ApiClient.baseUrl() } returns "http://hub.local/"
        try {
            assertEquals(
                "http://hub.local/api/v1/items/i1/artwork?size=thumb&v=42",
                repository.artworkUrl("i1", version = 42L, size = "thumb"),
            )
            assertEquals(
                "http://hub.local/api/v1/items/i1/artwork?size=thumb",
                repository.artworkUrl("i1", version = null, size = "thumb"),
            )
            assertEquals(
                "http://hub.local/api/v1/items/i1/artwork?v=42",
                repository.artworkUrl("i1", version = 42L, size = null),
            )
            assertEquals(
                "http://hub.local/api/v1/items/i1/artwork",
                repository.artworkUrl("i1", version = null, size = null),
            )
        } finally {
            unmockkObject(ApiClient)
        }
    }
}
