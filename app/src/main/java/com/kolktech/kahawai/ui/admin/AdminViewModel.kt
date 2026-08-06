package com.kolktech.kahawai.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.kolktech.kahawai.data.network.dto.AdminLibrary
import com.kolktech.kahawai.data.network.dto.AdminSession
import com.kolktech.kahawai.data.network.dto.CollectionInfo
import com.kolktech.kahawai.data.network.dto.EnrichStatusResponse
import com.kolktech.kahawai.data.network.dto.PendingEnrollment
import com.kolktech.kahawai.data.network.dto.ProvidersResponse
import com.kolktech.kahawai.data.network.dto.Satellite
import com.kolktech.kahawai.data.network.isAuthError
import com.kolktech.kahawai.data.network.readableMessage
import com.kolktech.kahawai.data.repository.AdminRepository

data class AdminData(
    val pending: List<PendingEnrollment>,
    val satellites: List<Satellite>,
    val sessions: List<AdminSession>,
    val libraries: List<AdminLibrary>,
    val collections: List<CollectionInfo>,
    val providers: ProvidersResponse,
    val enrich: EnrichStatusResponse,
)

sealed interface AdminState {
    data object Loading : AdminState
    data class Error(val message: String, val isAuthError: Boolean) : AdminState
    data class Loaded(val data: AdminData) : AdminState
}

/// Backs [AdminScreen] — full parity with `~/code/kahawai/web/src/views/Admin.tsx`:
/// metadata providers, pending enrollments, satellites, libraries, active
/// sessions. Polling only (no SSE `/api/v1/events` channel — see the plan
/// this was built from), same 15s interval the web client uses as its own
/// fallback.
class AdminViewModel(private val repo: AdminRepository = AdminRepository()) : ViewModel() {
    private val _state = MutableStateFlow<AdminState>(AdminState.Loading)
    val state: StateFlow<AdminState> = _state

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            try {
                _state.value = AdminState.Loaded(fetchAll())
            } catch (e: Exception) {
                _state.value = AdminState.Error(e.readableMessage(), e.isAuthError())
            }
        }
    }

    fun clearNotice() {
        _notice.value = null
    }

    fun approve(code: String) = runAction {
        val id = repo.approve(code)
        notice("Approved: $id")
    }

    fun deleteSatellite(id: String) = runAction {
        repo.deleteSatellite(id)
        notice("Deleted $id: certificate revoked, collections removed.")
    }

    fun setSatelliteDisabled(id: String, disabled: Boolean) = runAction {
        repo.setSatelliteDisabled(id, disabled)
    }

    fun createLibrary(name: String, mediaType: String) = runAction { repo.createLibrary(name, mediaType) }

    fun deleteLibrary(id: String) = runAction { repo.deleteLibrary(id) }

    fun attachCollection(libraryId: String, moduleId: String, collectionId: String) = runAction {
        repo.attachCollection(libraryId, moduleId, collectionId)
    }

    fun detachCollection(libraryId: String, moduleId: String, collectionId: String) = runAction {
        repo.detachCollection(libraryId, moduleId, collectionId)
    }

    fun refreshLibrary(id: String) = runAction {
        val r = repo.refreshLibrary(id)
        notice("Refresh requested: ${r.asked} collection(s)" + if (r.offline > 0) ", ${r.offline} offline" else "")
    }

    fun setTmdbKey(key: String) = runAction {
        repo.setTmdbKey(key)
        notice("TMDB key saved — enrichment started")
    }

    fun setTvdbKey(key: String, pin: String?) = runAction {
        repo.setTvdbKey(key, pin)
        notice("TVDB key saved — enrichment started")
    }

    fun setAnidb(username: String, password: String, udpApiKey: String?) = runAction {
        val r = repo.setAnidb(username, password, udpApiKey)
        notice(
            if (r.verified) "AniDB account verified — enrichment started"
            else "AniDB saved but login failed: ${r.error ?: "unknown"}",
        )
    }

    fun enrichRun() = runAction { repo.enrichRun() }

    fun setChain(mediaType: String, order: List<String>) = runAction {
        repo.setChain(mediaType, order)
        notice("$mediaType: provider order applied — metadata re-merged")
    }

    fun endSession(id: String) = runAction { repo.endSession(id) }

    private fun notice(message: String) {
        _notice.value = message
    }

    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                reload()
            } catch (e: Exception) {
                _notice.value = "Error: ${e.readableMessage()}"
            }
        }
    }

    private suspend fun fetchAll(): AdminData = coroutineScope {
        val pending = async { repo.pendingEnrollments() }
        val satellites = async { repo.satellites() }
        val sessions = async { repo.sessions() }
        val libraries = async { repo.libraries() }
        val collections = async { repo.collections() }
        val providers = async { repo.providers() }
        val enrich = async { repo.enrichStatus() }
        AdminData(
            pending = pending.await(),
            satellites = satellites.await(),
            sessions = sessions.await(),
            libraries = libraries.await(),
            collections = collections.await(),
            providers = providers.await(),
            enrich = enrich.await(),
        )
    }
}
