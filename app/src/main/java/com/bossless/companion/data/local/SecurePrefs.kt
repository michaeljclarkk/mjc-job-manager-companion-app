package com.bossless.companion.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePrefs @Inject constructor(
    @ApplicationContext context: Context
) {
    private val sharedPreferences: SharedPreferences

    private val secureRandom = SecureRandom()

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        sharedPreferences = EncryptedSharedPreferences.create(
            context,
            "secret_shared_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveServerUrl(url: String) {
        sharedPreferences.edit().putString("server_url", url).apply()
    }

    fun getServerUrl(): String? {
        return sharedPreferences.getString("server_url", null)
    }

    fun saveApiKey(key: String) {
        sharedPreferences.edit().putString("api_key", key).apply()
    }

    fun getApiKey(): String? {
        return sharedPreferences.getString("api_key", null)
    }

    fun saveAuthToken(accessToken: String, refreshToken: String, userId: String, email: String) {
        sharedPreferences.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putString("user_id", userId)
            .putString("user_email", email)
            .putBoolean("pin_unlock_required", false)
            .apply()
    }

    fun saveAuthToken(
        accessToken: String,
        refreshToken: String,
        userId: String,
        email: String,
        tokenExpiresAtEpochSeconds: Long?
    ) {
        val editor = sharedPreferences.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putString("user_id", userId)
            .putString("user_email", email)
            .putBoolean("pin_unlock_required", false)

        if (tokenExpiresAtEpochSeconds != null && tokenExpiresAtEpochSeconds > 0) {
            editor.putLong("token_expires_at_epoch_seconds", tokenExpiresAtEpochSeconds)
        } else {
            editor.remove("token_expires_at_epoch_seconds")
        }

        editor.apply()
    }

    fun getAccessToken(): String? {
        return sharedPreferences.getString("access_token", null)
    }

    fun getRefreshToken(): String? {
        return sharedPreferences.getString("refresh_token", null)
    }

    fun getUserId(): String? {
        return sharedPreferences.getString("user_id", null)
    }
    
    fun getUserEmail(): String? {
        return sharedPreferences.getString("user_email", null)
    }

    fun clearAuth() {
        sharedPreferences.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("user_id")
            .remove("user_email")
            .remove("token_expires_at_epoch_seconds")
            .remove("pin_unlock_required")
            .apply()
    }

    fun shouldSendLocationErrorReport(signature: String, minIntervalMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val lastSig = sharedPreferences.getString("location_error_sig", null)
        val lastAt = sharedPreferences.getLong("location_error_at_ms", 0L)

        val shouldSend = lastSig != signature || (now - lastAt) >= minIntervalMs
        if (shouldSend) {
            sharedPreferences.edit()
                .putString("location_error_sig", signature)
                .putLong("location_error_at_ms", now)
                .apply()
        }
        return shouldSend
    }

    fun getTokenExpiresAtEpochSeconds(): Long? {
        val value = sharedPreferences.getLong("token_expires_at_epoch_seconds", 0L)
        if (value > 0) return value

        // Backfill for older installs: derive expiry from JWT 'exp' claim if possible.
        val derived = deriveJwtExpiryFromAccessToken()
        if (derived != null && derived > 0) {
            sharedPreferences.edit().putLong("token_expires_at_epoch_seconds", derived).apply()
            return derived
        }

        return null
    }

    fun isTokenExpired(nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): Boolean {
        val expiresAt = getTokenExpiresAtEpochSeconds() ?: return true
        return nowEpochSeconds >= expiresAt
    }

    fun setPinUnlockRequired(required: Boolean) {
        sharedPreferences.edit().putBoolean("pin_unlock_required", required).apply()
    }

    fun isPinUnlockRequired(): Boolean {
        return sharedPreferences.getBoolean("pin_unlock_required", false)
    }

    fun hasPin(): Boolean {
        val hash = sharedPreferences.getString("pin_hash", null)
        val salt = sharedPreferences.getString("pin_salt", null)
        return !hash.isNullOrBlank() && !salt.isNullOrBlank()
    }

    fun savePin(pin: String) {
        val saltBytes = ByteArray(16).also { secureRandom.nextBytes(it) }
        val hashBytes = derivePinHash(pin, saltBytes)
        sharedPreferences.edit()
            .putString("pin_salt", base64Encode(saltBytes))
            .putString("pin_hash", base64Encode(hashBytes))
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val saltB64 = sharedPreferences.getString("pin_salt", null) ?: return false
        val hashB64 = sharedPreferences.getString("pin_hash", null) ?: return false

        val saltBytes = base64Decode(saltB64) ?: return false
        val expectedHash = base64Decode(hashB64) ?: return false
        val actualHash = derivePinHash(pin, saltBytes)
        return MessageDigest.isEqual(expectedHash, actualHash)
    }

    fun clearPin() {
        sharedPreferences.edit()
            .remove("pin_salt")
            .remove("pin_hash")
            .apply()
    }

    fun registerOnChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun derivePinHash(pin: String, saltBytes: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), saltBytes, 100_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun base64Encode(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun base64Decode(value: String): ByteArray? {
        return try {
            Base64.getDecoder().decode(value)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun deriveJwtExpiryFromAccessToken(): Long? {
        val token = getAccessToken() ?: return null
        val parts = token.split('.')
        if (parts.size < 2) return null

        val payloadJson = try {
            String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            return null
        }

        return try {
            val obj = JSONObject(payloadJson)
            val exp = obj.optLong("exp", 0L)
            if (exp > 0) exp else null
        } catch (_: Exception) {
            null
        }
    }

    fun setTrustAllCerts(trust: Boolean) {
        sharedPreferences.edit().putBoolean("trust_all_certs", trust).apply()
    }

    fun getTrustAllCerts(): Boolean {
        return sharedPreferences.getBoolean("trust_all_certs", false)
    }
    
    fun setBackgroundNotifications(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("bg_notifications", enabled).apply()
    }
    
    fun getBackgroundNotifications(): Boolean {
        return sharedPreferences.getBoolean("bg_notifications", true)
    }
    
    // Business Profile caching
    fun saveBusinessProfile(businessName: String?, logoUrl: String?) {
        sharedPreferences.edit()
            .putString("business_name", businessName)
            .putString("business_logo_url", logoUrl)
            .apply()
    }
    
    fun getBusinessName(): String? {
        return sharedPreferences.getString("business_name", null)
    }
    
    fun getBusinessLogoUrl(): String? {
        return sharedPreferences.getString("business_logo_url", null)
    }
    
    fun clearBusinessProfile() {
        sharedPreferences.edit()
            .remove("business_name")
            .remove("business_logo_url")
            .apply()
    }
    
    // Last sync timestamp
    fun saveLastSyncTimestamp(epochSeconds: Long) {
        sharedPreferences.edit().putLong("last_sync_timestamp", epochSeconds).apply()
    }
    
    fun getLastSyncTimestamp(): Long? {
        val timestamp = sharedPreferences.getLong("last_sync_timestamp", 0L)
        return if (timestamp > 0) timestamp else null
    }
    
    // Theme preference
    fun saveThemeMode(mode: ThemeMode) {
        sharedPreferences.edit().putString("theme_mode", mode.name).apply()
    }
    
    fun getThemeMode(): ThemeMode {
        val modeName = sharedPreferences.getString("theme_mode", ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(modeName ?: ThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }
    
    // Location tracking preference
    fun setLocationTrackingEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("location_tracking_enabled", enabled).apply()
    }
    
    fun getLocationTrackingEnabled(): Boolean {
        return sharedPreferences.getBoolean("location_tracking_enabled", true)
    }

    // Location tracking: only during business hours
    fun setLocationTrackingBusinessHoursOnly(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("location_tracking_business_hours_only", enabled).apply()
    }

    fun getLocationTrackingBusinessHoursOnly(): Boolean {
        return sharedPreferences.getBoolean("location_tracking_business_hours_only", true)
    }

    // App updates (self-hosted)
    fun setLastUpdateCheckAtMs(value: Long) {
        sharedPreferences.edit().putLong("last_update_check_at_ms", value).apply()
    }

    fun getLastUpdateCheckAtMs(): Long {
        return sharedPreferences.getLong("last_update_check_at_ms", 0L)
    }

    fun setAvailableUpdateVersionCode(value: Int) {
        sharedPreferences.edit().putInt("available_update_version_code", value).apply()
    }

    fun getAvailableUpdateVersionCode(): Int? {
        val value = sharedPreferences.getInt("available_update_version_code", 0)
        return if (value > 0) value else null
    }

    fun setAvailableUpdateVersionName(value: String) {
        sharedPreferences.edit().putString("available_update_version_name", value).apply()
    }

    fun getAvailableUpdateVersionName(): String? {
        return sharedPreferences.getString("available_update_version_name", null)
    }

    fun setAvailableUpdateApkUrl(value: String) {
        sharedPreferences.edit().putString("available_update_apk_url", value).apply()
    }

    fun getAvailableUpdateApkUrl(): String? {
        return sharedPreferences.getString("available_update_apk_url", null)
    }

    fun clearAvailableUpdate() {
        sharedPreferences.edit()
            .remove("available_update_version_code")
            .remove("available_update_version_name")
            .remove("available_update_apk_url")
            .apply()
    }
    
    // Generic getter for boolean preferences (used by service)
    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}
