package com.kolktech.kahawai.testutil

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.kolktech.kahawai.data.network.AdminApiService
import com.kolktech.kahawai.data.network.apiJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit

/// Builds a real [AdminApiService] against [server], using the same
/// serialization config as [com.kolktech.kahawai.data.network.ApiClient] —
/// mirrors [buildTestApiService] for the admin-only surface.
fun buildTestAdminApiService(server: MockWebServer): AdminApiService =
    Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addConverterFactory(apiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AdminApiService::class.java)
