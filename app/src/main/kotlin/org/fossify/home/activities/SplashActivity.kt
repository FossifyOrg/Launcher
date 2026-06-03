// File: app/src/main/kotlin/org/fossify/home/activities/SplashActivity.kt
// LAUNCHPAD: Rocket launch splash screen via Lottie, then route to setup or home.

package org.fossify.home.activities

import android.animation.Animator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieAnimationView
import org.fossify.home.R
import org.fossify.home.helpers.LaunchpadPrefs

@Suppress("MagicNumber")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-screen, no status bar
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        setContentView(R.layout.activity_splash)

        val anim = findViewById<LottieAnimationView>(R.id.rocket_animation)
        anim.addAnimatorListener(object : Animator.AnimatorListener {
            override fun onAnimationEnd(animation: Animator) {
                navigate()
            }
            override fun onAnimationStart(animation: Animator) { /* no-op */ }
            override fun onAnimationCancel(animation: Animator) { navigate() }
            override fun onAnimationRepeat(animation: Animator) { /* no-op */ }
        })

        // Safety net: if Lottie fails to fire the callback, navigate after 4s
        anim.postDelayed({ if (!isFinishing) navigate() }, 4_000L)
    }

    private fun navigate() {
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
