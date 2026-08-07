package com.kolktech.kahawai.testutil

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.kolktech.kahawai.data.network.ApiService
import com.kolktech.kahawai.data.network.apiJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit

/// Builds a real [ApiService] against [server], using the same
/// serialization config as [com.kolktech.kahawai.data.network.ApiClient]
/// so fixture JSON round-trips identically to production.
fun buildTestApiService(server: MockWebServer): ApiService =
    Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addConverterFactory(apiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ApiService::class.java)
