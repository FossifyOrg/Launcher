// File: app/src/main/kotlin/org/fossify/home/activities/SetupActivity.kt
// LAUNCHPAD first-run wizard: PIN → Startguthaben → Fertig.

package org.fossify.home.activities

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.widget.Button
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
import org.fossify.home.databases.AppsDatabase
import org.fossify.home.databases.CryptoCashTransaction
import org.fossify.home.helpers.LaunchpadConstants
import org.fossify.home.helpers.LaunchpadPrefs
import org.fossify.home.helpers.PinGateHelper

@Suppress("MagicNumber", "TooManyFunctions") // UI built programmatically; literals are paddings/colors/sizes
class SetupActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentStep = 1
    private var chosenBalance = 60

    // Views we need to reference across steps
    private lateinit var content: LinearLayout
    private lateinit var nextBtn: Button
    private lateinit var dot1: android.view.View
    private lateinit var dot2: android.view.View
    private lateinit var dot3: android.view.View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        content = findViewById(R.id.setup_content)
        nextBtn = findViewById(R.id.setup_next_button)
        dot1 = findViewById(R.id.step_dot_1)
        dot2 = findViewById(R.id.step_dot_2)
        dot3 = findViewById(R.id.step_dot_3)

        showStep(1)
        nextBtn.setOnClickListener { advance() }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun showStep(step: Int) {
        content.removeAllViews()
        currentStep = step
        updateDots()
        when (step) {
            1 -> buildWelcome()
            2 -> buildPin()
            3 -> buildBalance()
        }
    }

    private fun updateDots() {
        val active = Color.WHITE
        val inactive = Color.argb(85, 255, 255, 255)
        dot1.setBackgroundColor(if (currentStep >= 1) active else inactive)
        dot2.setBackgroundColor(if (currentStep >= 2) active else inactive)
        dot3.setBackgroundColor(if (currentStep >= 3) active else inactive)
    }

    // ─── Step 1: Welcome ──────────────────────────────────────────────────────

    private fun buildWelcome() {
        nextBtn.text = "Einrichten →"
        title("🚀 Willkommen bei LAUNCHPAD")
        body(
            "Ein fairer Launcher für Jake — mit Zeitlimits, Versprechen und klaren Regeln.\n\n" +
                "Drücke auf \"Einrichten\" und in 2 Minuten ist alles bereit."
        )
    }

    // ─── Step 2: PIN ──────────────────────────────────────────────────────────

    private lateinit var pinField1: EditText
    private lateinit var pinField2: EditText

    private fun buildPin() {
        nextBtn.text = "Weiter →"
        title("🔒 Eltern-PIN festlegen")
        body("Mit diesem PIN öffnest du den Eltern-Modus. Mindestens 4 Ziffern.")

        pinField1 = EditText(this).apply {
            hint = "PIN eingeben"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(150, 255, 255, 255))
            setBackgroundColor(Color.argb(50, 255, 255, 255))
            setPadding(24, 20, 24, 20)
        }
        content.addView(spacer(16))
        content.addView(pinField1)

        pinField2 = EditText(this).apply {
            hint = "PIN wiederholen"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(150, 255, 255, 255))
            setBackgroundColor(Color.argb(50, 255, 255, 255))
            setPadding(24, 20, 24, 20)
        }
        content.addView(spacer(12))
        content.addView(pinField2)
    }

    private fun validateAndSavePin(): Boolean {
        val p1 = pinField1.text.toString()
        val p2 = pinField2.text.toString()
        return when {
            p1.length < 4 -> { toast("PIN muss mindestens 4 Ziffern haben"); false }
            p1 != p2 -> { toast("PINs stimmen nicht überein"); false }
            else -> {
                PinGateHelper(this).setPinCode(p1)
                true
            }
        }
    }

    // ─── Step 3: Balance ──────────────────────────────────────────────────────

    private val balanceBtns = mutableMapOf<Int, Button>()

    private fun buildBalance() {
        nextBtn.text = "Fertig ✓"
        title("⏱️ Startguthaben für Jake")
        body("Wie viel Bildschirmzeit bekommt Jake zum Start? Du kannst das jederzeit ändern.")
        content.addView(spacer(24))

        for (minutes in listOf(30, 60, 90, 120)) {
            val btn = Button(this).apply {
                text = "$minutes Minuten"
                setTextColor(if (minutes == chosenBalance) Color.WHITE else Color.argb(200, 255, 255, 255))
                setBackgroundColor(
                    if (minutes == chosenBalance) Color.parseColor("#FF6B35")
                    else Color.argb(50, 255, 255, 255)
                )
                setPadding(0, 16, 0, 16)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 8, 0, 8) }
                setOnClickListener {
                    this@SetupActivity.chosenBalance = minutes
                    balanceBtns.forEach { (m, b) ->
                        val sel = this@SetupActivity.chosenBalance
                        b.setBackgroundColor(
                            if (m == sel) Color.parseColor("#FF6B35")
                            else Color.argb(50, 255, 255, 255)
                        )
                        b.setTextColor(if (m == sel) Color.WHITE else Color.argb(200, 255, 255, 255))
                    }
                }
            }
            balanceBtns[minutes] = btn
            content.addView(btn)
        }
    }

    private fun finishSetup() {
        scope.launch {
            withContext(Dispatchers.IO) {
                val db = AppsDatabase.getInstance(this@SetupActivity)
                db.cryptoCashDao().insertTransaction(
                    CryptoCashTransaction(
                        deltaMinutes = chosenBalance,
                        type = LaunchpadConstants.TX_TYPE_EARN,
                        actor = "setup",
                        reasonType = "initial_balance",
                        reasonText = "Startguthaben",
                        childVisibleText = "Startguthaben +$chosenBalance Min",
                        source = "setup",
                        balanceAfter = chosenBalance
                    )
                )
            }
            getSharedPreferences(LaunchpadPrefs.PREFS_FILE, Context.MODE_PRIVATE)
                .edit().putBoolean(LaunchpadPrefs.PREF_SETUP_DONE, true).apply()
            toast("Alles bereit! Kindermodus ist noch AUS — aktiviere ihn in Eltern-Modus wenn du bereit bist.")
            startActivity(
                Intent(this@SetupActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }
    }

    // ─── Navigation ───────────────────────────────────────────────────────────

    private fun advance() {
        when (currentStep) {
            1 -> showStep(2)
            2 -> if (validateAndSavePin()) showStep(3)
            3 -> finishSetup()
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun title(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 16)
        })
    }

    private fun body(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.argb(200, 255, 255, 255))
            setLineSpacing(0f, 1.4f)
        })
    }

    private fun spacer(dp: Int) = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            (dp * resources.displayMetrics.density).toInt())
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // ─── Back press: only allow on step 2+ ───────────────────────────────────

    @Deprecated("Deprecated in Java")
    @Suppress("GestureBackNavigation")
    override fun onBackPressed() {
        if (currentStep > 1) showStep(currentStep - 1) else super.onBackPressed()
    }
}
