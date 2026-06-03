// File: companion/app/src/main/kotlin/org/fossify/launchpad/companion/CompanionActivity.kt
// Companion app for LAUNCHPAD. Scans parent QR code, establishes encrypted session, displays
// pending approvals/commands, sends approval/denial responses.

package org.fossify.launchpad.companion

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.fossify.launchpad.companion.helpers.TestModeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@Suppress("MagicNumber")
class CompanionActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("LAUNCHPAD_COMPANION", Context.MODE_PRIVATE)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        setContentView(ScrollView(this).apply { addView(content) })

        content.addView(heading("LAUNCHPAD Companion"))
        content.addView(spacer(8))

        val launcherIp = prefs.getString("launcher_ip", null)
        if (launcherIp != null && launcherIp.isNotBlank()) {
            content.addView(status("Verbunden mit: $launcherIp"))
            loadData()
        } else {
            showPairingScreen(content)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun showPairingScreen(content: LinearLayout) {
        content.removeAllViews()
        content.addView(heading("Gerät koppeln"))
        content.addView(spacer(16))

        val scanQrLauncher = registerForActivityResult(ScanContract()) { result ->
            if (result.contents != null) {
                handleQrResult(result.contents)
            }
        }

        content.addView(button("QR-Code scannen") {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                scanQrLauncher.launch(ScanOptions())
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
            }
        })

        content.addView(button("IP manuell eingeben") { promptForIpFallback() })

        // Test Mode button (DEBUG only) — same-device testing
        if (BuildConfig.DEBUG) {
            content.addView(button("🧪 Test auf diesem Gerät") {
                scope.launch {
                    activateTestMode()
                }
            })
        }

        content.addView(spacer(32))
        content.addView(TextView(this).apply {
            text = "Tipp: Titel lang gedrückt halten → Demo-Modus (kein Gerät nötig)"
            textSize = 12f
            setTextColor(Color.GRAY)
        })
    }

    /**
     * Handle QR result: extract IP and connect to it.
     * In test mode with DEBUG build, defaults to localhost if no IP in QR.
     */
    private fun handleQrResult(qrContent: String) {
        try {
            val json = JSONObject(qrContent)
            val identity = json.optString("identity", "")
            val ip = json.optString("ip", "").takeIf { it.isNotBlank() }
                ?: if (BuildConfig.DEBUG) "localhost" else null
            when {
                identity != "launchpad" -> toast("Kein LAUNCHPAD-QR-Code")
                ip == null -> {
                    // Older QR without embedded IP — fall back to IP dialog
                    toast("QR enthält keine IP — bitte manuell eingeben")
                    promptForIpFallback()
                }
                else -> {
                    prefs.edit().putString("launcher_ip", ip).apply()
                    toast("Verbunden mit $ip ✓")
                    loadData()
                }
            }
        } catch (e: Exception) {
            toast("QR-Code konnte nicht gelesen werden: ${e.message?.take(40)}")
        }
    }

    /**
     * Test Mode: read QR payload from cache file (same-device testing).
     * Connects to localhost:7391 instead of scanning real QR.
     * Also generates AES session key, encrypts with public key from QR, writes back to cache.
     */
    private suspend fun activateTestMode() {
        val testQrJson = withContext(Dispatchers.IO) {
            try {
                // Read test QR from main app's cache
                TestModeManager.readTestQrPayload(this@CompanionActivity)
            } catch (e: Exception) {
                Log.e("TestMode", "Failed to read test QR", e)
                null
            }
        }

        if (testQrJson == null) {
            toast("Test-QR nicht gefunden.\nMain-App: Test-Modus aktivieren")
            return
        }

        try {
            // Parse QR to extract public key and identity
            val qrJson = JSONObject(testQrJson)
            val publicKeyB64 = qrJson.optString("publicKeyB64", "")

            if (publicKeyB64.isEmpty()) {
                toast("Test QR enthält keinen Public Key")
                return
            }

            // Generate random AES-256 session key (32 bytes)
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256, SecureRandom())
            val sessionKey = keyGen.generateKey()
            val sessionKeyBytes = sessionKey.encoded

            // Encrypt session key with public key from QR (RSA/ECB/PKCS1Padding)
            val publicKeyBytes = Base64.getDecoder().decode(publicKeyB64)
            val keySpec = X509EncodedKeySpec(publicKeyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey: PublicKey = keyFactory.generatePublic(keySpec)

            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val encryptedSessionKey = cipher.doFinal(sessionKeyBytes)
            val encryptedSessionKeyBase64 = Base64.getEncoder().encodeToString(encryptedSessionKey)

            // Write encrypted session key to cache for Main App to read and decrypt
            withContext(Dispatchers.IO) {
                TestModeManager.writeTestSessionKey(this@CompanionActivity, encryptedSessionKeyBase64)
            }

            // Now proceed with normal QR handling (connects to localhost)
            handleQrResult(testQrJson)
            toast("🧪 Test-Modus: Session Key verschlüsselt\nlokale Verbindung zu localhost:7391")

        } catch (e: Exception) {
            Log.e("TestMode", "Session key encryption failed", e)
            toast("Test QR ungültig: ${e.message?.take(40)}")
        }
    }

    // ─── IP fallback dialog ────────────────────────────────────────────────────

    private fun promptForIpFallback() {
        val input = android.widget.EditText(this).apply {
            hint = "z.B. 192.168.1.100"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle("Gerät-IP eingeben")
            .setView(input)
            .setPositiveButton("Verbinden") { _, _ ->
                val ip = input.text.toString().trim()
                if (ip.isNotBlank()) {
                    prefs.edit().putString("launcher_ip", ip).apply()
                    toast("Verbunden mit $ip ✓")
                    loadData()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    // ─── Data loading & UI refresh ──────────────────────────────────────────

    private fun loadData() {
        scope.launch {
            val launcherIp = prefs.getString("launcher_ip", null)
            if (launcherIp == null) {
                toast("Keine Geräte-IP gespeichert")
                return@launch
            }

            val statusJson = withContext(Dispatchers.IO) {
                try {
                    fetchApi("$launcherIp/api/status")
                } catch (e: Exception) {
                    Log.e("API", "Status fetch failed", e)
                    null
                }
            }

            if (statusJson != null) {
                val content = LinearLayout(this@CompanionActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(24, 24, 24, 24)
                }
                setContentView(ScrollView(this@CompanionActivity).apply { addView(content) })

                content.addView(heading("Geräte-Status"))
                try {
                    val json = JSONObject(statusJson)
                    val limit = json.optString("limit", "Unbekannt")
                    content.addView(status("Limit: $limit"))
                } catch (e: Exception) {
                    content.addView(status("Status: OK"))
                }

                content.addView(spacer(16))
                content.addView(heading("Ausstehende Genehmigungen", 18f))

                val pendingJson = withContext(Dispatchers.IO) {
                    try {
                        fetchApi("$launcherIp/api/pending")
                    } catch (e: Exception) {
                        null
                    }
                }

                if (pendingJson != null) {
                    try {
                        val pending = JSONArray(pendingJson)
                        for (i in 0 until pending.length()) {
                            val item = pending.getJSONObject(i)
                            val id = item.optString("id")
                            val action = item.optString("action")
                            content.addView(renderPendingItem(id, action))
                        }
                    } catch (e: Exception) {
                        content.addView(status("Keine ausstehenden Genehmigungen"))
                    }
                } else {
                    content.addView(status("Keine ausstehenden Genehmigungen"))
                }

                content.addView(spacer(16))
                content.addView(button("Neu laden") { loadData() })
                content.addView(button("Zurück zur Kopplung") {
                    prefs.edit().remove("launcher_ip").apply()
                    val newContent = LinearLayout(this@CompanionActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(24, 24, 24, 24)
                    }
                    setContentView(ScrollView(this@CompanionActivity).apply { addView(newContent) })
                    showPairingScreen(newContent)
                })
            }
        }
    }

    private fun renderPendingItem(id: String, action: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#f5f5f5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 8) }

            addView(TextView(this@CompanionActivity).apply {
                text = action
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
            })

            addView(LinearLayout(this@CompanionActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 8, 0, 0) }

                addView(button("✓ Genehmigen") {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val ip = prefs.getString("launcher_ip", null)
                            if (ip != null) {
                                try {
                                    fetchApi("$ip/api/command", method = "POST", body = "{\"id\":\"$id\",\"approved\":true}")
                                } catch (e: Exception) {
                                    Log.e("API", "Approval failed", e)
                                }
                            }
                        }
                        loadData()
                    }
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply { setMargins(0, 0, 4, 0) }
                })

                addView(button("✗ Ablehnen") {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val ip = prefs.getString("launcher_ip", null)
                            if (ip != null) {
                                try {
                                    fetchApi("$ip/api/command", method = "POST", body = "{\"id\":\"$id\",\"approved\":false}")
                                } catch (e: Exception) {
                                    Log.e("API", "Denial failed", e)
                                }
                            }
                        }
                        loadData()
                    }
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply { setMargins(4, 0, 0, 0) }
                })
            })
        }
    }

    private fun fetchApi(url: String, method: String = "GET", body: String = ""): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        if (method == "POST" && body.isNotBlank()) {
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toByteArray()) }
        }

        val reader = BufferedReader(InputStreamReader(connection.inputStream))
        val response = reader.readText()
        reader.close()
        return response
    }

    // ─── UI helpers ────────────────────────────────────────────────────────

    private fun heading(text: String, size: Float = 20f) = TextView(this).apply {
        this.text = text
        textSize = size
        setTypeface(null, Typeface.BOLD)
        setPadding(0, 12, 0, 12)
    }

    private fun status(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.DKGRAY)
        setPadding(0, 8, 0, 8)
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 8, 0, 8) }
        setOnClickListener { onClick() }
    }

    private fun spacer(height: Int) = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            height
        )
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
