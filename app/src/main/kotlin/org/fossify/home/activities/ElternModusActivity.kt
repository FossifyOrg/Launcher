// File: app/src/main/kotlin/org/fossify/home/activities/ElternModusActivity.kt
// LAUNCHPAD: Parent control centre — proper Settings-style layout.

package org.fossify.home.activities

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
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
import org.fossify.home.helpers.LaunchpadWidgetProvider
import org.fossify.home.databases.CryptoCashTransaction
import org.fossify.home.helpers.CooldownRulesConfig
import org.fossify.home.helpers.CooldownRulesValidator
import org.fossify.home.helpers.KioskManager
import org.fossify.home.helpers.LaunchpadConstants
import org.fossify.home.helpers.LaunchpadPrefs
import org.fossify.home.helpers.PairingManager
import org.fossify.home.helpers.PinGateHelper
import org.fossify.home.helpers.UsageTracker
import java.text.SimpleDateFormat
import java.util.*

@Suppress("MagicNumber", "TooManyFunctions") // UI built programmatically
class ElternModusActivity : AppCompatActivity() {

    private lateinit var db: AppsDatabase
    private lateinit var pinGate: PinGateHelper
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Dashboard views
    private lateinit var balanceBig: android.widget.TextView
    private lateinit var modeBadge: android.widget.TextView
    private lateinit var lastTx: android.widget.TextView
    private lateinit var enforcementLabel: android.widget.TextView

    // Row subtitles
    private lateinit var appsCount: android.widget.TextView
    private lateinit var zusagenCount: android.widget.TextView
    private lateinit var dogeCount: android.widget.TextView
    private lateinit var usageStatus: android.widget.TextView
    private lateinit var pairStatus: android.widget.TextView

    // Switches
    private lateinit var kindermodusSwitch: org.fossify.commons.views.MyMaterialSwitch
    private lateinit var kioskSwitch: org.fossify.commons.views.MyMaterialSwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = AppsDatabase.getInstance(this)
        pinGate = PinGateHelper(this)

        // Require PIN (or first-time setup)
        if (pinGate.isPinConfigured() && !pinGate.isParentModeActive()) {
            requestPin()
            return
        }

        initUi()
    }

    override fun onResume() {
        super.onResume()
        if (pinGate.isParentModeActive() || !pinGate.isPinConfigured()) {
            refresh()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun requestPin() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Eltern-PIN"
        }
        AlertDialog.Builder(this)
            .setTitle("Eltern-Modus")
            .setMessage("PIN eingeben:")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                if (pinGate.verifyPin(input.text.toString())) {
                    pinGate.activateParentMode(30)
                    initUi()
                    refresh()
                } else {
                    toast("Falscher PIN")
                    finish()
                }
            }
            .setNegativeButton("Abbrechen") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun initUi() {
        setContentView(R.layout.activity_eltern_modus)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.em_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply { title = "Eltern-Modus"; setDisplayHomeAsUpEnabled(true) }
        toolbar.setNavigationOnClickListener { finish() }

        // Dashboard
        balanceBig = findViewById(R.id.em_balance_big)
        modeBadge = findViewById(R.id.em_mode_badge)
        lastTx = findViewById(R.id.em_last_tx)
        enforcementLabel = findViewById(R.id.em_enforcement_label)

        // Row subtitles
        appsCount = findViewById(R.id.em_apps_count)
        zusagenCount = findViewById(R.id.em_zusagen_count)
        dogeCount = findViewById(R.id.em_doge_count)
        usageStatus = findViewById(R.id.em_usage_status)
        pairStatus = findViewById(R.id.em_pair_status)

        // Switches
        kindermodusSwitch = findViewById(R.id.em_kindermodus_switch)
        kioskSwitch = findViewById(R.id.em_kiosk_switch)

        // Wire rows. NOTE: fossify-commons' SettingsSwitchStyle sets the MyMaterialSwitch to
        // android:clickable="false" — the switch never reacts to taps itself. The surrounding
        // holder row must catch the tap and call switch.toggle(). That's why the two switch
        // rows below toggle their switch instead of being left unwired (the missing
        // em_row_kindermodus handler is what stopped Kindermodus from turning on).
        listOf<Pair<Int, () -> Unit>>(
            R.id.em_row_add_time to { showAddTimeDialog() },
            R.id.em_row_transactions to { showTransactions() },
            R.id.em_row_apps to { startActivity(Intent(this, AppsManagementActivity::class.java)) },
            R.id.em_row_zusagen to {
                startActivity(Intent(this, ZusagenActivity::class.java).putExtra("isParentMode", true))
            },
            R.id.em_row_doge to {
                startActivity(Intent(this, DogeRequestsActivity::class.java).putExtra("isParentMode", true))
            },
            R.id.em_row_cooldown_rules to { showCooldownEditor() },
            R.id.em_row_usage to { openUsageSettings() },
            R.id.em_row_kindermodus to { kindermodusSwitch.toggle() },
            R.id.em_row_kiosk to { kioskSwitch.toggle() },
            R.id.em_row_qr to { startActivity(Intent(this, PairingActivity::class.java)) },
        ).forEach { (id, action) -> findViewById<android.view.View>(id).setOnClickListener { action() } }

        // Switches
        val prefs = getSharedPreferences(LaunchpadPrefs.PREFS_FILE, Context.MODE_PRIVATE)
        kindermodusSwitch.isChecked = prefs.getBoolean(LaunchpadPrefs.PREF_ENFORCEMENT_ENABLED, false)
        kindermodusSwitch.setOnCheckedChangeListener { _, checked -> toggleKindermodus(checked) }

        kioskSwitch.isChecked = KioskManager.isKioskEnabled(this)
        kioskSwitch.setOnCheckedChangeListener { _, checked ->
            if (!KioskManager.isDeviceOwner(this)) {
                // Can't enable kiosk without device owner. Revert quietly and explain. The
                // `if (checked)` guard stops the revert below from re-triggering this dialog
                // (setting isChecked = false fires this listener again with checked = false).
                if (checked) {
                    kioskSwitch.isChecked = false
                    showKioskSetupDialog()
                }
            } else {
                KioskManager.setKioskEnabled(this, checked)
                if (checked) KioskManager.applyRestrictions(this) else KioskManager.stopKiosk(this)
            }
        }
    }

    private fun refresh() {
        if (!this::balanceBig.isInitialized) return
        scope.launch {
            val balance = withContext(Dispatchers.IO) { db.cryptoCashDao().getCurrentBalance() }
            val tx = withContext(Dispatchers.IO) { db.cryptoCashDao().getLastTransaction() }
            val appCount = withContext(Dispatchers.IO) { db.allowedAppDao().getAllEnabledApps().size }
            val zusagenPending = withContext(Dispatchers.IO) { db.zusageDao().getZusagenByStatus("ACTIVE").size }
            val dogePending = withContext(Dispatchers.IO) { db.dogeRequestDao().getPending().size }
            val paired = PairingManager(this@ElternModusActivity).isPaired()
            val usageGranted = UsageTracker.hasUsageAccess(this@ElternModusActivity)
            val enforcement = getSharedPreferences(LaunchpadPrefs.PREFS_FILE, Context.MODE_PRIVATE)
                .getBoolean(LaunchpadPrefs.PREF_ENFORCEMENT_ENABLED, false)

            balanceBig.text = "$balance Min"
            balanceBig.setTextColor(when {
                balance <= 0 -> android.graphics.Color.parseColor("#FF4444")
                balance < 15 -> android.graphics.Color.parseColor("#FF6B35")
                else -> android.graphics.Color.parseColor("#4CAF50")
            })

            modeBadge.text = if (enforcement) "AKTIV" else "SETUP"
            modeBadge.setBackgroundColor(
                android.graphics.Color.parseColor(if (enforcement) "#4CAF50" else "#FF6B35")
            )

            val fmt = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY)
            lastTx.text = tx?.let { "Letzte Transaktion: ${fmt.format(Date(it.createdAt))}" } ?: "Keine Transaktionen"
            enforcementLabel.text = if (enforcement) "Kindermodus AN" else "Kindermodus AUS"

            appsCount.text = "$appCount Apps freigegeben"
            zusagenCount.text = if (zusagenPending > 0) {
                "$zusagenPending wartende Versprechen"
            } else {
                "Keine aktiven Versprechen"
            }
            dogeCount.text = if (dogePending > 0) "$dogePending offene Anfragen" else "Keine offenen Anfragen"
            usageStatus.text = if (usageGranted) "Erteilt ✓" else "Nicht erteilt — Tippe zum Öffnen"
            pairStatus.text = if (paired) "Gekoppelt ✓" else "Nicht gekoppelt"
        }
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    private fun showAddTimeDialog() {
        val mins = EditText(this).apply {
            hint = "Minuten (z.B. 30)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val reason = EditText(this).apply {
            hint = "Grund (z.B. Hausaufgaben)"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(mins); addView(reason)
        }
        AlertDialog.Builder(this)
            .setTitle("Zeit hinzufügen")
            .setView(box)
            .setPositiveButton("Hinzufügen") { _, _ ->
                val m = mins.text.toString().toIntOrNull()
                if (m == null || m <= 0) { toast("Ungültige Minutenzahl"); return@setPositiveButton }
                val r = reason.text.toString().ifBlank { "Manuelle Anpassung" }
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val cur = db.cryptoCashDao().getCurrentBalance()
                        db.cryptoCashDao().insertTransaction(CryptoCashTransaction(
                            deltaMinutes = m, type = LaunchpadConstants.TX_TYPE_EARN,
                            actor = "parent", reasonType = "manual", reasonText = r,
                            childVisibleText = "$r +$m Min", source = "parent_app",
                            balanceAfter = cur + m
                        ))
                    }
                    toast("+$m Minuten hinzugefügt")
                    refresh()
                    LaunchpadWidgetProvider.requestUpdate(this@ElternModusActivity)
                }
            }
            .setNegativeButton("Abbrechen", null).show()
    }

    private fun showTransactions() {
        scope.launch {
            val txs = withContext(Dispatchers.IO) { db.cryptoCashDao().getAllTransactions().reversed() }
            val fmt = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY)
            val msg = if (txs.isEmpty()) "Keine Transaktionen"
            else txs.take(20).joinToString("\n") {
                val sign = if (it.deltaMinutes >= 0) "+" else ""
                "${fmt.format(Date(it.createdAt))}  $sign${it.deltaMinutes} Min — ${it.reasonText}"
            }
            AlertDialog.Builder(this@ElternModusActivity)
                .setTitle("Transaktionen")
                .setMessage(msg)
                .setPositiveButton("OK", null).show()
        }
    }

    private fun showCooldownEditor() {
        val prefs = getSharedPreferences(LaunchpadPrefs.PREFS_FILE, Context.MODE_PRIVATE)
        val json = prefs.getString(LaunchpadPrefs.PREF_COOLDOWN_RULES_JSON, null) ?: CooldownRulesConfig.defaultJson()
        val input = EditText(this).apply {
            setText(json)
            inputType = InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 5
        }
        AlertDialog.Builder(this)
            .setTitle("Ruhezeiten (JSON)")
            .setView(input)
            .setPositiveButton("Speichern") { _, _ ->
                val v = CooldownRulesValidator().validate(input.text.toString())
                if (v.isValid) {
                    prefs.edit().putString(LaunchpadPrefs.PREF_COOLDOWN_RULES_JSON, input.text.toString()).apply()
                    toast("Gespeichert")
                } else toast("Ungültig: ${v.error}")
            }
            .setNegativeButton("Abbrechen", null).show()
    }

    @Suppress("TooGenericExceptionCaught") // broad catch: intentional fail-safe opening settings
    private fun openUsageSettings() {
        if (UsageTracker.hasUsageAccess(this)) { toast("Nutzungszugriff ist bereits erteilt ✓"); return }
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (e: Exception) {
            android.util.Log.w("LAUNCHPAD", "Usage-access settings unavailable", e)
            toast("Einstellungen nicht verfügbar")
        }
    }

    private fun toggleKindermodus(enable: Boolean) {
        // Write to applicationContext prefs to match the same context MainActivity uses.
        applicationContext.getSharedPreferences(LaunchpadPrefs.PREFS_FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(LaunchpadPrefs.PREF_ENFORCEMENT_ENABLED, enable).apply()

        if (enable) {
            scope.launch {
                val count = withContext(Dispatchers.IO) {
                    db.allowedAppDao().getAllEnabledApps().size
                }
                if (count == 0) {
                    // Don't block activation — just warn. Parent can add apps next.
                    toast("Kindermodus AN ⚠️ Noch keine Apps freigegeben — unter 'Apps verwalten' Apps hinzufügen")
                } else {
                    toast("Kindermodus AN — $count Apps freigegeben")
                }
                refresh()
            }
        } else {
            toast("Kindermodus AUS — alle Apps sichtbar")
            scope.launch { refresh() }
        }
    }

    private fun showKioskSetupDialog() {
        AlertDialog.Builder(this)
            .setTitle("Device Owner benötigt")
            .setMessage(
                "Kiosk-Modus benötigt einen einmaligen ADB-Befehl:\n\n" +
                    "${KioskManager.deviceOwnerSetupCommand(this)}\n\n" +
                    "Auf einem frisch zurückgesetzten Gerät ausführen."
            )
            .setPositiveButton("OK", null).show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
