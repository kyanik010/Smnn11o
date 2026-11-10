package com.idealplayer.app.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parental Control manager using EncryptedSharedPreferences.
 *
 * Features:
 * - PIN setup and verification (4-digit)
 * - Per-category whitelist (child-safe categories)
 * - Child lock mode toggle
 * - PIN hash stored encrypted via AndroidKeyStore / EncryptedSharedPreferences
 */
@Singleton
class ParentalControlManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val PREFS_NAME = "idealplayer_parental_control"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_CHILD_LOCK = "child_lock_enabled"
        const val KEY_WHITELIST_PREFIX = "whitelist_"
        const val KEY_PIN_SET = "pin_set"
        const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        const val KEY_LOCKED_UNTIL = "locked_until"
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_MS = 30_000L
    }

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e(e, "EncryptedSharedPreferences init failed")
            throw IllegalStateException("Secure parental control storage is unavailable", e)
        }
    }

    /**
     * Returns true if a PIN has been set.
     */
    val isPinSet: Boolean
        get() = prefs.getBoolean(KEY_PIN_SET, false)

    /**
     * Returns true if child lock is currently active.
     */
    val isChildLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_CHILD_LOCK, false)

    /**
     * Set a new 4-digit PIN. Stores a salted PBKDF2 hash.
     * Returns false if PIN format is invalid.
     */
    fun setPin(pin: String): Boolean {
        if (pin.length != 4 || !pin.all { it.isDigit() }) return false
        prefs.edit()
            .putString(KEY_PIN_HASH, PinHasher.hash(pin))
            .putBoolean(KEY_PIN_SET, true)
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKED_UNTIL)
            .apply()
        Timber.d("ParentalControl: PIN set")
        return true
    }

    /**
     * Verify the given PIN. Returns true if it matches.
     */
    fun verifyPin(pin: String): Boolean {
        if (!isPinSet) return false
        val now = System.currentTimeMillis()
        if (prefs.getLong(KEY_LOCKED_UNTIL, 0L) > now) return false

        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val verified = PinHasher.verify(pin, storedHash)

        if (verified) {
            prefs.edit()
                .remove(KEY_FAILED_ATTEMPTS)
                .remove(KEY_LOCKED_UNTIL)
                .apply()

            if (PinHasher.needsRehash(storedHash)) {
                prefs.edit().putString(KEY_PIN_HASH, PinHasher.hash(pin)).apply()
            }
            return true
        }

        recordFailedAttempt(now)
        return false
    }

    /**
     * Enable or disable child lock mode.
     * Requires PIN verification — call [verifyPin] before.
     */
    fun setChildLock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CHILD_LOCK, enabled).apply()
        Timber.d("ParentalControl: child lock = $enabled")
    }

    /**
     * Add a category to the whitelist (allowed in child lock mode).
     */
    fun addToWhitelist(categoryName: String) {
        prefs.edit().putBoolean("$KEY_WHITELIST_PREFIX${categoryName.lowercase()}", true).apply()
    }

    /**
     * Remove a category from the whitelist.
     */
    fun removeFromWhitelist(categoryName: String) {
        prefs.edit().remove("$KEY_WHITELIST_PREFIX${categoryName.lowercase()}").apply()
    }

    /**
     * Check if a category is allowed in child lock mode.
     */
    fun isCategoryAllowed(categoryName: String): Boolean {
        if (!isChildLockEnabled) return true
        return prefs.getBoolean("$KEY_WHITELIST_PREFIX${categoryName.lowercase()}", false)
    }

    /**
     * Returns all whitelisted category names.
     */
    fun getWhitelistedCategories(): Set<String> {
        return prefs.all
            .filter { it.key.startsWith(KEY_WHITELIST_PREFIX) && it.value == true }
            .map { it.key.removePrefix(KEY_WHITELIST_PREFIX) }
            .toSet()
    }

    /**
     * Clear PIN and disable child lock.
     */
    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .putBoolean(KEY_PIN_SET, false)
            .putBoolean(KEY_CHILD_LOCK, false)
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKED_UNTIL)
            .apply()
        Timber.d("ParentalControl: PIN cleared")
    }

    private fun recordFailedAttempt(now: Long) {
        val failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        prefs.edit().apply {
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                putInt(KEY_FAILED_ATTEMPTS, 0)
                putLong(KEY_LOCKED_UNTIL, now + LOCKOUT_MS)
            } else {
                putInt(KEY_FAILED_ATTEMPTS, failedAttempts)
            }
        }.apply()
    }
}
