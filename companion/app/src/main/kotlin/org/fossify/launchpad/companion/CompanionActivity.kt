// File: companion/src/main/kotlin/org/fossify/launchpad/companion/CompanionActivity.kt
// LAUNCHPAD Companion App: parent phone app for approving Jake's requests.
//
// Connects via QR code shown in the launcher's Eltern-Modus → Kopplung screen.
// The QR encodes the launcher's LAN IP + RSA public key; the companion reads the IP
// and connects to LaunchpadServer on port 7391 (same WiFi network required).
//
// Demo mode: long-press the title bar or tap "Demo-Modus aktivieren" to test the
// full UI with fake data — no launcher or QR needed.

package org.fossify.launchpad.companion

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

@Suppress("MagicNumber", "TooManyFunctions")
class CompanionActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var prefs: SharedPreferences
    private lateinit var content: LinearLayout

    // Fake responses for demo mode
    private val demoStatus = """{"balance":45,"enforcement":true,"cooldown":false}"""
    private val demoPending = """{"doge":[{"id":"demo-1","description":"Minecraft Stream schauen (30 Min)"},{"id":"demo-2","description":"YouTube: Technik-Video (20 Min)"}],"zusagen":[{"id":"demo-3","text":"Zimmer aufräumen vor dem Abendessen"}]}"""

    private val cameraPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchQrScanner() else promptForIpFallback()
    }

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) handleQrResult(result.contents)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("companion_prefs", Context.MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }
        val toolbar = androidx.appcompat.widget.Toolbar(this).apply {
            title = "LAUNCHPAD Eltern"
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            setTitleTextColor(Color.WHITE)
            setOnLongClickListener { confirmDemoMode(); true }
        }
        root.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setSupportActionBar(toolbar)

        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val refreshBtn = Button(this).apply {
            text = "↻ Aktualisieren"
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            setTextColor(Color.WHITE)
            setOnClickListener { loadData() }
        }
        root.addView(refreshBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        setContentView(root)

        if (prefs.getString("launcher_ip", null) == null) showPairingScreen() else loadData()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ─── Pairing screen ───────────────────────────────────────────────────────

    private fun showPairingScreen() {
        content.removeAllViews()
        content.addView(spacer(32))

        content.addView(TextView(this).apply {
            text = "LAUNCHPAD Eltern verbinden"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        content.addView(spacer(12))
        content.addView(TextView(this).apply {
            text = "Öffne auf Jakes Gerät:\nEltern-Modus → Kopplung → QR-Code anzeigen.\nDann hier scannen."
            textSize = 14f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.3f)
        })
        content.addView(spacer(32))

        content.addView(Button(this).apply {
            text = "📷 QR-Code scannen"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#FF6B35"))
            setTextColor(Color.WHITE)
            setPadding(0, 20, 0, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12) }
            setOnClickListener { requestCameraAndScan() }
        })

        content.addView(Button(this).apply {
            text = "IP manuell eingeben"
            setBackgroundColor(Color.argb(50, 255, 255, 255))
            setTextColor(Color.LTGRAY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { promptForIpFallback() }
        })

        content.addView(spacer(32))
        content.addView(TextView(this).apply {
            text = "Tipp: Titel lang gedrückt halten → Demo-Modus (kein Gerät nötig)"
            textSize = 11f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        })
    }

    // ─── QR scanning ──────────────────────────────────────────────────────────

    private fun requestCameraAndScan() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchQrScanner()
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchQrScanner() {
        qrScanLauncher.launch(ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("QR-Code von LAUNCHPAD scannen")
            setBeepEnabled(true)
            setOrientationLocked(false)
        })
    }

    private fun handleQrResult(qrContent: String) {
        try {
            val json = JSONObject(qrContent)
            val identity = json.optString("identity", "")
            val ip = json.optString("ip", "")
            when {
                identity != "launchpad" -> toast("Kein LAUNCHPAD-QR-Code")
                ip.isBlank() -> {
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

    // ─── IP fallback dialog ────────────────────────────────────────────────────

    private fun promptForIpFallback() {
        val stored = prefs.getString("launcher_ip", "").takeIf { it != "DEMO" } ?: ""
        val input = EditText(this).apply {
            hint = "192.168.1.x"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(stored)
        }
        AlertDialog.Builder(this)
            .setTitle("Jake's Gerät IP")
            .setMessage("IP-Adresse von Jakes Gerät (Port 7391):")
            .setView(input)
            .setPositiveButton("Verbinden") { _, _ ->
                val ip = input.text.toString().trim()
                if (ip.isNotBlank()) {
                    prefs.edit().putString("launcher_ip", ip).apply()
                    loadData()
                }
            }
            .setCancelable(prefs.getString("launcher_ip", null) != null)
            .show()
    }

    // ─── Demo mode ────────────────────────────────────────────────────────────

    private fun confirmDemoMode() {
        AlertDialog.Builder(this)
            .setTitle("🧪 Demo-Modus")
            .setMessage("Simuliert einen verbundenen Launcher mit Fake-Daten.\nKeine echte Verbindung oder QR-Code nötig.\n\nAktivieren?")
            .setPositiveButton("Demo starten") { _, _ ->
                prefs.edit().putString("launcher_ip", "DEMO").apply()
                loadData()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun isDemoMode() = prefs.getString("launcher_ip", null) == "DEMO"

    // ─── Data loading ──────────────────────────────────────────────────────────

    private fun loadData() {
        val ip = prefs.getString("launcher_ip", null) ?: return showPairingScreen()
        content.removeAllViews()
        content.addView(loadingView(if (isDemoMode()) "Demo-Modus lädt …" else "Verbinde mit $ip:7391 …"))

        scope.launch {
            val statusResult = withContext(Dispatchers.IO) {
                if (isDemoMode()) demoStatus else apiGet("http://$ip:7391/api/status")
            }
            val pendingResult = withContext(Dispatchers.IO) {
                if (isDemoMode()) demoPending else apiGet("http://$ip:7391/api/pending")
            }

            content.removeAllViews()

            if (statusResult == null) {
                content.addView(errorView("Keine Verbindung zu $ip:7391.\nIst Jake's Gerät im gleichen WLAN?"))
                content.addView(settingsSection(ip))
                return@launch
            }

            try {
                val s = JSONObject(statusResult)
                content.addView(statusCard(
                    balance = s.optInt("balance", 0),
                    enforcement = s.optBoolean("enforcement", false),
                    cooldown = s.optBoolean("cooldown", false)
                ))
            } catch (e: Exception) { /* malformed response — skip status card */ }

            val pending = try { JSONObject(pendingResult ?: "{}") } catch (e: Exception) { JSONObject() }
            val dogeList = pending.optJSONArray("doge") ?: JSONArray()
            val zusageList = pending.optJSONArray("zusagen") ?: JSONArray()

            if (dogeList.length() == 0 && zusageList.length() == 0) {
                content.addView(emptyState())
            } else {
                if (dogeList.length() > 0) {
                    content.addView(sectionHeader("🎬 Medien-Anfragen (${dogeList.length()})"))
                    for (i in 0 until dogeList.length()) {
                        val item = dogeList.getJSONObject(i)
                        content.addView(requestCard(
                            id = item.optString("id", ""),
                            title = item.optString("description", "Anfrage"),
                            type = "doge",
                            ip = ip
                        ))
                    }
                }
                if (zusageList.length() > 0) {
                    content.addView(sectionHeader("🤝 Versprechen (${zusageList.length()})"))
                    for (i in 0 until zusageList.length()) {
                        val item = zusageList.getJSONObject(i)
                        content.addView(requestCard(
                            id = item.optString("id", ""),
                            title = item.optString("text", "Versprechen"),
                            type = "zusage",
                            ip = ip
                        ))
                    }
                }
            }
            content.addView(settingsSection(ip))
        }
    }

    // ─── Request cards ─────────────────────────────────────────────────────────

    private fun requestCard(id: String, title: String, type: String, ip: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(20, 16, 20, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 8) }
        }
        card.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 12)
        })
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(approveButton(id, type, ip))
        btnRow.addView(denyButton(id, type, ip))
        card.addView(btnRow)
        return card
    }

    private fun approveButton(id: String, type: String, ip: String) = Button(this).apply {
        text = "✓ Genehmigen"
        setBackgroundColor(Color.parseColor("#4CAF50"))
        setTextColor(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply { setMargins(0, 0, 8, 0) }
        setOnClickListener {
            isEnabled = false
            val cmd = if (type == "doge") """{"type":"approve_doge","id":"$id","minutes":20}"""
                      else """{"type":"approve_zusage","id":"$id"}"""
            sendCommand(ip, cmd)
        }
    }

    private fun denyButton(id: String, type: String, ip: String) = Button(this).apply {
        text = "✗ Ablehnen"
        setBackgroundColor(Color.parseColor("#FF4444"))
        setTextColor(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        setOnClickListener {
            isEnabled = false
            val cmd = if (type == "doge") """{"type":"reject_doge","id":"$id"}"""
                      else """{"type":"reject_zusage","id":"$id","reason":"Nicht jetzt"}"""
            sendCommand(ip, cmd)
        }
    }

    private fun sendCommand(ip: String, json: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (isDemoMode()) """{"ok":true}"""
                else apiPost("http://$ip:7391/api/command", json)
            }
            toast(if (result != null) "Gesendet ✓" else "Fehler beim Senden")
            delay(500)
            loadData()
            CompanionWidgetProvider.requestUpdate(this@CompanionActivity)
        }
    }

    // ─── HTTP helpers ──────────────────────────────────────────────────────────

    private fun apiGet(url: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 5000
            conn.connect()
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().readText() else null
        } catch (e: Exception) { null }
    }

    private fun apiPost(url: String, body: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 3000
            conn.readTimeout = 5000
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().readText() else null
        } catch (e: Exception) { null }
    }

    // ─── UI helpers ───────────────────────────────────────────────────────────

    private fun loadingView(text: String) = TextView(this).apply {
        this.text = text; textSize = 14f; setTextColor(Color.GRAY)
        setPadding(0, 32, 0, 32); gravity = Gravity.CENTER
    }

    private fun errorView(text: String) = TextView(this).apply {
        this.text = "⚠️ $text"; textSize = 14f; setTextColor(Color.parseColor("#FF6B35"))
        setPadding(0, 32, 0, 32); gravity = Gravity.CENTER
    }

    private fun emptyState() = TextView(this).apply {
        text = "✓ Keine offenen Anfragen\n\nJake hat gerade nichts angefragt."
        textSize = 15f; setTextColor(Color.parseColor("#4CAF50"))
        setPadding(0, 48, 0, 48); gravity = Gravity.CENTER
    }

    private fun sectionHeader(text: String) = TextView(this).apply {
        this.text = text; textSize = 13f; setTypeface(null, Typeface.BOLD)
        setTextColor(Color.GRAY); setPadding(0, 20, 0, 4)
    }

    private fun statusCard(balance: Int, enforcement: Boolean, cooldown: Boolean): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            setPadding(20, 16, 20, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
        }
        val balColor = when {
            !enforcement -> "#AAAAAA"
            balance <= 0 -> "#FF4444"
            balance < 10 -> "#FF6B35"
            else -> "#4CAF50"
        }
        val icon = when {
            isDemoMode() -> "🧪"
            cooldown -> "⏸️"
            !enforcement -> "🔓"
            balance <= 0 -> "📵"
            else -> "⏱️"
        }
        card.addView(TextView(this).apply { text = icon; textSize = 28f; setPadding(0, 0, 16, 0) })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply {
            text = if (!enforcement) "Kein Limit" else "$balance Min verfügbar"
            textSize = 18f; setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor(balColor))
        })
        col.addView(TextView(this).apply {
            text = when {
                isDemoMode() -> "Demo-Modus (Fake-Daten)"
                enforcement -> "Kindermodus AN"
                else -> "Einrichtungsmodus"
            }
            textSize = 12f; setTextColor(Color.GRAY)
        })
        card.addView(col)
        return card
    }

    private fun settingsSection(ip: String): LinearLayout {
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 0)
        }
        section.addView(Button(this).apply {
            text = "📷 Neu koppeln (QR scannen)"
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                prefs.edit().remove("launcher_ip").apply()
                showPairingScreen()
            }
        })
        if (ip != "DEMO") {
            section.addView(Button(this).apply {
                text = "🧪 Demo-Modus aktivieren"
                setBackgroundColor(Color.TRANSPARENT)
                setTextColor(Color.DKGRAY)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener { confirmDemoMode() }
            })
        } else {
            section.addView(Button(this).apply {
                text = "🔌 Demo-Modus beenden"
                setBackgroundColor(Color.TRANSPARENT)
                setTextColor(Color.DKGRAY)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener {
                    prefs.edit().remove("launcher_ip").apply()
                    showPairingScreen()
                }
            })
        }
        return section
    }

    private fun spacer(dp: Int) = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (dp * resources.displayMetrics.density).toInt()
        )
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
