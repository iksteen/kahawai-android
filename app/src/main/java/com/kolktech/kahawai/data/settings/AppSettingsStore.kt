package com.kolktech.kahawai.data.settings

import android.content.Context

/// Local, device-only display preferences. Distinct from per-user prefs
/// stored on the hub (`/api/v1/prefs`) — these never leave the device.
class AppSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("kahawai_app_settings", Context.MODE_PRIVATE)

    var reserveNotchSpace: Boolean
        get() = prefs.getBoolean(KEY_RESERVE_NOTCH_SPACE, true)
        set(value) = prefs.edit().putBoolean(KEY_RESERVE_NOTCH_SPACE, value).apply()

    private companion object {
        const val KEY_RESERVE_NOTCH_SPACE = "reserve_notch_space"
    }
}
