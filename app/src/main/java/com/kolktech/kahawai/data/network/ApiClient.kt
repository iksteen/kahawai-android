package com.kolktech.kahawai.data.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.kolktech.kahawai.BuildConfig
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

    /// One-off service against a CANDIDATE hub URL during setup — lets the
    /// reachability check run before the URL is committed to
    /// [ServerConfigStore], so a typo'd address never becomes the stored
    /// base URL. Not cached: setup is a one-shot flow.
    fun probeApiService(candidateBaseUrl: String): ApiService =
        buildRetrofit(plainClient(), normalize(candidateBaseUrl)).create(ApiService::class.java)

    fun apiService(): ApiService = authRetrofit().create(ApiService::class.java)

    fun adminApiService(): AdminApiService = authRetrofit().create(AdminApiService::class.java)

    fun authenticatedOkHttpClient(): OkHttpClient = authClient()

    /// Same auth/refresh pipeline as [authenticatedOkHttpClient] (built via
    /// `newBuilder()`, so it's cheap and stays in sync), but for ExoPlayer's
    /// OkHttpDataSource rather than Retrofit: reading an HLS segment can
    /// stall well past a normal API call's 10s if the hub's transcode
    /// pipeline is still producing it, and OkHttp's default read/write
    /// timeout (10s, same as the connect timeout set below) would surface
    /// that stall to ExoPlayer as a generic source error indistinguishable
    /// from an actual failure.
    fun streamingOkHttpClient(): OkHttpClient = authClient().newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun baseUrl(): String =
        normalize(serverConfigStore.baseUrl ?: error("hub server not configured yet"))

    private fun normalize(url: String): String = if (url.endsWith("/")) url else "$url/"

    private fun loggingInterceptor() = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private fun plainClient(): OkHttpClient {
        return plainClient ?: OkHttpClient.Builder()
            .apply { if (BuildConfig.DEBUG) addInterceptor(loggingInterceptor()) }
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()
            .also { plainClient = it }
    }

    private fun authClient(): OkHttpClient {
        return authClient ?: OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .apply { if (BuildConfig.DEBUG) addInterceptor(loggingInterceptor()) }
            .authenticator(TokenAuthenticator(tokenStore) { plainApiService() })
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()
            .also { authClient = it }
    }

    private fun plainRetrofit(): Retrofit =
        plainRetrofit ?: buildRetrofit(plainClient(), baseUrl()).also { plainRetrofit = it }

    private fun authRetrofit(): Retrofit =
        authRetrofit ?: buildRetrofit(authClient(), baseUrl()).also { authRetrofit = it }

    private fun buildRetrofit(client: OkHttpClient, baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
