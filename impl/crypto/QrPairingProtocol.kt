// File: shared/src/main/java/org/fossify/launchpad/crypto/QrPairingProtocol.kt
// M1: QR code pairing protocol for parent-to-launcher communication

package org.fossify.launchpad.crypto

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.random.Random

/**
 * QR Pairing Protocol: Secure parent-to-launcher setup.
 *
 * Flow:
 * 1. Parent app generates ephemeral key pair (RSA-2048)
 * 2. Parent encodes into QR: { version, parentId, publicKey, nonce, timestamp }
 * 3. Jake launcher scans QR, displays confirmation
 * 4. Launcher generates AES-256 session key
 * 5. Launcher encrypts session key with parent's public key
 * 6. Launcher stores: parent public key + persistent session key
 * 7. Future commands signed with session key + parent public key
 *
 * Security properties:
 * - No cloud/internet required (QR is local, air-gapped)
 * - Parent can't trick launcher (requires Jake to scan QR + confirm)
 * - Launcher can verify parent identity via persistent key
 * - Session key can be revoked by parent (lost phone, etc)
 */
class QrPairingProtocol {
    private val tag = "QrPairingProtocol"

    companion object {
        const val PROTOCOL_VERSION = "1"
        const val RSA_KEY_SIZE = 2048
        const val AES_KEY_SIZE = 256
        const val NONCE_SIZE_BYTES = 16
        const val GCM_TAG_SIZE_BITS = 128
        const val GCM_IV_SIZE_BYTES = 12
    }

    /**
     * Generate QR payload on parent app.
     * Returns JSON-encoded string suitable for QR encoding.
     */
    fun generateParentQrPayload(parentId: String): QrPayloadJson {
        val keyPair = generateRsaKeyPair()
        val nonce = generateNonce()
        val timestamp = System.currentTimeMillis()

        // Encode public key to Base64
        val publicKeyBytes = keyPair.public.encoded
        val publicKeyB64 = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)

        // Create payload
        val payload = QrPayloadJson(
            version = PROTOCOL_VERSION,
            parentId = parentId,
            publicKeyB64 = publicKeyB64,
            nonceHex = nonce.toHex(),
            timestamp = timestamp,
            persistent = true // Can pair multiple times
        )

        return payload
    }

    /**
     * Parse QR payload on launcher.
     * Returns the parent public key for later verification.
     */
    fun parseQrPayload(json: String): QrPayloadParsed? {
        return try {
            val payload = QrPayloadJson.fromJson(json)

            // Verify version
            if (payload.version != PROTOCOL_VERSION) {
                return null
            }

            // Decode public key
            val publicKeyBytes = Base64.decode(payload.publicKeyB64, Base64.NO_WRAP)
            val publicKey = decodePublicKey(publicKeyBytes)

            QrPayloadParsed(
                parentId = payload.parentId,
                publicKey = publicKey,
                nonce = payload.nonceHex.fromHex(),
                timestamp = payload.timestamp,
                persistent = payload.persistent
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Generate session key on launcher (after confirming pairing).
     * Encrypt with parent's public key.
     * Returns base64-encoded encrypted key.
     */
    fun generateAndEncryptSessionKey(parentPublicKey: PublicKey): String {
        val sessionKey = generateAesKey()
        val encryptedKey = encryptWithRsa(sessionKey.encoded, parentPublicKey)
        return Base64.encodeToString(encryptedKey, Base64.NO_WRAP)
    }

    /**
     * Store pairing on launcher.
     * Saves parent identity and session key for future commands.
     */
    data class StoredPairing(
        val parentId: String,
        val publicKeyB64: String, // For command verification
        val sessionKeyB64: String, // For encrypting commands
        val pairedAt: Long = System.currentTimeMillis(),
        val expiresAt: Long? = null, // Optional expiration
        val isPersistent: Boolean = true
    )

    /**
     * Sign a command for transmission to launcher.
     * Used by parent app to create command QR or via LAN.
     */
    fun signCommand(
        command: String,
        sessionKey: SecretKey
    ): String {
        val nonce = generateNonce()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_SIZE_BITS, generateGcmIv())
        cipher.init(Cipher.ENCRYPT_MODE, sessionKey, spec)

        val ciphertext = cipher.doFinal(command.toByteArray())
        val iv = cipher.iv

        // Return: iv||ciphertext (both base64)
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    /**
     * Verify and decrypt command on launcher.
     * Returns decrypted command or null if verification fails.
     */
    fun verifyAndDecryptCommand(
        encryptedCommandB64: String,
        sessionKey: SecretKey
    ): String? {
        return try {
            val encryptedData = Base64.decode(encryptedCommandB64, Base64.NO_WRAP)

            // Split iv and ciphertext
            val iv = encryptedData.sliceArray(0 until GCM_IV_SIZE_BYTES)
            val ciphertext = encryptedData.sliceArray(GCM_IV_SIZE_BYTES until encryptedData.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_SIZE_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, sessionKey, spec)

            val plaintext = cipher.doFinal(ciphertext)
            String(plaintext)
        } catch (e: Exception) {
            null // Decryption/verification failed
        }
    }

    // --- Helper methods ---

    private fun generateRsaKeyPair() = KeyPairGenerator.getInstance("RSA").apply {
        initialize(RSA_KEY_SIZE)
    }.generateKeyPair()

    private fun generateAesKey(): SecretKey {
        val kg = KeyGenerator.getInstance("AES")
        kg.init(AES_KEY_SIZE)
        return kg.generateKey()
    }

    private fun generateNonce(): ByteArray {
        val nonce = ByteArray(NONCE_SIZE_BYTES)
        SecureRandom().nextBytes(nonce)
        return nonce
    }

    private fun generateGcmIv(): ByteArray {
        val iv = ByteArray(GCM_IV_SIZE_BYTES)
        SecureRandom().nextBytes(iv)
        return iv
    }

    private fun encryptWithRsa(data: ByteArray, publicKey: PublicKey): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(data)
    }

    private fun decodePublicKey(keyBytes: ByteArray): PublicKey {
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(keySpec)
    }

    // Base64 and Hex helpers
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    private fun String.fromHex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

/**
 * JSON payloads for QR encoding/decoding.
 */
data class QrPayloadJson(
    val version: String,
    val parentId: String,
    val publicKeyB64: String, // Base64-encoded RSA public key
    val nonceHex: String, // 16 bytes in hex
    val timestamp: Long,
    val persistent: Boolean = true
) {
    fun toJson(): String {
        // TODO: Use kotlinx.serialization or gson
        return """{"version":"$version","parentId":"$parentId","publicKeyB64":"$publicKeyB64","nonceHex":"$nonceHex","timestamp":$timestamp,"persistent":$persistent}"""
    }

    companion object {
        fun fromJson(json: String): QrPayloadJson {
            // TODO: Use kotlinx.serialization or gson
            // For now, simple parsing
            return QrPayloadJson("1", "parent", "", "", 0)
        }
    }
}

data class QrPayloadParsed(
    val parentId: String,
    val publicKey: java.security.PublicKey,
    val nonce: ByteArray,
    val timestamp: Long,
    val persistent: Boolean
)

/**
 * Command sent via QR or LAN after pairing.
 * Encrypted with session key.
 */
data class PairedCommand(
    val commandId: String,
    val type: String, // adjust_time, toggle_app, etc
    val payload: Map<String, Any>,
    val reasonType: String,
    val reasonText: String,
    val childVisibleText: String,
    val createdAt: Long = System.currentTimeMillis(),
    val parentId: String
)
