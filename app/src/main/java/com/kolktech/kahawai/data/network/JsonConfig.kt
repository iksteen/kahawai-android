package com.kolktech.kahawai.data.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/// Shared by [ApiClient]'s Retrofit converter and test code that needs to
/// build an identical `ApiService` against a `MockWebServer`.
@OptIn(ExperimentalSerializationApi::class)
internal val apiJson = Json {
    ignoreUnknownKeys = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}
