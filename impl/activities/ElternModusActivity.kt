// File: app/src/main/java/org/fossify/home/activities/ElternModusActivity.kt
// M1: Parent Mode menu and controls

package org.fossify.home.activities

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.fossify.home.R
import org.fossify.home.databases.AppsDatabase
import org.fossify.home.helpers.PinGateHelper
import org.fossify.home.helpers.LaunchpadConstants

/**
 * ElternModusActivity: Parent control center.
 *
 * M1 Features:
 * - PIN setup/verification
 * - Mode toggle (KID ↔ PARENT)
 * - Basic time adjustment (manual Krypto-Cash top-up)
 * - View current balance
 * - App whitelist management (enable/disable apps)
 * - View recent transactions (audit trail)
 *
 * Future (M2+):
 * - Zusagen management
 * - Doge-Coin approvals
 * - QR pairing setup
 * - Detailed analytics
 */
class ElternModusActivity : AppCompatActivity() {
    private val tag = "ElternModusActivity"

    private lateinit var database: AppsDatabase
    private lateinit var pinGate: PinGateHelper

    private var isPinSetup = false
    private var isParentModeVerified = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_eltern_modus)

        database = AppsDatabase.getInstance(this)
        pinGate = PinGateHelper(this)

        isPinSetup = pinGate.isPinConfigured()
        isParentModeVerified = false

        if (!isPinSetup) {
            showPinSetupFlow()
        } else {
            showPinVerificationFlow()
        }
    }

    /**
     * First-time PIN setup for parent.
     */
    private fun showPinSetupFlow() {
        Log.d(tag, "Starting PIN setup flow")

        val container = findViewById<LinearLayout>(R.id.eltern_container)
        container.removeAllViews()

        // Title
        val title = TextView(this).apply {
            text = "Eltern-PIN einrichten"
            textSize = 20f
            setPadding(16, 16, 16, 0)
        }
        container.addView(title)

        // PIN input
        val pinInput = EditText(this).apply {
            hint = "4-stellige PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(16, 16, 16, 16)
        }
        container.addView(pinInput)

        // Confirm input
        val confirmInput = EditText(this).apply {
            hint = "PIN wiederholen"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(16, 16, 16, 16)
        }
        container.addView(confirmInput)

        // Setup button
        val setupButton = Button(this).apply {
            text = "PIN speichern"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(16, 0, 16, 16) }
            setOnClickListener {
                val pin1 = pinInput.text.toString()
                val pin2 = confirmInput.text.toString()

                when {
                    pin1.isEmpty() || pin2.isEmpty() -> {
                        showMessage("PIN erforderlich")
                    }
                    pin1 != pin2 -> {
                        showMessage("PINs stimmen nicht überein")
                    }
                    pin1.length < 4 -> {
                        showMessage("PIN muss mindestens 4 Ziffern haben")
                    }
                    else -> {
                        if (pinGate.setPinCode(pin1)) {
                            isPinSetup = true
                            pinGate.activateParentMode()
                            showParentModeMenu()
                        } else {
                            showMessage("Fehler beim Speichern der PIN")
                        }
                    }
                }
            }
        }
        container.addView(setupButton)
    }

    /**
     * PIN verification (existing PIN).
     */
    private fun showPinVerificationFlow() {
        Log.d(tag, "Starting PIN verification flow")

        val container = findViewById<LinearLayout>(R.id.eltern_container)
        container.removeAllViews()

        val title = TextView(this).apply {
            text = "Eltern-PIN"
            textSize = 20f
            setPadding(16, 16, 16, 0)
        }
        container.addView(title)

        val pinInput = EditText(this).apply {
            hint = "PIN eingeben"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(16, 16, 16, 16)
        }
        container.addView(pinInput)

        val verifyButton = Button(this).apply {
            text = "Verifizieren"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(16, 0, 16, 16) }
            setOnClickListener {
                val pin = pinInput.text.toString()
                if (pinGate.verifyPin(pin)) {
                    isParentModeVerified = true
                    pinGate.activateParentMode(durationMinutes = 30)
                    showParentModeMenu()
                } else {
                    showMessage("PIN falsch")
                    pinInput.text.clear()
                }
            }
        }
        container.addView(verifyButton)
    }

    /**
     * Main parent mode menu.
     */
    private fun showParentModeMenu() {
        Log.d(tag, "Showing parent mode menu")

        val container = findViewById<LinearLayout>(R.id.eltern_container)
        container.removeAllViews()

        val title = TextView(this).apply {
            text = "Eltern-Modus"
            textSize = 20f
            setPadding(16, 16, 16, 16)
        }
        container.addView(title)

        // Show current balance
        showCurrentBalance(container)

        // Menu buttons
        val adjustTimeButton = Button(this).apply {
            text = "+ Zeit hinzufügen"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(16, 8, 16, 8) }
            setOnClickListener { showAdjustTimeDialog() }
        }
        container.addView(adjustTimeButton)

        val manageAppsButton = Button(this).apply {
            text = "Apps verwalten"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(16, 8, 16, 8) }
            setOnClickListener { showAppManagementScreen() }
        }
        container.addView(manageAppsButton)

        val transactionsButton = Button(this).apply {
            text = "Transaktionen anzeigen"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(16, 8, 16, 8) }
            setOnClickListener { showTransactionHistory() }
        }
        container.addView(transactionsButton)

        val cooldownButton = Button(this).apply {
            text = "Ruhezeiten konfigurieren"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(16, 8, 16, 8) }
            setOnClickListener { showCooldownConfig() }
        }
        container.addView(cooldownButton)

        val exitButton = Button(this).apply {
            text = "Beenden"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(16, 8, 16, 8) }
            setOnClickListener {
                pinGate.deactivateParentMode()
                finish()
            }
        }
        container.addView(exitButton)
    }

    /**
     * Show current Krypto-Cash balance.
     */
    private fun showCurrentBalance(container: LinearLayout) {
        // TODO: Query database for latest balance
        val balanceText = TextView(this).apply {
            text = "Verfügbare Zeit: 45 Minuten"
            textSize = 16f
            setPadding(16, 16, 16, 16)
        }
        container.addView(balanceText)
    }

    /**
     * Adjust time (add Krypto-Cash).
     */
    private fun showAdjustTimeDialog() {
        Log.d(tag, "Showing time adjustment dialog")
        // TODO: Implement dialog for +5, +10, +15, +30 minutes
        // Requires reason entry (homework, chore, etc)
        // Creates EARN transaction
    }

    /**
     * Manage app whitelist.
     */
    private fun showAppManagementScreen() {
        Log.d(tag, "Showing app management screen")
        // TODO: List all installed apps with toggle for enabled/disabled
        // Modifies allowed_apps table
    }

    /**
     * Show transaction audit trail.
     */
    private fun showTransactionHistory() {
        Log.d(tag, "Showing transaction history")
        // TODO: Query crypto_cash_tx table and display in reverse chronological order
        // Shows: Date, Reason, +/- Minutes, Actor
    }

    /**
     * Configure cool-down rules (JSON import).
     */
    private fun showCooldownConfig() {
        Log.d(tag, "Showing cool-down configuration")
        // TODO: Show cool-down duration setting (default 15 min)
        // Show rules JSON import field
        // Allows importing rules like:
        // { "duration": 15, "startTime": "18:00", "allowedApps": ["audiobook_app", "drawing_app"] }
    }

    private fun showMessage(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
