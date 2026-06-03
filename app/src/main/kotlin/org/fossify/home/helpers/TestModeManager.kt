package org.fossify.home.helpers

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Base64

/**
 * Handles test mode QR payload and session key exchange via cache files.
 * Used ONLY in DEBUG builds for same-device testing.
 *
 * Flow:
 * 1. Main App: writes QR payload + private key to cache
 * 2. Companion App: reads QR payload, generates AES key, encrypts with public key from QR
 * 3. Companion App: writes encrypted session key to cache
 * 4. Main App: reads encrypted key, decrypts with private key from cache
 * 5. Both apps now have matching session keys for encrypted command testing
 */
object TestModeManager {
    private const val TAG = "TestModeManager"
    private const val TEST_QR_FILE = "launchpad_test_qr.json"
    private const val TEST_PRIVATE_KEY_FILE = "launchpad_test_private_key.txt"
    private const val TEST_SESSION_KEY_FILE = "launchpad_test_session_key.txt"

    fun getTestQrCacheFile(context: Context): File =
        File(context.cacheDir, TEST_QR_FILE)

    fun getTestPrivateKeyFile(context: Context): File =
        File(context.cacheDir, TEST_PRIVATE_KEY_FILE)

    fun getTestSessionKeyFile(context: Context): File =
        File(context.cacheDir, TEST_SESSION_KEY_FILE)

    /**
     * Write the QR payload JSON to cache file (Main App side).
     * This makes the payload available for Companion App to read.
     */
    fun writeTestQrPayload(context: Context, qrPayloadJson: String): Boolean {
        return try {
            val file = getTestQrCacheFile(context)
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
     * Returns the payload or null if file not found.
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
     * Write the private key to cache (Main App side, for decryption reference in test mode).
     * Base64-encoded PEM format.
     */
    fun writeTestPrivateKey(context: Context, privateKeyPem: String): Boolean {
        return try {
            val file = getTestPrivateKeyFile(context)
            file.writeText(privateKeyPem)
            Log.d(TAG, "Test private key written to ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write test private key", e)
            false
        }
    }

    /**
     * Read the private key from cache (Main App side, for session key decryption).
     */
    fun readTestPrivateKey(context: Context): String? {
        return try {
            val file = getTestPrivateKeyFile(context)
            if (file.exists()) {
                file.readText()
            } else {
                Log.w(TAG, "Test private key file not found")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read test private key", e)
            null
        }
    }

    /**
     * Write the encrypted session key to cache (Companion App side).
     * This is what the Main App will read and decrypt.
     */
    fun writeTestSessionKey(context: Context, encryptedSessionKeyBase64: String): Boolean {
        return try {
            val file = getTestSessionKeyFile(context)
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
     * Returns Base64-encoded encrypted key, or null if not yet available.
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
     * Check if test QR is available (for Companion to know if it can read it).
     */
    fun isTestQrAvailable(context: Context): Boolean =
        getTestQrCacheFile(context).exists()

    /**
     * Check if test session key is available (for Main App to know if Companion completed pairing).
     */
    fun isTestSessionKeyAvailable(context: Context): Boolean =
        getTestSessionKeyFile(context).exists()

    /**
     * Clean up all test mode files after use.
     */
    fun clearTestMode(context: Context): Boolean {
        return try {
            listOf(
                getTestQrCacheFile(context),
                getTestPrivateKeyFile(context),
                getTestSessionKeyFile(context)
            ).all { it.delete() || !it.exists() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear test mode files", e)
            false
        }
    }
}
