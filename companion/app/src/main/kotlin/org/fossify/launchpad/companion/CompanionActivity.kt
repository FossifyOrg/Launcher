// File: companion/src/main/kotlin/org/fossify/launchpad/companion/CompanionActivity.kt
// LAUNCHPAD Companion App: parent phone app for approving Jake's requests remotely.
//
// Connects to the launcher's local HTTP API (same WiFi network).
// The launcher runs LaunchpadServer on port 7391.
// First-time setup: enter Jake's device IP (shown in launcher Eltern-Modus → QR/Kopplung).

package org.fossify.launchpad.companion

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class CompanionActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var prefs: SharedPreferences
    private lateinit var content: LinearLayout
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("companion_prefs", Context.MODE_PRIVATE)

        // Root layout
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }
        toolbar = androidx.appcompat.widget.Toolbar(this).apply {
            title = "LAUNCHPAD Eltern"
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            setTitleTextColor(Color.WHITE)
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

        if (prefs.getString("launcher_ip", null) == null) {
            promptForIp()
        } else {
            loadData()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun promptForIp() {
        val input = EditText(this).apply {
            hint = "192.168.1.x"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(prefs.getString("launcher_ip", ""))
        }
        AlertDialog.Builder(this)
            .setTitle("Jake's Gerät IP")
            .setMessage("Welche IP hat Jakes Gerät? (Eltern-Modus → Kopplung → IP anzeigen)")
            .setView(input)
            .setPositiveButton("Verbinden") { _, _ ->
                prefs.edit().putString("launcher_ip", input.text.toString().trim()).apply()
                loadData()
            }
            .setCancelable(false)
            .show()
    }

    private fun loadData() {
        val ip = prefs.getString("launcher_ip", null) ?: return promptForIp()
        content.removeAllViews()
        content.addView(loadingView("Verbinde mit $ip:7391 …"))

        scope.launch {
            // Load status + pending requests in parallel
            val statusResult = withContext(Dispatchers.IO) { apiGet("http://$ip:7391/api/status") }
            val pendingResult = withContext(Dispatchers.IO) { apiGet("http://$ip:7391/api/pending") }

            content.removeAllViews()

            if (statusResult == null) {
                content.addView(errorView("Keine Verbindung zu $ip:7391.\nIst Jake's Gerät im gleichen WLAN?"))
                content.addView(settingsButton())
                return@launch
            }

            // Status card
            try {
                val s = JSONObject(statusResult)
                content.addView(statusCard(
                    balance = s.optInt("balance", 0),
                    enforcement = s.optBoolean("enforcement", false),
                    cooldown = s.optBoolean("cooldown", false)
                ))
            } catch (e: Exception) { /* ignore */ }

            // Pending requests
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
                            id = item.getString("id"),
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
                            id = item.getString("id"),
                            title = item.optString("text", "Versprechen"),
                            type = "zusage",
                            ip = ip
                        ))
                    }
                }
            }
            content.addView(settingsButton())
        }
    }

    private fun requestCard(id: String, title: String, type: String, ip: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(20, 16, 20, 16)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 8, 0, 8)
            layoutParams = lp
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
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, 8, 0) }
        setOnClickListener {
            isEnabled = false
            when (type) {
                "doge" -> sendCommand(ip, """{"type":"approve_doge","id":"$id","minutes":20}""")
                "zusage" -> sendCommand(ip, """{"type":"approve_zusage","id":"$id"}""")
            }
        }
    }

    private fun denyButton(id: String, type: String, ip: String) = Button(this).apply {
        text = "✗ Ablehnen"
        setBackgroundColor(Color.parseColor("#FF4444"))
        setTextColor(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        setOnClickListener {
            isEnabled = false
            when (type) {
                "doge" -> sendCommand(ip, """{"type":"reject_doge","id":"$id"}""")
                "zusage" -> sendCommand(ip, """{"type":"reject_zusage","id":"$id","reason":"Nicht jetzt"}""")
            }
        }
    }

    private fun sendCommand(ip: String, json: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                apiPost("http://$ip:7391/api/command", json)
            }
            Toast.makeText(this@CompanionActivity,
                if (result != null) "Gesendet ✓" else "Fehler beim Senden",
                Toast.LENGTH_SHORT).show()
            delay(500)
            loadData()
            // Update widget so count refreshes immediately
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
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().readText()
            else null
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
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().readText()
            else null
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
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 16); layoutParams = lp
        }
        val balColor = when { !enforcement -> "#AAAAAA"; balance <= 0 -> "#FF4444"; balance < 10 -> "#FF6B35"; else -> "#4CAF50" }
        val icon = when { cooldown -> "⏸️"; !enforcement -> "🔓"; balance <= 0 -> "📵"; else -> "⏱️" }
        card.addView(TextView(this).apply { text = icon; textSize = 28f; setPadding(0, 0, 16, 0) })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply {
            text = if (!enforcement) "Kein Limit" else "$balance Min verfügbar"
            textSize = 18f; setTypeface(null, Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor(balColor))
        })
        col.addView(TextView(this).apply {
            text = if (enforcement) "Kindermodus AN" else "Einrichtungsmodus"
            textSize = 12f; setTextColor(Color.GRAY)
        })
        card.addView(col)
        return card
    }

    private fun settingsButton() = Button(this).apply {
        text = "⚙ IP-Adresse ändern"
        setBackgroundColor(Color.TRANSPARENT)
        setTextColor(Color.GRAY)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 16, 0, 0) }
        setOnClickListener { promptForIp() }
    }
}
