package com.kolktech.kahawai.data.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.kolktech.kahawai.data.auth.ServerConfigStore
import com.kolktech.kahawai.data.auth.TokenStore
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/// Builds the two OkHttp/Retrofit pairs the app needs against the
/// user-configured hub: `plain` (no Authorization header, used for
/// login/refresh/bootstrap) and `authenticated` (attaches + refreshes
/// the bearer token, used for everything else — including artwork,
/// which Coil loads through [authenticatedOkHttpClient]). Base URL is
/// only known at runtime (self-hosted, no fixed domain), so both are
/// rebuilt lazily and invalidated by [reset] when the user changes
/// servers.
object ApiClient {
    private lateinit var tokenStore: TokenStore
    private lateinit var serverConfigStore: ServerConfigStore

    private var plainClient: OkHttpClient? = null
    private var authClient: OkHttpClient? = null
    private var plainRetrofit: Retrofit? = null
    private var authRetrofit: Retrofit? = null

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        namingStrategy = kotlinx.serialization.json.JsonNamingStrategy.SnakeCase
    }

    fun init(tokenStore: TokenStore, serverConfigStore: ServerConfigStore) {
        this.tokenStore = tokenStore
        this.serverConfigStore = serverConfigStore
    }

    fun reset() {
        plainClient = null
        authClient = null
        plainRetrofit = null
        authRetrofit = null
    }

    fun plainApiService(): ApiService = plainRetrofit().create(ApiService::class.java)

    fun apiService(): ApiService = authRetrofit().create(ApiService::class.java)

    fun authenticatedOkHttpClient(): OkHttpClient = authClient()

    fun baseUrl(): String {
        val url = serverConfigStore.baseUrl ?: error("hub server not configured yet")
        return if (url.endsWith("/")) url else "$url/"
    }

    private fun loggingInterceptor() = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private fun plainClient(): OkHttpClient {
        return plainClient ?: OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor())
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()
            .also { plainClient = it }
    }

    private fun authClient(): OkHttpClient {
        return authClient ?: OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .addInterceptor(loggingInterceptor())
            .authenticator(TokenAuthenticator(tokenStore) { plainApiService() })
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()
            .also { authClient = it }
    }

    private fun plainRetrofit(): Retrofit =
        plainRetrofit ?: buildRetrofit(plainClient()).also { plainRetrofit = it }

    private fun authRetrofit(): Retrofit =
        authRetrofit ?: buildRetrofit(authClient()).also { authRetrofit = it }

    private fun buildRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl())
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
