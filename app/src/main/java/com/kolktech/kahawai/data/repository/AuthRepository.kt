package com.kolktech.kahawai.data.repository

import com.kolktech.kahawai.data.network.ApiClient
import com.kolktech.kahawai.data.network.dto.LogoutRequest

/// Thin wrapper over [ApiClient.apiService]'s auth routes, same style as
/// [CatalogRepository] — currently just server-side logout, which needs
/// the authenticated (bearer-attaching) client rather than [ApiClient.plainApiService].
class AuthRepository {
    suspend fun logout(refreshToken: String) =
        ApiClient.apiService().logout(LogoutRequest(refreshToken = refreshToken))
}
