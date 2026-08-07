package com.kolktech.kahawai.data.network

import okhttp3.Interceptor
import okhttp3.Response
import com.kolktech.kahawai.data.auth.TokenStorage

class AuthInterceptor(private val tokenStore: TokenStorage) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenStore.accessToken ?: return chain.proceed(chain.request())
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}
