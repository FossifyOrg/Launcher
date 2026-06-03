// File: app/src/main/kotlin/org/fossify/home/activities/PairingActivity.kt
// M4: launcher-side pairing screen. Renders the pairing QR (parent app scans it), accepts the
// returned RSA-encrypted session key, and applies commands (encrypted via the session key, or
// plaintext for testing) through CommandProcessor. UI is built programmatically.

package org.fossify.home.activities

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.home.BuildConfig
import org.fossify.home.databases.AppsDatabase
import org.fossify.home.helpers.CommandProcessor
import org.fossify.home.helpers.PairingManager
import org.fossify.home.helpers.TestModeManager

@Suppress("MagicNumber", "TooManyFunctions") // UI built programmatically
class PairingActivity : AppCompatActivity() {
    private lateinit var database: AppsDatabase
    private lateinit var pairing: PairingManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var statusView: TextView
    private lateinit var qrView: ImageView

    companion object {
        private const val PERMISSION_REQUEST_TEST_MODE = 42
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = AppsDatabase.getInstance(this)
        pairing = PairingManager(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        setContentView(ScrollView(this).apply { addView(content) })

        content.addView(heading("Kopplung mit Eltern-Gerät"))

        // Show local IP so parent can enter it in the companion app
        val localIp = org.fossify.home.helpers.LaunchpadServer.getLocalIp(this)
        if (localIp != null) {
            content.addView(TextView(this).apply {
                text = "📡 Jakes Gerät IP: $localIp:${org.fossify.home.helpers.LaunchpadServer.PORT}"
                textSize = 15f
                setPadding(0, 0, 0, 8)
            })
        }

        statusView = TextView(this).apply { setPadding(0, 8, 0, 16) }
        content.addView(statusView)

        qrView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(qrSizePx(), qrSizePx())
        }
        content.addView(qrView)

        content.addView(button("QR neu anzeigen") { showQr(reset = false) })
        content.addView(button("Neu koppeln (Reset)") {
            pairing.unpair()
            showQr(reset = true)
            toast("Kopplung zurückgesetzt")
        })

        // LAUNCHPAD M4: Test Mode — same-device testing (always available)
        content.addView(button("🧪 Test-Modus (gleiches Gerät)") {
            // Request storage permissions before starting test mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // Permission is not granted, request it
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        PERMISSION_REQUEST_TEST_MODE
                    )
                } else {
                    // Permission already granted
                    activateTestMode()
                }
            } else {
                // For Android < 6.0, permissions are granted at install time
                activateTestMode()
            }
        })

        // Step 2: receive the parent's encrypted session key.
        content.addView(heading("Sitzungsschlüssel (vom Eltern-Gerät)", 16f))
        val sessionInput = editText("Base64 des verschlüsselten Schlüssels")
        content.addView(sessionInput)
        content.addView(button("Schlüssel empfangen") {
            val ok = pairing.receiveSessionKey(sessionInput.text.toString().trim())
            toast(if (ok) "Gekoppelt ✓" else "Schlüssel ungültig")
            refreshStatus()
        })

        // Step 3: apply commands.
        content.addView(heading("Befehl anwenden", 16f))
        val commandInput = editText("Verschlüsselter Befehl (Base64) oder Klartext-JSON").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
        }
        content.addView(commandInput)
        content.addView(button("Verschlüsselt anwenden") {
            val decrypted = pairing.decryptCommand(commandInput.text.toString().trim())
            if (decrypted == null) {
                toast("Entschlüsselung fehlgeschlagen (gekoppelt?)")
            } else {
                applyCommand(decrypted)
            }
        })
        content.addView(button("Klartext testen") {
            applyCommand(commandInput.text.toString().trim())
        })

        showQr(reset = false)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_TEST_MODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, proceed with test mode
                    activateTestMode()
                } else {
                    // Permission denied
                    toast("Speicherberechtigung erforderlich für Test-Modus")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    @Suppress("TooGenericExceptionCaught") // broad catch: intentional fail-safe on QR render
    private fun showQr(reset: Boolean) {
        val payload = pairing.getOrCreateQrPayload(reset)
        try {
            qrView.setImageBitmap(renderQr(payload, qrSizePx()))
        } catch (e: Exception) {
            android.util.Log.w("LAUNCHPAD", "QR render failed", e)
            toast("QR konnte nicht erzeugt werden")
        }
        refreshStatus()
    }

    private fun refreshStatus() {
        statusView.text = if (pairing.isPaired()) {
            "Status: gekoppelt mit ${pairing.pairedParentId() ?: "Eltern"} ✓"
        } else {
            "Status: noch nicht gekoppelt. Eltern-App den QR scannen lassen."
        }
    }

    private fun applyCommand(json: String) {
        if (json.isBlank()) {
            toast("Kein Befehl eingegeben")
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                CommandProcessor(this@PairingActivity, database, pairing.pairedParentId() ?: "parent")
                    .apply(json)
            }
            toast(result.message)
        }
    }

    /**
     * Test Mode: write QR payload to cache for Companion app.
     * Companion will read QR, encrypt session key with public key, write encrypted key back.
     * Main app will then read encrypted key and decrypt with private key stored in PairingManager.
     */
    private fun activateTestMode() {
        scope.launch {
            val payload = pairing.getOrCreateQrPayload(reset = false)
            val success = withContext(Dispatchers.IO) {
                // Write QR payload to cache for Companion to read
                TestModeManager.writeTestQrPayload(this@PairingActivity, payload)
            }

            if (success) {
                toast("🧪 Test-Modus aktiviert\nCompanion: \"Test auf diesem Gerät\" drücken")
                refreshStatus()

                // Poll for session key from Companion app (up to 10 seconds)
                withContext(Dispatchers.IO) {
                    var sessionKeyFound = false
                    repeat(51) {
                        if (!sessionKeyFound) {
                            Thread.sleep(200) // Check every 200ms
                            val encryptedKey = TestModeManager.readTestSessionKey(this@PairingActivity)
                            if (encryptedKey != null) {
                                sessionKeyFound = true
                                // Companion has written the encrypted session key
                                // Now decrypt and store it using PairingManager's standard method
                                withContext(Dispatchers.Main) {
                                    val ok = pairing.receiveSessionKey(encryptedKey)
                                    refreshStatus()
                                    if (ok) {
                                        toast("🧪 Session Key automatisch empfangen! Gekoppelt ✓")
                                    } else {
                                        toast("⚠️ Session Key Entschlüsselung fehlgeschlagen")
                                    }
                                }
                            }
                        }
                    }
                    if (!sessionKeyFound) {
                        withContext(Dispatchers.Main) {
                            toast(
                                "⚠️ Session Key nicht innerhalb von 10s erhalten.\n" +
                                    "Companion hat vermutlich noch nicht \"Test\" aktiviert."
                            )
                        }
                    }
                }
            } else {
                toast("Test-Modus Aktivierung fehlgeschlagen")
            }
        }
    }

    private fun renderQr(text: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    private fun qrSizePx(): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 240f, resources.displayMetrics).toInt()

    private fun heading(text: String, size: Float = 20f) = TextView(this).apply {
        this.text = text
        textSize = size
        setPadding(0, 24, 0, 8)
    }

    private fun editText(hintText: String) = EditText(this).apply {
        hint = hintText
        inputType = InputType.TYPE_CLASS_TEXT
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 8, 0, 8) }
        setOnClickListener { onClick() }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
