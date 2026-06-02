// File: app/src/main/kotlin/org/fossify/home/activities/ZusagenActivity.kt
// M2: Zusagen (promises) UI for parents and child — wired to Room via ZusageDao + mappers.

package org.fossify.home.activities

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.home.databases.AppsDatabase
import org.fossify.home.helpers.toEntity
import org.fossify.home.helpers.toModel
import org.fossify.home.models.Zusage
import org.fossify.home.models.ZusageManager

class ZusagenActivity : AppCompatActivity() {
    private val tag = "ZusagenActivity"

    private lateinit var database: AppsDatabase
    private val manager = ZusageManager()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isParentMode = false

    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = AppsDatabase.getInstance(this)
        isParentMode = intent.getBooleanExtra("isParentMode", false)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        setContentView(ScrollView(this).apply { addView(content) })

        if (isParentMode) showParentView() else showChildView()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun label(text: String, size: Float = 16f, topPad: Int = 24) = TextView(this).apply {
        this.text = text
        textSize = size
        setPadding(0, topPad, 0, 8)
    }

    // ─── Parent view ────────────────────────────────────────────────────────────────

    private fun showParentView() {
        content.removeAllViews()
        content.addView(label("Zusagen verwalten", size = 20f, topPad = 0))

        content.addView(label("Neue Zusage"))
        val textInput = EditText(this).apply {
            hint = "z.B. 'Nach Hausaufgaben, dann 20 Min Minecraft'"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val conditionInput = EditText(this).apply {
            hint = "Bedingung (optional): z.B. 'Hausaufgaben fertig'"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        content.addView(textInput)
        content.addView(conditionInput)

        content.addView(Button(this).apply {
            text = "Zusage erstellen"
            layoutParams = matchWidth()
            setOnClickListener {
                val text = textInput.text.toString()
                if (text.isBlank()) {
                    toast("Zusage-Text erforderlich")
                    return@setOnClickListener
                }
                val condition = conditionInput.text.toString().ifBlank { null }
                createZusage(text, condition) {
                    textInput.text.clear()
                    conditionInput.text.clear()
                }
            }
        })

        content.addView(label("Wartende Genehmigungen (24h Auto-Genehmigung)"))
        val pending = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(pending)

        refreshPending(pending)
    }

    private fun createZusage(text: String, condition: String?, onDone: () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val zusage = manager.createZusage(text, "Eltern", condition)
                database.zusageDao().insertZusage(zusage.toEntity())
            }
            toast("Zusage erstellt — Auto-Genehmigung in 24h")
            onDone()
            // Rebuild parent view so the pending list refreshes.
            showParentView()
        }
    }

    private fun refreshPending(target: LinearLayout) {
        scope.launch {
            val pending = withContext(Dispatchers.IO) {
                val all = database.zusageDao().getAllZusagen().map { it.toModel() }
                manager.getPendingZusagen(all)
            }
            target.removeAllViews()
            if (pending.isEmpty()) {
                target.addView(label("(Keine wartenden Zusagen)", size = 14f, topPad = 8))
                return@launch
            }
            for (z in pending) {
                target.addView(renderPendingRow(z))
            }
        }
    }

    private fun renderPendingRow(z: Zusage): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 12)
        }
        row.addView(TextView(this).apply {
            text = z.text + (z.condition?.let { "  (Bedingung: $it)" } ?: "")
            textSize = 15f
        })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "Genehmigen"
            setOnClickListener { decide(z, approve = true) }
        })
        buttons.addView(Button(this).apply {
            text = "Ablehnen"
            setOnClickListener { promptReject(z) }
        })
        row.addView(buttons)
        return row
    }

    private fun promptReject(z: Zusage) {
        val reasonInput = EditText(this).apply { hint = "Grund" }
        AlertDialog.Builder(this)
            .setTitle("Zusage ablehnen")
            .setView(reasonInput)
            .setPositiveButton("Ablehnen") { _, _ ->
                decide(z, approve = false, reason = reasonInput.text.toString().ifBlank { "Abgelehnt" })
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun decide(z: Zusage, approve: Boolean, reason: String = "") {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val updated = if (approve) {
                        manager.approveZusage(z, "Eltern")
                    } else {
                        manager.rejectZusage(z, "Eltern", reason)
                    }
                    database.zusageDao().updateZusage(updated.toEntity())
                }
                toast(if (approve) "Zusage genehmigt" else "Zusage abgelehnt")
            } catch (e: IllegalStateException) {
                toast("Nicht möglich: ${e.message}")
            }
            showParentView()
        }
    }

    // ─── Child view (read-only) ──────────────────────────────────────────────────────

    private fun showChildView() {
        content.removeAllViews()
        content.addView(label("Mama und Papas Zusagen", size = 20f, topPad = 0))
        content.addView(label("Hier siehst du, was Mama und Papa dir versprechen. ✨", size = 14f))

        content.addView(label("Das erwartet dich:"))
        val activeHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(activeHolder)

        content.addView(label("Das hat geklappt:"))
        val fulfilledHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(fulfilledHolder)

        scope.launch {
            val all = withContext(Dispatchers.IO) {
                database.zusageDao().getAllZusagen().map { it.toModel() }
            }
            val active = manager.getActiveZusagen(all)
            val fulfilled = all.filter { it.status == "FULFILLED" }

            if (active.isEmpty()) {
                activeHolder.addView(label("(Noch keine aktiven Zusagen)", size = 14f, topPad = 4))
            } else {
                for (z in active) activeHolder.addView(TextView(this@ZusagenActivity).apply {
                    text = "• ${z.childVisibleText}"
                    setPadding(8, 6, 8, 6)
                })
            }

            if (fulfilled.isEmpty()) {
                fulfilledHolder.addView(label("(Noch nichts erfüllt)", size = 14f, topPad = 4))
            } else {
                for (z in fulfilled) fulfilledHolder.addView(TextView(this@ZusagenActivity).apply {
                    text = "✓ ${z.text}"
                    setPadding(8, 6, 8, 6)
                })
            }
        }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { setMargins(0, 8, 0, 16) }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
