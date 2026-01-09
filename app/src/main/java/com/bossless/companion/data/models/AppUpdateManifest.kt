package com.bossless.companion.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppUpdateManifest(
    @SerialName("versionCode") val versionCode: Int,
    @SerialName("versionName") val versionName: String,
    @SerialName("apkUrl") val apkUrl: String,
    // Optional: integrity check (hex lowercase/uppercase accepted)
    @SerialName("sha256") val sha256: String? = null
)
