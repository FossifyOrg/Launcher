// File: app/src/main/kotlin/org/fossify/home/activities/ElternModusActivity.kt
// M1/M2: Parent Mode menu and controls (wired to Room).

package org.fossify.home.activities

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.home.R
import org.fossify.home.databases.AllowedApp
import org.fossify.home.databases.AppsDatabase
import org.fossify.home.databases.CryptoCashTransaction
import org.fossify.home.helpers.CooldownRulesConfig
import org.fossify.home.helpers.CooldownRulesValidator
import org.fossify.home.helpers.KioskManager
import org.fossify.home.helpers.LaunchpadConstants
import org.fossify.home.helpers.LaunchpadPrefs
import org.fossify.home.helpers.PinGateHelper
import org.fossify.home.helpers.UsageTracker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ElternModusActivity: Parent control center.
 *
 * Wired in this pass:
 * - Live Krypto-Cash balance (CryptoCashDao)
 * - Time top-up writing an EARN transaction with a correct balanceAfter snapshot
 * - App whitelist management (allowed_apps)
 * - Transaction history (ledger audit trail)
 * - Cool-down rules JSON import + validation (persisted to prefs)
 */
class ElternModusActivity : AppCompatActivity() {
    private val tag = "ElternModusActivity"

    private lateinit var database: AppsDatabase
    private lateinit var pinGate: PinGateHelper
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var balanceView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_eltern_modus)

        database = AppsDatabase.getInstance(this)
        pinGate = PinGateHelper(this)

        if (!pinGate.isPinConfigured()) {
            showPinSetupFlow()
        } else if (pinGate.isParentModeActive()) {
            showParentModeMenu()
        } else {
            showPinVerificationFlow()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun container(): LinearLayout = findViewById(R.id.eltern_container)

    private fun heading(text: String, size: Float = 20f) = TextView(this).apply {
        this.text = text
        textSize = size
        setPadding(0, 24, 0, 16)
    }

    private fun fullWidthButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 8, 0, 8) }
        setOnClickListener { onClick() }
    }

    // ─── PIN flows ──────────────────────────────────────────────────────────────

    private fun showPinSetupFlow() {
        val c = container()
        c.removeAllViews()
        c.addView(heading("Eltern-PIN einrichten"))

        val pin1 = EditText(this).apply {
            hint = "4-stellige PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val pin2 = EditText(this).apply {
            hint = "PIN wiederholen"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        c.addView(pin1)
        c.addView(pin2)
        c.addView(fullWidthButton("PIN speichern") {
            val a = pin1.text.toString()
            val b = pin2.text.toString()
            when {
                a.length < 4 -> toast("PIN muss mindestens 4 Ziffern haben")
                a != b -> toast("PINs stimmen nicht überein")
                pinGate.setPinCode(a) -> {
                    pinGate.activateParentMode()
                    showParentModeMenu()
                }
                else -> toast("Fehler beim Speichern der PIN")
            }
        })
    }

    private fun showPinVerificationFlow() {
        val c = container()
        c.removeAllViews()
        c.addView(heading("Eltern-PIN"))

        val pin = EditText(this).apply {
            hint = "PIN eingeben"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        c.addView(pin)
        c.addView(fullWidthButton("Verifizieren") {
            if (pinGate.verifyPin(pin.text.toString())) {
                pinGate.activateParentMode(durationMinutes = 30)
                showParentModeMenu()
            } else {
                toast("PIN falsch")
                pin.text.clear()
            }
        })
    }

    // ─── Main menu ────────────────────────────────────────────────────────────────

    private fun showParentModeMenu() {
        val c = container()
        c.removeAllViews()
        c.addView(heading("Eltern-Modus"))

        balanceView = TextView(this).apply {
            textSize = 16f
            setPadding(0, 8, 0, 16)
            text = "Verfügbare Zeit: …"
        }
        c.addView(balanceView)
        refreshBalance()

        c.addView(fullWidthButton("+ Zeit hinzufügen") { showAdjustTimeDialog() })
        c.addView(fullWidthButton("Apps verwalten") { showAppManagementScreen() })
        c.addView(fullWidthButton("Transaktionen anzeigen") { showTransactionHistory() })
        c.addView(fullWidthButton("Versprechen (Zusagen)") {
            startActivity(
                Intent(this, ZusagenActivity::class.java).putExtra("isParentMode", true)
            )
        })
        c.addView(fullWidthButton("Medien-Anfragen (Doge-Coins)") {
            startActivity(
                Intent(this, DogeRequestsActivity::class.java).putExtra("isParentMode", true)
            )
        })
        c.addView(fullWidthButton("Ruhezeiten konfigurieren") { showCooldownConfig() })
        c.addView(fullWidthButton("Nutzungszugriff (Zeit-Tracking)") {
            val granted = UsageTracker.hasUsageAccess(this)
            toast(if (granted) "Nutzungszugriff ist aktiv ✓" else "Bitte LAUNCHPAD aktivieren")
            if (!granted) {
                try {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (e: Exception) {
                    toast("Einstellungen nicht verfügbar")
                }
            }
        })

        val kioskState = when {
            !KioskManager.isDeviceOwner(this) -> "nicht eingerichtet"
            KioskManager.isKioskEnabled(this) -> "AN"
            else -> "AUS"
        }
        c.addView(fullWidthButton("Kiosk-Modus (Gerätesperre): $kioskState") { toggleKiosk() })

        c.addView(fullWidthButton("Eltern-Modus beenden") {
            pinGate.deactivateParentMode()
            finish()
        })
    }

    private fun refreshBalance() {
        scope.launch {
            val balance = withContext(Dispatchers.IO) { database.cryptoCashDao().getCurrentBalance() }
            balanceView?.text = "Verfügbare Zeit: $balance Minuten"
        }
    }

    // ─── Time adjustment (EARN transaction) ─────────────────────────────────────────

    private fun showAdjustTimeDialog() {
        val minutesInput = EditText(this).apply {
            hint = "Minuten (z.B. 10)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val reasonInput = EditText(this).apply {
            hint = "Grund (z.B. Hausaufgaben)"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
            addView(minutesInput)
            addView(reasonInput)
        }

        AlertDialog.Builder(this)
            .setTitle("Zeit hinzufügen")
            .setView(box)
            .setPositiveButton("Hinzufügen") { _, _ ->
                val minutes = minutesInput.text.toString().toIntOrNull()
                val reason = reasonInput.text.toString().ifBlank { "Manuelle Anpassung" }
                if (minutes == null || minutes <= 0) {
                    toast("Bitte gültige Minutenzahl eingeben")
                    return@setPositiveButton
                }
                addEarnTransaction(minutes, reason)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun addEarnTransaction(minutes: Int, reason: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val current = database.cryptoCashDao().getCurrentBalance()
                database.cryptoCashDao().insertTransaction(
                    CryptoCashTransaction(
                        deltaMinutes = minutes,
                        type = LaunchpadConstants.TX_TYPE_EARN,
                        actor = "parent",
                        reasonType = "manual_adjustment",
                        reasonText = reason,
                        childVisibleText = "$reason +$minutes Min",
                        source = "parent_app",
                        balanceAfter = current + minutes // maintain No-Regression running sum
                    )
                )
            }
            toast("+$minutes Minuten hinzugefügt")
            refreshBalance()
        }
    }

    // ─── App whitelist management ─────────────────────────────────────────────────

    private fun showAppManagementScreen() {
        val c = container()
        c.removeAllViews()
        c.addView(heading("Apps verwalten"))
        c.addView(TextView(this).apply {
            text = "Aktivierte Apps erscheinen auf Jakes Startbildschirm."
            setPadding(0, 0, 0, 16)
        })
        c.addView(fullWidthButton("← Zurück") { showParentModeMenu() })

        val listHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(listHolder)

        scope.launch {
            val (apps, enabledPkgs) = withContext(Dispatchers.IO) {
                val pm = packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val resolved: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
                val seen = LinkedHashMap<String, String>()
                for (ri in resolved) {
                    val pkg = ri.activityInfo.packageName
                    if (pkg == packageName) continue
                    if (!seen.containsKey(pkg)) seen[pkg] = ri.loadLabel(pm).toString()
                }
                val enabled = database.allowedAppDao().getAll().map { it.packageName }.toSet()
                seen.entries.sortedBy { it.value.lowercase() } to enabled
            }

            for (entry in apps) {
                val pkg = entry.key
                val label = entry.value
                val row = CheckBox(this@ElternModusActivity).apply {
                    text = label
                    isChecked = enabledPkgs.contains(pkg)
                    setPadding(8, 12, 8, 12)
                    setOnCheckedChangeListener { _, checked -> toggleApp(pkg, checked) }
                }
                listHolder.addView(row)
            }
            if (apps.isEmpty()) {
                listHolder.addView(TextView(this@ElternModusActivity).apply {
                    text = "Keine startbaren Apps gefunden."
                })
            }
        }
    }

    private fun toggleApp(pkg: String, enable: Boolean) {
        scope.launch {
            withContext(Dispatchers.IO) {
                if (enable) {
                    database.allowedAppDao().insertApp(
                        AllowedApp(packageName = pkg, category = LaunchpadConstants.CATEGORY_NEUTRAL)
                    )
                } else {
                    database.allowedAppDao().deleteApp(pkg)
                }
            }
        }
    }

    // ─── Transaction history ────────────────────────────────────────────────────────

    private fun showTransactionHistory() {
        val c = container()
        c.removeAllViews()
        c.addView(heading("Transaktions-Verlauf"))
        c.addView(fullWidthButton("← Zurück") { showParentModeMenu() })

        val listHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(listHolder)

        scope.launch {
            val txs = withContext(Dispatchers.IO) {
                database.cryptoCashDao().getAllTransactions().reversed()
            }
            val fmt = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY)
            if (txs.isEmpty()) {
                listHolder.addView(TextView(this@ElternModusActivity).apply {
                    text = "Noch keine Transaktionen."
                    setPadding(0, 8, 0, 8)
                })
            }
            for (tx in txs) {
                val sign = if (tx.deltaMinutes >= 0) "+" else ""
                listHolder.addView(TextView(this@ElternModusActivity).apply {
                    text = "${fmt.format(Date(tx.createdAt))}  •  $sign${tx.deltaMinutes} Min  •  " +
                        "${tx.reasonText} (${tx.actor})"
                    setPadding(0, 8, 0, 8)
                })
            }
        }
    }

    // ─── Cool-down rules JSON config ─────────────────────────────────────────────────

    private fun showCooldownConfig() {
        val c = container()
        c.removeAllViews()
        c.addView(heading("Ruhezeiten (JSON)"))
        c.addView(fullWidthButton("← Zurück") { showParentModeMenu() })

        val prefs = getSharedPreferences(LaunchpadPrefs.PREFS_FILE, Context.MODE_PRIVATE)
        val existing = prefs.getString(LaunchpadPrefs.PREF_COOLDOWN_RULES_JSON, null)
            ?: CooldownRulesConfig.defaultJson()

        val jsonInput = EditText(this).apply {
            setText(existing)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 6
            setPadding(16, 16, 16, 16)
        }
        c.addView(jsonInput)

        c.addView(fullWidthButton("Speichern") {
            val json = jsonInput.text.toString()
            val result = CooldownRulesValidator().validate(json)
            if (result.isValid) {
                prefs.edit().putString(LaunchpadPrefs.PREF_COOLDOWN_RULES_JSON, json).apply()
                toast("Ruhezeiten gespeichert")
            } else {
                toast("Ungültig: ${result.error}")
            }
        })
    }

    // ─── Kiosk / Device-Owner ──────────────────────────────────────────────────────

    private fun toggleKiosk() {
        if (!KioskManager.isDeviceOwner(this)) {
            val cmd = KioskManager.deviceOwnerSetupCommand(this)
            AlertDialog.Builder(this)
                .setTitle("Kiosk-Modus benötigt Device Owner")
                .setMessage(
                    "Dieses Gerät ist nicht als Device Owner eingerichtet.\n\n" +
                        "Auf einem frisch zurückgesetzten Gerät (ohne Google-Konto) einmalig " +
                        "per ADB ausführen:\n\n$cmd\n\n" +
                        "Danach kann der Kiosk-Modus hier aktiviert werden."
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val enabling = !KioskManager.isKioskEnabled(this)
        KioskManager.setKioskEnabled(this, enabling)
        if (enabling) {
            KioskManager.applyRestrictions(this)
            toast("Kiosk-Modus aktiviert — beim Launcher-Start aktiv")
        } else {
            KioskManager.stopKiosk(this)
            toast("Kiosk-Modus deaktiviert")
        }
        showParentModeMenu()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
