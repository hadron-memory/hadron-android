package com.hadronmemory.hadronkit

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * One process-wide OkHttp client — connection pool and thread pool are meant
 * to be shared, and the OAuth and GraphQL legs talk to the same origin.
 */
internal object HadronHttp {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Server responses carry fields we don't model; never fail on them. */
    val json: Json = Json { ignoreUnknownKeys = true }

    val jsonMediaType = "application/json".toMediaType()
    val formMediaType = "application/x-www-form-urlencoded".toMediaType()
}
