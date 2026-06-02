// File: app/src/main/kotlin/org/fossify/home/helpers/PairingManager.kt
// M4: launcher-side pairing state. Holds an RSA keypair, publishes the public key in a QR for
// the parent app to scan, receives the parent's AES session key (RSA-encrypted), and decrypts
// incoming commands with it. Transport (LAN/QR-return/paste) is supplied by the caller.

package org.fossify.home.helpers

import android.content.Context
import android.util.Log

class PairingManager(context: Context) {
    private val tag = "PairingManager"
    private val appContext = context.applicationContext
    private val proto = QrPairingProtocol()

    private fun prefs() =
        appContext.getSharedPreferences(LaunchpadPrefs.PREFS_FILE, Context.MODE_PRIVATE)

    private val launcherIdentity = "launchpad"

    fun isPaired(): Boolean =
        !prefs().getString(LaunchpadPrefs.PREF_PAIR_SESSION_KEY, null).isNullOrEmpty()

    fun pairedParentId(): String? =
        prefs().getString(LaunchpadPrefs.PREF_PAIR_PARENT_ID, null)

    /**
     * Ensure a keypair exists and return the QR payload JSON (parent scans this). Regenerating
     * is allowed (e.g. re-pair); it does NOT drop an existing session unless [reset] is true.
     */
    fun getOrCreateQrPayload(reset: Boolean = false): String {
        val p = prefs()
        if (reset || p.getString(LaunchpadPrefs.PREF_PAIR_PRIVATE_KEY, null) == null) {
            val keyPair = proto.newKeyPair()
            val nonce = proto.newNonceHex()
            p.edit()
                .putString(LaunchpadPrefs.PREF_PAIR_PRIVATE_KEY, proto.encodeKey(keyPair.private.encoded))
                .putString(LaunchpadPrefs.PREF_PAIR_PUBLIC_KEY, proto.encodeKey(keyPair.public.encoded))
                .putString(LaunchpadPrefs.PREF_PAIR_NONCE, nonce)
                .apply()
            if (reset) {
                p.edit()
                    .remove(LaunchpadPrefs.PREF_PAIR_SESSION_KEY)
                    .remove(LaunchpadPrefs.PREF_PAIR_PARENT_ID)
                    .apply()
            }
        }
        val payload = QrPayloadJson(
            version = QrPairingProtocol.PROTOCOL_VERSION,
            identity = launcherIdentity,
            publicKeyB64 = p.getString(LaunchpadPrefs.PREF_PAIR_PUBLIC_KEY, "").orEmpty(),
            nonceHex = p.getString(LaunchpadPrefs.PREF_PAIR_NONCE, "").orEmpty(),
            timestamp = System.currentTimeMillis()
        )
        return payload.toJson()
    }

    /**
     * Receive the parent's session key, RSA-encrypted with our public key. Decrypts with our
     * stored private key and persists the AES session key. Returns true on success.
     */
    fun receiveSessionKey(encryptedSessionKeyB64: String, parentId: String? = null): Boolean {
        return try {
            val privB64 = prefs().getString(LaunchpadPrefs.PREF_PAIR_PRIVATE_KEY, null) ?: return false
            val privateKey = proto.decodePrivateKey(privB64)
            val sessionKeyBytes = proto.decryptWithRsa(proto.decodeKey(encryptedSessionKeyB64), privateKey)
            prefs().edit()
                .putString(LaunchpadPrefs.PREF_PAIR_SESSION_KEY, proto.encodeKey(sessionKeyBytes))
                .putString(LaunchpadPrefs.PREF_PAIR_PARENT_ID, parentId ?: "parent")
                .apply()
            Log.d(tag, "Session key received and stored")
            true
        } catch (e: Exception) {
            Log.e(tag, "receiveSessionKey failed", e)
            false
        }
    }

    /** Decrypt an AES-GCM command payload using the stored session key, or null if not paired. */
    fun decryptCommand(encryptedCommandB64: String): String? {
        val sessionB64 = prefs().getString(LaunchpadPrefs.PREF_PAIR_SESSION_KEY, null) ?: return null
        val key = proto.aesKeyFromBytes(proto.decodeKey(sessionB64))
        return proto.decryptCommand(encryptedCommandB64, key)
    }

    fun unpair() {
        prefs().edit()
            .remove(LaunchpadPrefs.PREF_PAIR_SESSION_KEY)
            .remove(LaunchpadPrefs.PREF_PAIR_PARENT_ID)
            .apply()
    }
}
