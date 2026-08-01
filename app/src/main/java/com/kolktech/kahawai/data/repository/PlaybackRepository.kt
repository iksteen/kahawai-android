package com.kolktech.kahawai.data.repository

import com.kolktech.kahawai.data.network.ApiClient
import com.kolktech.kahawai.data.network.dto.CapabilityProfile
import com.kolktech.kahawai.data.network.dto.ProgressRequest
import com.kolktech.kahawai.data.network.dto.SeekRequest
import com.kolktech.kahawai.data.network.dto.SeekResponse
import com.kolktech.kahawai.data.network.dto.StartSessionRequest
import com.kolktech.kahawai.data.network.dto.StartSessionResponse

class PlaybackRepository {
    suspend fun startSession(
        itemId: String,
        profile: CapabilityProfile,
        startMs: Long = 0,
        audioTrack: Int = 0,
        videoTrack: Int = 0,
    ): StartSessionResponse = ApiClient.apiService().startSession(
        StartSessionRequest(itemId = itemId, profile = profile, startMs = startMs, audioTrack = audioTrack, videoTrack = videoTrack),
    )

    suspend fun seek(sessionId: String, positionMs: Long): SeekResponse =
        ApiClient.apiService().seek(sessionId, SeekRequest(positionMs = positionMs))

    suspend fun reportProgress(sessionId: String, positionMs: Long) {
        ApiClient.apiService().progress(sessionId, ProgressRequest(positionMs = positionMs))
    }

    suspend fun endSession(sessionId: String) {
        ApiClient.apiService().endSession(sessionId)
    }
}
