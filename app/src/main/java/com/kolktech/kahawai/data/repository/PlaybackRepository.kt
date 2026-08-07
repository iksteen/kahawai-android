package com.kolktech.kahawai.data.repository

import com.kolktech.kahawai.data.network.ApiClient
import com.kolktech.kahawai.data.network.ApiService
import com.kolktech.kahawai.data.network.dto.CapabilityProfile
import com.kolktech.kahawai.data.network.dto.FontsResponse
import com.kolktech.kahawai.data.network.dto.ItemQueryRequest
import com.kolktech.kahawai.data.network.dto.ProgressRequest
import com.kolktech.kahawai.data.network.dto.SeekRequest
import com.kolktech.kahawai.data.network.dto.SeekResponse
import com.kolktech.kahawai.data.network.dto.StartSessionRequest
import com.kolktech.kahawai.data.network.dto.StartSessionResponse
import com.kolktech.kahawai.data.network.dto.SubtitleTrack

class PlaybackRepository(private val api: ApiService = ApiClient.apiService()) {
    suspend fun startSession(
        itemId: String,
        profile: CapabilityProfile,
        startMs: Long = 0,
        audioTrack: Int = 0,
        videoTrack: Int = 0,
        subtitleTrack: Long? = null,
    ): StartSessionResponse = api.startSession(
        StartSessionRequest(
            itemId = itemId,
            profile = profile,
            startMs = startMs,
            audioTrack = audioTrack,
            videoTrack = videoTrack,
            subtitleTrack = subtitleTrack,
        ),
    )

    suspend fun seek(sessionId: String, positionMs: Long, subtitleTrack: Long? = null): SeekResponse =
        api.seek(sessionId, SeekRequest(positionMs = positionMs, subtitleTrack = subtitleTrack))

    /// Track list + computed delivery for this client's declared
    /// [profile] (tracks.rs `Delivery`), read off the same QUERY that
    /// negotiates the source `startSession` will start — the
    /// once-separate `GET /items/{id}/subtitles` route was deleted
    /// because it could resolve a different source than the session did
    /// (kahawai commit 5147059).
    suspend fun subtitles(itemId: String, profile: CapabilityProfile): List<SubtitleTrack> =
        api.itemQuery(itemId, ItemQueryRequest(profile = profile)).negotiated?.subtitles
            ?: emptyList()

    suspend fun fonts(itemId: String): FontsResponse = api.fonts(itemId)

    suspend fun reportProgress(sessionId: String, positionMs: Long) {
        api.progress(sessionId, ProgressRequest(positionMs = positionMs))
    }

    suspend fun endSession(sessionId: String) {
        api.endSession(sessionId)
    }
}
