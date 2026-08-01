package com.kolktech.kahawai.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class BootstrapResponse(
    val setupRequired: Boolean,
    val authenticated: Boolean,
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

/// Mirrors `TokenPair` in crates/kahawai-hub/src/auth.rs.
@Serializable
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)
