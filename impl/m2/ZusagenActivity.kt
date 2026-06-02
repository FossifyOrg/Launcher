// File: app/src/main/java/org/fossify/home/activities/ZusagenActivity.kt
// M2: Zusagen (promises) UI for parents and child

package org.fossify.home.activities

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.fossify.home.databases.AppsDatabase
import org.fossify.launchpad.models.ZusageManager
import org.fossify.home.databases.entities.Zusage as RoomZusage

/**
 * ZusagenActivity: Manage family promises.
 *
 * Parent view:
 * - Create new zusage
 * - Review pending zusagen (awaiting 24h auto-approval)
 * - Approve or reject zusagen
 * - View fulfilled zusagen
 *
 * Child view:
 * - See active zusagen (promises to look forward to)
 * - See fulfilled zusagen (what was kept)
 * - No editing (read-only for child)
 */
class ZusagenActivity : AppCompatActivity() {
    private val tag = "ZusagenActivity"

    private lateinit var database: AppsDatabase
    private lateinit var manager: ZusageManager
    private var isParentMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(android.R.layout.simple_list_item_1) // Placeholder

        database = AppsDatabase.getInstance(this)
        manager = ZusageManager()
        isParentMode = intent.getBooleanExtra("isParentMode", false)

        if (isParentMode) {
            showParentView()
        } else {
            showChildView()
        }
    }

    /**
     * Parent view: Create, review, approve, reject zusagen.
     */
    private fun showParentView() {
        Log.d(tag, "Showing parent zusagen view")

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Title
        container.addView(TextView(this).apply {
            text = "Zusagen verwalten"
            textSize = 20f
            setPadding(0, 0, 0, 16)
        })

        // Create new zusage section
        container.addView(TextView(this).apply {
            text = "Neue Zusage"
            textSize = 16f
            isEnabled = false
            setPadding(0, 16, 0, 8)
        })

        // Zusage text input
        val zusageInput = EditText(this).apply {
            hint = "z.B. 'Nach Hausaufgaben, dann 20 Min Minecraft'"
            setPadding(8, 8, 8, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(zusageInput)

        // Condition input (optional)
        val conditionInput = EditText(this).apply {
            hint = "Bedingung (optional): z.B. 'Hausaufgaben fertig'"
            setPadding(8, 8, 8, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(conditionInput)

        // Create button
        container.addView(Button(this).apply {
            text = "Zusage erstellen"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 16) }
            setOnClickListener {
                val text = zusageInput.text.toString()
                val condition = conditionInput.text.toString().takeIf { it.isNotEmpty() }

                if (text.isEmpty()) {
                    showMessage("Zusage text erforderlich")
                    return@setOnClickListener
                }

                // Create zusage (would be saved to Room)
                val zusage = manager.createZusage(text, "Parent", condition)
                Log.d(tag, "Created zusage: ${zusage.id}")
                showMessage("Zusage erstellt: Auto-Genehmigung in 24h")
                zusageInput.text.clear()
                conditionInput.text.clear()
                refreshZusagenList(container)
            }
        })

        // Pending zusagen section
        container.addView(TextView(this).apply {
            text = "Wartende Genehmigungen (24h Auto-Genehmigung)"
            textSize = 16f
            isEnabled = false
            setPadding(0, 16, 0, 8)
        })

        val pendingContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(pendingContainer)

        // Refresh button
        container.addView(Button(this).apply {
            text = "Aktualisieren"
            setOnClickListener { refreshZusagenList(container) }
        })

        setContentView(container)
    }

    /**
     * Child view: See active and fulfilled zusagen.
     * Read-only.
     */
    private fun showChildView() {
        Log.d(tag, "Showing child zusagen view")

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Title
        container.addView(TextView(this).apply {
            text = "Mama und Papa's Zusagen"
            textSize = 20f
            setPadding(0, 0, 0, 16)
        })

        // Info text
        container.addView(TextView(this).apply {
            text = "Hier siehst du, was Mama und Papa dir versprechen. ✨"
            textSize = 14f
            setPadding(0, 0, 0, 16)
        })

        // Active zusagen (things to look forward to)
        container.addView(TextView(this).apply {
            text = "Das erwartet dich:"
            textSize = 16f
            isEnabled = false
            setPadding(0, 16, 0, 8)
        })

        // Placeholder active zusagen list
        container.addView(TextView(this).apply {
            text = "(Keine aktiven Zusagen)\n\nWenn Mama oder Papa eine Zusage machen, erscheint sie hier."
            textSize = 14f
            setPadding(8, 8, 8, 8)
        })

        // Fulfilled zusagen (things that were kept)
        container.addView(TextView(this).apply {
            text = "Das hat geklappt:"
            textSize = 16f
            isEnabled = false
            setPadding(0, 16, 0, 8)
        })

        // Placeholder fulfilled list
        container.addView(TextView(this).apply {
            text = "(Keine erfüllten Zusagen)\n\nWenn eine Zusage erfüllt ist, erscheint hier ein Häkchen."
            textSize = 14f
            setPadding(8, 8, 8, 8)
        })

        setContentView(container)
    }

    /**
     * Refresh and display zusagen list.
     */
    private fun refreshZusagenList(container: LinearLayout) {
        Log.d(tag, "Refreshing zusagen list")
        // In real implementation: Query Room database
        // Filter based on parent/child mode
        // Render list of zusagen with actions
    }

    private fun showMessage(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
