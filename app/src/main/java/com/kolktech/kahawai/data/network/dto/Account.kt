package com.kolktech.kahawai.data.network.dto

import kotlinx.serialization.Serializable

/// `GET /api/v1/account/opensubtitles` (kahawai commit 7835630, "Seal
/// viewer OpenSubtitles accounts"). The hub's credential store never reads
/// secrets back out to clients, so this is the whole answer: whether an
/// account is attached, not which one.
@Serializable
data class ProviderConfiguration(val configured: Boolean)

/// `POST /api/v1/account/opensubtitles` body. Either field empty is
/// rejected with 400 server-side.
@Serializable
data class SetOpenSubtitlesAccountRequest(
    val username: String,
    val password: String,
)

@Serializable
data class OkResponse(val ok: Boolean)
