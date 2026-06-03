// Same-device test harness: delegates to LaunchpadServer in-process vars.
// No file I/O, no storage permissions required on any API level.
@file:Suppress("TooGenericExceptionCaught")

package org.fossify.home.helpers

import android.util.Log

object TestModeManager {
    private const val TAG = "TestModeManager"

    fun writeTestQrPayload(qrPayloadJson: String): Boolean {
        return try {
            LaunchpadServer.testQrPayload = qrPayloadJson
            Log.d(TAG, "Test QR payload stored in server (${qrPayloadJson.length} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store test QR payload", e)
            false
        }
    }

    fun readTestSessionKey(): String? = LaunchpadServer.testSessionKey

    fun clearTestMode() {
        LaunchpadServer.testQrPayload = null
        LaunchpadServer.testSessionKey = null
    }
}
