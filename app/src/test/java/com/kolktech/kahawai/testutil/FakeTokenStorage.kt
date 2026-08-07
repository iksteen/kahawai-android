package com.kolktech.kahawai.testutil

import com.kolktech.kahawai.data.auth.TokenStorage
import com.kolktech.kahawai.data.network.dto.TokenPair

class FakeTokenStorage(
    accessToken: String? = null,
    refreshToken: String? = null,
) : TokenStorage {
    override var accessToken: String? = accessToken
        private set
    override var refreshToken: String? = refreshToken
        private set

    var saveCallCount = 0
        private set
    var clearCallCount = 0
        private set

    override suspend fun save(tokens: TokenPair) {
        accessToken = tokens.accessToken
        refreshToken = tokens.refreshToken
        saveCallCount++
    }

    override suspend fun clear() {
        accessToken = null
        refreshToken = null
        clearCallCount++
    }
}
