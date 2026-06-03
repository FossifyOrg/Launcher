// File: app/src/main/kotlin/org/fossify/home/fragments/RulesFragment.kt
// LAUNCHPAD: Jake's rules page — shown to the LEFT of the home screen (swipe right from page 0).

package org.fossify.home.fragments

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.home.R
import org.fossify.home.databases.AppsDatabase
import org.fossify.home.helpers.CooldownRulesConfig
import org.fossify.home.helpers.LaunchpadConstants
import org.fossify.home.helpers.LaunchpadPrefs
import org.fossify.home.helpers.TimeBudgetManager

@Suppress("MagicNumber") // UI built programmatically
class RulesFragment : Fragment() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_rules, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rulesContainer = view.findViewById<LinearLayout>(R.id.rules_container)
        val balanceView = view.findViewById<TextView>(R.id.rules_balance)
        val modeView = view.findViewById<TextView>(R.id.rules_mode)

        // Build static rules from config
        val prefs = requireContext().getSharedPreferences(LaunchpadPrefs.PREFS_FILE, Context.MODE_PRIVATE)
        val cooldownJson = prefs.getString(LaunchpadPrefs.PREF_COOLDOWN_RULES_JSON, null)
        val cooldown = if (cooldownJson != null) CooldownRulesConfig.fromJson(cooldownJson)
                       else CooldownRulesConfig()

        val rules = buildList {
            add(Rule("⏱️", "Zeitlimit", "${LaunchpadConstants.DEFAULT_WEEK_CAP_MINUTES} Minuten pro Woche"))
            add(Rule("😌", "Bildschirmpause", "Nach dem Limit: ${cooldown.duration} Minuten Pause"))
            add(Rule("📱", "Apps", "Nur freigegebene Apps sind verfügbar"))
            add(Rule("🌐", "Surfen", "Nur sichere Seiten im Entdecken-Modus"))
            add(Rule("🤝", "Versprechen", "Mama und Papa halten ihre Zusagen"))
            add(Rule("🎬", "Medien", "YouTube & Co: immer erst anfragen"))
            add(Rule("❤️", "Fairness", "Jake wird geliebt. Nicht optimiert."))
        }

        rules.forEach { rule -> rulesContainer.addView(buildRuleRow(rule)) }

        // Live status
        scope.launch {
            val db = AppsDatabase.getInstance(requireContext())
            val budget = withContext(Dispatchers.IO) {
                TimeBudgetManager(requireContext(), db).getCurrentBudget()
            }
            val enforced = prefs.getBoolean(LaunchpadPrefs.PREF_ENFORCEMENT_ENABLED, false)

            balanceView.text = "${budget.balanceMinutes} Min"
            balanceView.setTextColor(when {
                budget.balanceMinutes <= 0 -> Color.parseColor("#FF4444")
                budget.balanceMinutes < 15 -> Color.parseColor("#FF6B35")
                else -> Color.parseColor("#4CAF50")
            })
            modeView.text = if (enforced) "AN" else "Einrichtung"
            modeView.setTextColor(if (enforced) Color.parseColor("#4CAF50") else Color.parseColor("#FF6B35"))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
    }

    private data class Rule(val emoji: String, val title: String, val detail: String)

    private fun buildRuleRow(rule: Rule): LinearLayout {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 12)
        }
        row.addView(TextView(requireContext()).apply {
            text = rule.emoji
            textSize = 22f
            setTypeface(null, Typeface.NORMAL)
            setPadding(0, 0, 16, 0)
            layoutParams = LinearLayout.LayoutParams(52.dp, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        val textCol = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        textCol.addView(TextView(requireContext()).apply {
            text = rule.title
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        textCol.addView(TextView(requireContext()).apply {
            text = rule.detail
            textSize = 13f
            setTextColor(Color.argb(180, 255, 255, 255))
            setPadding(0, 2, 0, 0)
        })
        row.addView(textCol)

        // Divider
        val wrapper = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
            addView(android.view.View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(Color.argb(30, 255, 255, 255))
            })
        }
        return wrapper
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}
