package com.example.test.api

import com.google.gson.annotations.SerializedName

data class AppUsageEntry(
    @SerializedName("appName") val appName: String,
    @SerializedName("category") val category: String,
    @SerializedName("minutes") val minutes: Double,
)

data class UpsertUsageBody(
    @SerializedName("reportDate") val reportDate: String? = null,
    @SerializedName("apps") val apps: List<AppUsageEntry>,
    @SerializedName("source") val source: String = "android",
)

data class UsageSnapshotDto(
    @SerializedName("userId") val userId: String,
    @SerializedName("apps") val apps: List<AppUsageEntry> = emptyList(),
    @SerializedName("updated") val updated: String? = null,
    @SerializedName("reportDate") val reportDate: String? = null,
)
