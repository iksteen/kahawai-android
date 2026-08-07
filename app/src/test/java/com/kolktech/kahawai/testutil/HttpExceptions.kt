package com.kolktech.kahawai.testutil

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

fun httpException(code: Int, errorBody: String = ""): HttpException =
    HttpException(Response.error<Any>(code, errorBody.toResponseBody("application/json".toMediaType())))
