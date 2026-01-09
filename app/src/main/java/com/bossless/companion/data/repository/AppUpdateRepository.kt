package com.bossless.companion.data.repository

import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.data.models.AppUpdateManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val securePrefs: SecurePrefs
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun getLastUpdateCheckAtMs(): Long = securePrefs.getLastUpdateCheckAtMs()

    fun setLastUpdateCheckAtMs(value: Long) = securePrefs.setLastUpdateCheckAtMs(value)

    fun getCachedAvailableUpdate(): CachedAvailableUpdate? {
        val versionCode = securePrefs.getAvailableUpdateVersionCode() ?: return null
        val versionName = securePrefs.getAvailableUpdateVersionName() ?: return null
        val apkUrl = securePrefs.getAvailableUpdateApkUrl() ?: return null
        return CachedAvailableUpdate(versionCode, versionName, apkUrl)
    }

    fun cacheAvailableUpdate(update: CachedAvailableUpdate?) {
        if (update == null) {
            securePrefs.clearAvailableUpdate()
            return
        }
        securePrefs.setAvailableUpdateVersionCode(update.versionCode)
        securePrefs.setAvailableUpdateVersionName(update.versionName)
        securePrefs.setAvailableUpdateApkUrl(update.apkUrl)
    }

    suspend fun fetchManifest(url: String): AppUpdateManifest = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Update manifest request failed: HTTP ${response.code}")
            }

            val body = response.body?.string()
                ?: throw IOException("Update manifest response body was empty")

            json.decodeFromString(AppUpdateManifest.serializer(), body)
        }
    }

    suspend fun downloadApk(
        apkUrl: String,
        destinationFile: File,
        expectedSha256Hex: String? = null,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit
    ) = withContext(Dispatchers.IO) {
        destinationFile.parentFile?.mkdirs()
        if (destinationFile.exists()) destinationFile.delete()

        val request = Request.Builder()
            .url(apkUrl)
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("APK download failed: HTTP ${response.code}")
            }

            val body = response.body ?: throw IOException("APK download body was empty")
            val total = body.contentLength().takeIf { it > 0 }

            val digest = if (!expectedSha256Hex.isNullOrBlank()) MessageDigest.getInstance("SHA-256") else null

            body.byteStream().use { input ->
                val inStream = if (digest != null) DigestInputStream(input, digest) else input

                destinationFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = inStream.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                    output.flush()
                }
            }

            if (digest != null) {
                val actual = digest.digest().toHexLower()
                val expected = expectedSha256Hex!!.trim().lowercase()
                if (actual != expected) {
                    destinationFile.delete()
                    throw IOException("APK SHA-256 mismatch")
                }
            }
        }
    }

    data class CachedAvailableUpdate(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String
    )

    private fun ByteArray.toHexLower(): String {
        val chars = CharArray(size * 2)
        var i = 0
        forEach { b ->
            val v = b.toInt() and 0xFF
            chars[i++] = HEX[v ushr 4]
            chars[i++] = HEX[v and 0x0F]
        }
        return String(chars)
    }

    private companion object {
        private val HEX = "0123456789abcdef".toCharArray()
    }
}
