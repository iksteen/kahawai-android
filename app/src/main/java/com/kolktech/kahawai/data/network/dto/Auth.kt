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

/// First-time hub setup (`POST /api/v1/setup`, `crates/kahawai-hub/src/auth.rs:191-201`):
/// the one-time admin-bootstrap token the hub prints on its console,
/// paired with the admin account being created. Unlike [LoginRequest]
/// this only works once — the hub 409s once an admin account exists.
@Serializable
data class SetupRequest(
    val token: String,
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
