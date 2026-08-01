package com.kolktech.kahawai.data.auth

import android.content.Context

/// The hub base URL, e.g. "http://192.168.1.20:8420/". Self-hosted, so
/// there is no fixed domain — this is entered once at first run.
/// Not sensitive; a plain (unencrypted) preference file is fine.
class ServerConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("kahawai_server", Context.MODE_PRIVATE)

    var baseUrl: String?
        get() = prefs.getString(KEY_BASE_URL, null)
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    private companion object {
        const val KEY_BASE_URL = "base_url"
    }
}
