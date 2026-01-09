package com.bossless.companion.data.repository

import com.bossless.companion.data.api.ApiService
import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.data.models.BusinessProfile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BusinessProfileRepository @Inject constructor(
    private val apiService: ApiService,
    private val securePrefs: SecurePrefs
) {
    /**
     * Get cached business logo URL (synchronous, for quick access)
     */
    fun getCachedLogoUrl(): String? {
        return securePrefs.getBusinessLogoUrl()
    }
    
    /**
     * Get cached business name (synchronous, for quick access)
     */
    fun getCachedBusinessName(): String? {
        return securePrefs.getBusinessName()
    }
    
    /**
     * Fetch business profile from API and cache it
     * Returns the business profile or null if failed
     */
    suspend fun fetchAndCacheBusinessProfile(): Result<BusinessProfile?> {
        return try {
            val response = apiService.getBusinessProfile()
            if (response.isSuccessful) {
                val profile = response.body()?.firstOrNull()
                // Cache the profile data
                if (profile != null) {
                    securePrefs.saveBusinessProfile(
                        businessName = profile.business_name,
                        logoUrl = profile.logo_url
                    )
                }
                Result.success(profile)
            } else {
                Result.failure(Exception("Failed to fetch business profile: ${response.code()}"))
            }
        } catch (e: Exception) {
            // Return cached data as fallback
            val cachedName = securePrefs.getBusinessName()
            val cachedLogo = securePrefs.getBusinessLogoUrl()
            if (cachedName != null || cachedLogo != null) {
                Result.success(BusinessProfile(
                    business_name = cachedName,
                    logo_url = cachedLogo
                ))
            } else {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Clear cached business profile (e.g., on logout)
     */
    fun clearCache() {
        securePrefs.clearBusinessProfile()
    }
}
