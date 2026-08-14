package com.kolktech.kahawai.data.network.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class BootstrapResponse(
    val setupRequired: Boolean,
    val authenticated: Boolean,
)

/// `client` picks the hub's auth transport (`crates/kahawai-hub/src/api.rs`
/// `AuthClient`): `"api"` returns bearer tokens and sets no cookies, which
/// is what every non-browser client — this app included — must send on
/// every login/refresh/logout request. `"browser"` is the embedded web UI's
/// cookie-based mode and never applies here. It's a required field on the
/// hub side (no `#[serde(default)]`), so `@EncodeDefault` is load-bearing:
/// [apiJson] leaves `encodeDefaults` at its false default, and without the
/// annotation this property — always equal to its default — would be
/// dropped from the request body entirely.
private const val AUTH_CLIENT_API = "api"

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    @EncodeDefault val client: String = AUTH_CLIENT_API,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RefreshRequest(
    val refreshToken: String,
    @EncodeDefault val client: String = AUTH_CLIENT_API,
)

/// `POST /api/v1/auth/logout` (`crates/kahawai-hub/src/api.rs`): revokes
/// just this login's refresh family. Requires the access bearer (attached
/// by [com.kolktech.kahawai.data.network.AuthInterceptor]) plus the current
/// refresh token in the body.
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LogoutRequest(
    val refreshToken: String,
    @EncodeDefault val client: String = AUTH_CLIENT_API,
)

/// Mirrors `TokenPair` in crates/kahawai-hub/src/auth.rs.
@Serializable
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)
