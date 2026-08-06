package com.kolktech.kahawai.data.auth

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class JwtPayload(val admin: Boolean = false)

private val json = Json { ignoreUnknownKeys = true }

/// Decodes the unsigned JWT payload segment purely to read the `admin`
/// claim (mirrors `Claims` in crates/kahawai-hub/src/auth.rs:24-31) for UI
/// gating — e.g. whether to show the hamburger menu's Admin entry. Never
/// trusted for authorization: the hub re-checks `admin` server-side on
/// every `/admin/v1/*` route regardless of what this reads, and a forged
/// or corrupt token here just hides/shows a menu item, nothing more.
fun decodeAdminClaim(accessToken: String): Boolean {
    val parts = accessToken.split(".")
    if (parts.size != 3) return false
    return try {
        val payload = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        json.decodeFromString(JwtPayload.serializer(), String(payload)).admin
    } catch (e: Exception) {
        false
    }
}
