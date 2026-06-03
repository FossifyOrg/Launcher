// File: app/src/main/kotlin/org/fossify/home/activities/AppsManagementActivity.kt
// LAUNCHPAD: Whitelist app management — searchable list with checkboxes.

package org.fossify.home.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.home.databases.AllowedApp
import org.fossify.home.databases.AppsDatabase
import org.fossify.home.helpers.LaunchpadConstants

@Suppress("MagicNumber") // UI built programmatically
class AppsManagementActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var db: AppsDatabase
    private lateinit var listHolder: LinearLayout
    private lateinit var searchBox: EditText

    // packageName → (label, enabled)
    private var allApps: List<Triple<String, String, Boolean>> = emptyList()
    private var filter = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = AppsDatabase.getInstance(this)

        // Build layout programmatically — avoids any layout-id conflict risk
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val toolbar = androidx.appcompat.widget.Toolbar(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#1A1A2E"))
            title = "Apps verwalten"
            setTitleTextColor(android.graphics.Color.WHITE)
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { finish() }
            navigationIcon?.setTint(android.graphics.Color.WHITE)
        }
        root.addView(
            toolbar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        searchBox = EditText(this).apply {
            hint = "Apps suchen…"
            setPadding(32, 16, 32, 16)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) { /* no-op */ }
                override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) { /* no-op */ }
                override fun afterTextChanged(s: Editable?) { filter = s.toString().lowercase(); renderList() }
            })
        }
        root.addView(
            searchBox,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val scroll = ScrollView(this)
        listHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(listHolder)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        setSupportActionBar(toolbar)

        loadApps()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun loadApps() {
        scope.launch {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = pm.queryIntentActivities(intent, 0)
            val enabled = withContext(Dispatchers.IO) { db.allowedAppDao().getAll().map { it.packageName }.toSet() }

            allApps = resolved
                .map { ri ->
                    Triple(
                        ri.activityInfo.packageName,
                        ri.loadLabel(pm).toString(),
                        enabled.contains(ri.activityInfo.packageName)
                    )
                }
                .filter { (pkg, _, _) -> pkg != packageName }
                .distinctBy { (pkg, _, _) -> pkg }
                .sortedWith(
                    compareByDescending<Triple<String, String, Boolean>> { (_, _, on) -> on }
                        .thenBy { (_, label, _) -> label.lowercase() }
                )

            renderList()
        }
    }

    private fun renderList() {
        listHolder.removeAllViews()
        val filtered = allApps.filter { (pkg, label, _) ->
            filter.isEmpty() || label.lowercase().contains(filter) || pkg.lowercase().contains(filter)
        }
        for ((pkg, label, enabled) in filtered) {
            val row = RelativeLayout(this).apply {
                setPadding(32, 0, 32, 0)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 88.dp)
            }
            val cb = CheckBox(this).apply {
                text = label
                isChecked = enabled
                setPadding(8, 0, 8, 0)
                layoutParams = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT
                )
                setOnCheckedChangeListener { _, checked -> toggleApp(pkg, checked) }
            }
            row.addView(cb)
            listHolder.addView(row)
        }
        if (filtered.isEmpty()) {
            listHolder.addView(TextView(this).apply {
                text = if (filter.isEmpty()) "Keine Apps gefunden" else "Keine Treffer für \"$filter\""
                setPadding(32, 32, 32, 32)
            })
        }
    }

    private fun toggleApp(pkg: String, enable: Boolean) {
        scope.launch(Dispatchers.IO) {
            if (enable) {
                db.allowedAppDao().insertApp(
                    AllowedApp(packageName = pkg, category = LaunchpadConstants.CATEGORY_NEUTRAL)
                )
            } else {
                db.allowedAppDao().deleteApp(pkg)
            }
            // Update local state
            allApps = allApps.map { (p, l, e) -> Triple(p, l, if (p == pkg) enable else e) }
        }
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}
