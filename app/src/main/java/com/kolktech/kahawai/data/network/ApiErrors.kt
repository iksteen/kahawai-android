package com.kolktech.kahawai.data.network

import retrofit2.HttpException

/// `HttpException.message` is just the status line ("HTTP 409
/// Conflict") — the hub puts the actual reason in the response body
/// (`format!("{e:#}")` throughout crates/kahawai-hub/src/api.rs, e.g.
/// "no source is currently available (mediahost offline)"), which is
/// what a user needs to see.
fun Throwable.readableMessage(): String {
    if (this is HttpException) {
        val body = response()?.errorBody()?.string()?.trim()
        if (!body.isNullOrEmpty()) return body
    }
    return message ?: "Unknown error"
}
