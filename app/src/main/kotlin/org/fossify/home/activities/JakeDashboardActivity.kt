// File: app/src/main/kotlin/org/fossify/home/activities/JakeDashboardActivity.kt
// LAUNCHPAD: Jake's view — current status, request media, see promises.

package org.fossify.home.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.home.databases.AppsDatabase
import org.fossify.home.helpers.TimeBudgetManager

@Suppress("MagicNumber", "CyclomaticComplexMethod") // UI built programmatically
class JakeDashboardActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var db: AppsDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AppsDatabase.getInstance(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(0, 0, 0, 0)
        }

        val closeBtn = TextView(this).apply {
            text = "✕ Schließen"
            textSize = 14f
            setTextColor(Color.argb(180, 255, 255, 255))
            setPadding(32, 48, 32, 16)
            setOnClickListener { finish() }
        }
        root.addView(closeBtn)

        // Big time display
        val timeView = TextView(this).apply {
            textSize = 72f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(32, 16, 32, 4)
            text = "…"
        }
        root.addView(timeView)

        val timeLabel = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.argb(180, 255, 255, 255))
            gravity = android.view.Gravity.CENTER
            text = "Minuten verfügbar"
            setPadding(32, 0, 32, 32)
        }
        root.addView(timeLabel)

        // Status message
        val statusMsg = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(32, 0, 32, 32)
        }
        root.addView(statusMsg)

        // Divider
        root.addView(android.view.View(this).apply {
            setBackgroundColor(Color.argb(50, 255, 255, 255))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        })

        // Action buttons
        fun bigButton(label: String, emoji: String, onClick: () -> Unit): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(0, 24, 0, 24)
                isClickable = true
                background = android.util.TypedValue().let { tv ->
                    theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
                    resources.getDrawable(tv.resourceId, theme)
                }
                addView(TextView(context).apply { text = emoji; textSize = 32f; gravity = android.view.Gravity.CENTER })
                addView(TextView(context).apply {
                    text = label; textSize = 12f
                    setTextColor(Color.argb(200, 255, 255, 255))
                    gravity = android.view.Gravity.CENTER; setPadding(0, 4, 0, 0)
                })
                setOnClickListener { onClick() }
            }
        }

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(bigButton("Medien\nanfragen", "🎬") {
            startActivity(Intent(this@JakeDashboardActivity, DogeRequestsActivity::class.java)
                .putExtra("isParentMode", false))
        })
        actions.addView(bigButton("Versprechen\nanzeigen", "🤝") {
            startActivity(Intent(this@JakeDashboardActivity, ZusagenActivity::class.java)
                .putExtra("isParentMode", false))
        })
        root.addView(actions)

        setContentView(root)

        // Load live data
        scope.launch {
            val budget = withContext(Dispatchers.IO) {
                TimeBudgetManager(this@JakeDashboardActivity, db).getCurrentBudget()
            }
            timeView.text = "${budget.balanceMinutes}"
            timeView.setTextColor(when {
                budget.balanceMinutes <= 0 -> Color.parseColor("#FF4444")
                budget.balanceMinutes < 10 -> Color.parseColor("#FF6B35")
                else -> Color.parseColor("#4CAF50")
            })
            statusMsg.text = when {
                budget.inCooldown -> {
                    val rem = budget.minutesUntilCooldownExpires() ?: 0
                    timeLabel.text = "Minuten Pause noch"
                    timeView.text = "$rem"
                    "😌 Bildschirmpause! Zeichnen, Lesen oder LEGO?"
                }
                budget.balanceMinutes <= 0 -> "⏰ Keine Zeit mehr. Frag Mama oder Papa."
                budget.balanceMinutes < 10 -> "⚡ Fast aufgebraucht — noch ${budget.balanceMinutes} Min!"
                else -> "👍 Viel Spaß!"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
