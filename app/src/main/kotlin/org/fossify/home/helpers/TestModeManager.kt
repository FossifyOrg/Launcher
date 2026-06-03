package org.fossify.home.helpers

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Handles test mode QR payload and session key exchange via SHARED cache file.
 * Used ONLY for same-device testing - both apps read/write to shared external cache.
 *
 * Flow:
 * 1. Main App: writes QR payload to shared cache
 * 2. Companion App: reads QR payload from shared cache
 * 3. Companion App: generates AES key, encrypts with public key from QR
 * 4. Companion App: writes encrypted session key to shared cache
 * 5. Main App: reads encrypted key, decrypts with private key
 * 6. Both apps now have matching session keys for encrypted command testing
 */
object TestModeManager {
    private const val TAG = "TestModeManager"
    private const val TEST_QR_FILE = "launchpad_test_qr.json"
    private const val TEST_SESSION_KEY_FILE = "launchpad_test_session_key.txt"

    /**
     * Get shared external cache file (accessible by all apps).
     * Falls back to regular cache if external not available.
     */
    fun getTestQrCacheFile(context: Context): File {
        val externalCache = context.externalCacheDir
        return if (externalCache != null && externalCache.exists()) {
            File(externalCache, TEST_QR_FILE)
        } else {
            // Fallback to internal cache
            File(context.cacheDir, TEST_QR_FILE)
        }
    }

    fun getTestSessionKeyFile(context: Context): File {
        val externalCache = context.externalCacheDir
        return if (externalCache != null && externalCache.exists()) {
            File(externalCache, TEST_SESSION_KEY_FILE)
        } else {
            // Fallback to internal cache
            File(context.cacheDir, TEST_SESSION_KEY_FILE)
        }
    }

    /**
     * Write the QR payload JSON to cache file (Main App side).
     */
    fun writeTestQrPayload(context: Context, qrPayloadJson: String): Boolean {
        return try {
            val file = getTestQrCacheFile(context)
            file.parentFile?.mkdirs() // Ensure directory exists
            file.writeText(qrPayloadJson)
            Log.d(TAG, "Test QR payload written to ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write test QR payload", e)
            false
        }
    }

    /**
     * Read the QR payload JSON from cache file (Companion App side).
     */
    fun readTestQrPayload(context: Context): String? {
        return try {
            val file = getTestQrCacheFile(context)
            if (file.exists()) {
                file.readText()
            } else {
                Log.w(TAG, "Test QR file not found at ${file.absolutePath}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read test QR payload", e)
            null
        }
    }

    /**
     * Write the encrypted session key to cache (Companion App side).
     */
    fun writeTestSessionKey(context: Context, encryptedSessionKeyBase64: String): Boolean {
        return try {
            val file = getTestSessionKeyFile(context)
            file.parentFile?.mkdirs() // Ensure directory exists
            file.writeText(encryptedSessionKeyBase64)
            Log.d(TAG, "Test session key written to ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write test session key", e)
            false
        }
    }

    /**
     * Read the encrypted session key from cache (Main App side).
     */
    fun readTestSessionKey(context: Context): String? {
        return try {
            val file = getTestSessionKeyFile(context)
            if (file.exists()) {
                file.readText()
            } else {
                Log.w(TAG, "Test session key file not found (companion may not have written it yet)")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read test session key", e)
            null
        }
    }

    /**
     * Check if test QR is available.
     */
    fun isTestQrAvailable(context: Context): Boolean =
        getTestQrCacheFile(context).exists()

    /**
     * Check if test session key is available.
     */
    fun isTestSessionKeyAvailable(context: Context): Boolean =
        getTestSessionKeyFile(context).exists()

    /**
     * Clean up all test mode files.
     */
    fun clearTestMode(context: Context): Boolean {
        return try {
            listOf(
                getTestQrCacheFile(context),
                getTestSessionKeyFile(context)
            ).all { it.delete() || !it.exists() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear test mode files", e)
            false
        }
    }
}
