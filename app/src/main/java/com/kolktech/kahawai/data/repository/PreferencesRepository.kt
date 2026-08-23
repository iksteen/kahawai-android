package com.kolktech.kahawai.data.repository

import com.kolktech.kahawai.data.network.ApiClient
import com.kolktech.kahawai.data.network.ApiService
import com.kolktech.kahawai.data.network.dto.Pref
import com.kolktech.kahawai.data.network.dto.PutPrefRequest

/// The account's preferences (HUB-33), the ones the hub stores rather than
/// this device. Same lazy [api] as [CatalogRepository], for the same reason.
class PreferencesRepository(apiProvider: () -> ApiService = { ApiClient.apiService() }) {
    private val api: ApiService by lazy(apiProvider)

    suspend fun all(): List<Pref> = api.prefs().prefs

    /// [scope] is an item id for a title's own memory and `""` for the
    /// account's own settings; an empty [value] deletes the row.
    suspend fun put(scope: String, key: String, value: String) {
        api.putPref(PutPrefRequest(scope = scope, key = key, value = value))
    }
}
