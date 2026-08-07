package com.kolktech.kahawai.data.auth

import com.kolktech.kahawai.data.network.dto.TokenPair

/// Read/write surface [AuthInterceptor]/[TokenAuthenticator] need from a
/// token store — split out from [TokenStore] so tests can substitute an
/// in-memory fake instead of a real Context/Tink/DataStore-backed store.
interface TokenStorage {
    val accessToken: String?
    val refreshToken: String?
    suspend fun save(tokens: TokenPair)
    suspend fun clear()
}
