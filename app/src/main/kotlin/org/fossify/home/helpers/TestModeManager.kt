package org.fossify.home.helpers

import android.content.Context
import android.util.Log
import java.io.File

object TestModeManager {
    private const val TAG = "TestModeManager"
    private const val TEST_QR_FILE = "launchpad_test_qr.json"

    fun getTestQrCacheFile(context: Context): File =
        File(context.cacheDir, TEST_QR_FILE)

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

    fun isTestQrAvailable(context: Context): Boolean =
        getTestQrCacheFile(context).exists()

    fun clearTestQrPayload(context: Context): Boolean {
        return try {
            getTestQrCacheFile(context).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear test QR", e)
            false
        }
    }
}
