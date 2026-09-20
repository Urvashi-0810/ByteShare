package com.example.test.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface UsageApi {
    @POST("usage/me")
    suspend fun upsertMyUsage(
        @Header("Authorization") authorization: String,
        @Body body: UpsertUsageBody,
    ): UsageSnapshotDto

    companion object {
        fun create(baseUrl: String): UsageApi {
            val normalized =
                baseUrl.trimEnd('/') + "/"
            return Retrofit.Builder()
                .baseUrl(normalized)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(UsageApi::class.java)
        }
    }
}
