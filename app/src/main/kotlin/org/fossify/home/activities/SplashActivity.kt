package org.fossify.home.activities

import android.content.Context
import android.content.Intent
import org.fossify.commons.activities.BaseSplashActivity
import org.fossify.home.helpers.LaunchpadPrefs

class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {
        val prefs = getSharedPreferences(LaunchpadPrefs.PREFS_FILE, Context.MODE_PRIVATE)
        val target = if (prefs.getBoolean(LaunchpadPrefs.PREF_SETUP_DONE, false)) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, SetupActivity::class.java)
        }
        startActivity(target)
        finish()
    }
}
