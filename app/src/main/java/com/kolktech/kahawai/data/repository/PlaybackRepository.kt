package com.kolktech.kahawai.data.repository

import com.kolktech.kahawai.data.network.ApiClient
import com.kolktech.kahawai.data.network.dto.CapabilityProfile
import com.kolktech.kahawai.data.network.dto.FontsResponse
import com.kolktech.kahawai.data.network.dto.ProgressRequest
import com.kolktech.kahawai.data.network.dto.SeekRequest
import com.kolktech.kahawai.data.network.dto.SeekResponse
import com.kolktech.kahawai.data.network.dto.StartSessionRequest
import com.kolktech.kahawai.data.network.dto.StartSessionResponse
import com.kolktech.kahawai.data.network.dto.SubtitleTrack
import com.kolktech.kahawai.data.network.dto.SubtitlesResponse

class PlaybackRepository {
    suspend fun startSession(
        itemId: String,
        profile: CapabilityProfile,
        startMs: Long = 0,
        audioTrack: Int = 0,
        videoTrack: Int = 0,
        subtitleTrack: Long? = null,
    ): StartSessionResponse = ApiClient.apiService().startSession(
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
        ApiClient.apiService().seek(sessionId, SeekRequest(positionMs = positionMs, subtitleTrack = subtitleTrack))

    /// Track list + computed delivery for this client's declared
    /// capabilities (tracks.rs `Delivery`). Always requests the richest
    /// reading (both bits true) — CapabilityProfileBuilder claims the
    /// same on the session profile, so what's listed here is what the
    /// player can actually render.
    suspend fun subtitles(itemId: String): List<SubtitleTrack> =
        ApiClient.apiService().subtitles(itemId, assRender = true, graphicsOverlay = true).subtitles

    suspend fun fonts(itemId: String): FontsResponse = ApiClient.apiService().fonts(itemId)

    suspend fun reportProgress(sessionId: String, positionMs: Long) {
        ApiClient.apiService().progress(sessionId, ProgressRequest(positionMs = positionMs))
    }

    suspend fun endSession(sessionId: String) {
        ApiClient.apiService().endSession(sessionId)
    }
}
