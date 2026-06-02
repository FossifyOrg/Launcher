// File: app/src/main/kotlin/org/fossify/home/activities/EntdeckenActivity.kt
// LAUNCHPAD: Activity wrapper for the safe-browsing EntdeckenFragment.

package org.fossify.home.activities

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import org.fossify.home.R
import org.fossify.home.fragments.EntdeckenFragment

class EntdeckenActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_entdecken)

        val toolbar = findViewById<Toolbar>(R.id.entdecken_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "🌐 Sicheres Surfen"
            setDisplayHomeAsUpEnabled(true)
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.entdecken_container, EntdeckenFragment())
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    // Block back-press when WebView can go back
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val fragment = supportFragmentManager.findFragmentById(R.id.entdecken_container)
        if (fragment is EntdeckenFragment) {
            // Let fragment handle it (WebView back navigation)
        }
        super.onBackPressed()
    }
}
