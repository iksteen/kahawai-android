package com.kolktech.kahawai.data.network.dto

import kotlinx.serialization.Serializable

/// Per-user preference row (HUB-33). `scope` is a library id, or `""` for
/// user-global keys — this client only reads/writes global ones. Mirrors
/// the row shape from `GET /api/v1/prefs` (crates/kahawai-hub/src/api.rs:1362-1382).
@Serializable
data class Pref(
    val scope: String,
    val key: String,
    val value: String,
)

@Serializable
data class PrefsResponse(val prefs: List<Pref>)

/// `PUT /api/v1/prefs` body (api.rs:1384-1391). An empty [value] deletes
/// the preference server-side (api.rs:1401-1408).
@Serializable
data class PutPrefRequest(
    val scope: String = "",
    val key: String,
    val value: String,
)

@Serializable
data class PutPrefResponse(val ok: Boolean)
