package org.fossify.home.helpers

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * Handles test mode QR payload and session key exchange via SHARED public cache.
 * Used ONLY for same-device testing - both apps read/write to shared public directory.
 *
 * IMPORTANT: This uses a truly shared location that both apps can access without
 * being app-specific. On Android 11+, this requires MANAGE_EXTERNAL_STORAGE permission
 * in the manifest (or we can use app's cache dir as fallback for testing).
 *
 * Flow:
 * 1. Main App: writes QR payload to shared public cache
 * 2. Companion App: reads QR payload from shared public cache
 * 3. Companion App: generates AES key, encrypts with public key from QR
 * 4. Companion App: writes encrypted session key to shared public cache
 * 5. Main App: reads encrypted key, decrypts with private key
 * 6. Both apps now have matching session keys for encrypted command testing
 */
object TestModeManager {
    private const val TAG = "TestModeManager"
    private const val TEST_QR_FILE = "launchpad_test_qr.json"
    private const val TEST_SESSION_KEY_FILE = "launchpad_test_session_key.txt"
    private const val SHARED_CACHE_DIR = "LAUNCHPAD_TEST"

    /**
     * Get shared public cache directory accessible by all apps.
     * Uses Environment.getExternalStorageDirectory() which is writable by all apps
     * (or their cache dir as fallback for debugging).
     */
    private fun getSharedCacheDir(context: Context): File {
        // Try to use public external storage first
        val externalStorageState = Environment.getExternalStorageState()
        val externalStorageAvailable = externalStorageState == Environment.MEDIA_MOUNTED

        if (externalStorageAvailable) {
            try {
                val publicCache = File(Environment.getExternalStorageDirectory(), SHARED_CACHE_DIR)
                Log.d(TAG, "Using public external storage: ${publicCache.absolutePath}")
                return publicCache
            } catch (e: Exception) {
                Log.w(TAG, "Could not use public external storage: ${e.message}")
            }
        }

        // Fallback: use device cache directory (usually /dev/cache or /cache)
        // This is more likely to be writable on test devices
        val cacheDir = context.cacheDir
        Log.w(TAG, "Falling back to app cache directory: ${cacheDir.absolutePath}")
        return cacheDir
    }

    fun getTestQrCacheFile(context: Context): File {
        val dir = getSharedCacheDir(context)
        val file = File(dir, TEST_QR_FILE)
        Log.d(TAG, "QR cache file path: ${file.absolutePath}")
        return file
    }

    fun getTestSessionKeyFile(context: Context): File {
        val dir = getSharedCacheDir(context)
        val file = File(dir, TEST_SESSION_KEY_FILE)
        Log.d(TAG, "Session key file path: ${file.absolutePath}")
        return file
    }

    /**
     * Write the QR payload JSON to cache file (Main App side).
     */
    fun writeTestQrPayload(context: Context, qrPayloadJson: String): Boolean {
        return try {
            val file = getTestQrCacheFile(context)
            Log.d(TAG, "Attempting to write QR payload to: ${file.absolutePath}")
            Log.d(TAG, "QR payload size: ${qrPayloadJson.length} bytes")

            file.parentFile?.mkdirs() // Ensure directory exists
            val parentPath = file.parentFile?.absolutePath
            Log.d(TAG, "Parent directory: $parentPath")
            Log.d(TAG, "Parent directory exists: ${file.parentFile?.exists()}")
            Log.d(TAG, "Parent directory writable: ${file.parentFile?.canWrite()}")

            file.writeText(qrPayloadJson)

            Log.d(TAG, "✓ Test QR payload written successfully to ${file.absolutePath}")
            Log.d(TAG, "File exists after write: ${file.exists()}, size: ${file.length()} bytes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "✗ FAILED to write test QR payload: ${e.message}", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * Read the QR payload JSON from cache file (Companion App side).
     */
    fun readTestQrPayload(context: Context): String? {
        return try {
            val file = getTestQrCacheFile(context)
            Log.d(TAG, "Attempting to read QR payload from: ${file.absolutePath}")
            Log.d(TAG, "File exists: ${file.exists()}")
            if (file.exists()) {
                Log.d(TAG, "File size: ${file.length()} bytes")
                val content = file.readText()
                Log.d(TAG, "✓ Successfully read QR payload (${content.length} bytes)")
                content
            } else {
                Log.w(TAG, "✗ Test QR file not found at ${file.absolutePath}")
                Log.w(TAG, "  Parent directory: ${file.parentFile?.absolutePath}")
                Log.w(TAG, "  Parent exists: ${file.parentFile?.exists()}")
                val parentContents = file.parentFile?.listFiles()
                if (parentContents != null) {
                    Log.w(TAG, "  Parent contents: ${parentContents.joinToString { it.name }}")
                } else {
                    Log.w(TAG, "  Parent contents: EMPTY or unreadable")
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ FAILED to read test QR payload: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }

    /**
     * Write the encrypted session key to cache (Companion App side).
     */
    fun writeTestSessionKey(context: Context, encryptedSessionKeyBase64: String): Boolean {
        return try {
            val file = getTestSessionKeyFile(context)
            Log.d(TAG, "Attempting to write session key to: ${file.absolutePath}")
            Log.d(TAG, "Session key size: ${encryptedSessionKeyBase64.length} bytes")

            file.parentFile?.mkdirs() // Ensure directory exists
            Log.d(TAG, "Parent directory created/verified: ${file.parentFile?.absolutePath}")

            file.writeText(encryptedSessionKeyBase64)

            Log.d(TAG, "✓ Test session key written successfully to ${file.absolutePath}")
            Log.d(TAG, "File exists after write: ${file.exists()}, size: ${file.length()} bytes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "✗ FAILED to write test session key: ${e.message}", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * Read the encrypted session key from cache (Main App side).
     */
    fun readTestSessionKey(context: Context): String? {
        return try {
            val file = getTestSessionKeyFile(context)
            Log.d(TAG, "Attempting to read session key from: ${file.absolutePath}")
            Log.d(TAG, "File exists: ${file.exists()}")
            if (file.exists()) {
                Log.d(TAG, "File size: ${file.length()} bytes")
                val content = file.readText()
                Log.d(TAG, "✓ Successfully read session key (${content.length} bytes)")
                content
            } else {
                Log.w(TAG, "✗ Test session key file not found (companion may not have written it yet)")
                Log.w(TAG, "  Expected path: ${file.absolutePath}")
                val parentContents = file.parentFile?.listFiles()
                if (parentContents != null) {
                    Log.w(TAG, "  Parent contents: ${parentContents.joinToString { it.name }}")
                } else {
                    Log.w(TAG, "  Parent contents: EMPTY or unreadable")
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ FAILED to read test session key: ${e.message}", e)
            e.printStackTrace()
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
