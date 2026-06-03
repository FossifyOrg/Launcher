@file:Suppress("MagicNumber", "TooGenericExceptionCaught") // fail-safe catches

package org.fossify.home.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Startet Phyphox-Experimente aus den LAUNCHPAD-Assets.
 *
 * Die .phyphox-Dateien liegen in app/src/main/assets/phyphox/ und werden
 * beim ersten Aufruf in den internen Speicher kopiert (FileProvider braucht
 * eine echte Datei, keine Asset-URI).
 *
 * Phyphox-Intent: ACTION_VIEW mit MIME-Type "application/phyphox"
 * Phyphox liest die XML-Datei und startet das Experiment direkt.
 *
 * Integration in LAUNCHPAD-Grid:
 *   Phyphox-Shortcuts sind virtuelle App-Einträge mit package = "phyphox.experiment"
 *   und activityName = "<filename>" (z.B. "01_magnet_explorer.phyphox").
 *   launchApp() erkennt dieses Muster und ruft PhyphoxLaunchHelper.launch() auf.
 */
object PhyphoxLaunchHelper {

    private const val TAG = "PhyphoxLaunchHelper"
    private const val ASSET_DIR = "phyphox"
    private const val PHYPHOX_MIME = "application/phyphox"

    /**
     * Startet ein Phyphox-Experiment.
     * @param filename z.B. "01_magnet_explorer.phyphox"
     */
    fun launch(context: Context, filename: String) {
        val file = getOrCopyExperiment(context, filename)
        if (file == null) {
            Log.e(TAG, "Experiment nicht gefunden: $filename")
            showPhyphoxNotInstalledHint(context)
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, PHYPHOX_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Phyphox nicht installiert oder Fehler: ${e.message}")
            showPhyphoxNotInstalledHint(context)
        }
    }

    /** Prüft ob ein Paketname ein Phyphox-Shortcut ist */
    fun isPhyphoxExperiment(packageName: String): Boolean =
        packageName == "phyphox.experiment"

    /** Alle verfügbaren Experimente als Liste */
    @Suppress("UnusedParameter") // context kept for API symmetry
    fun availableExperiments(context: Context): List<PhyphoxExperiment> = listOf(
        PhyphoxExperiment(
            filename    = "01_magnet_explorer.phyphox",
            label       = "Magnet-Entdecker",
            emoji       = "🧲",
            description = "Miss unsichtbare Magnetfelder in µT"
        ),
        PhyphoxExperiment(
            filename    = "02_lego_spinner.phyphox",
            label       = "LEGO Spinner",
            emoji       = "🌀",
            description = "Drehgeschwindigkeit deines LEGO-Motors in RPM"
        ),
        PhyphoxExperiment(
            filename    = "03_sound_explorer.phyphox",
            label       = "Schall-Entdecker",
            emoji       = "🔊",
            description = "Wie laut ist deine Welt? Messen in dB"
        ),
    )

    private fun getOrCopyExperiment(context: Context, filename: String): File? {
        val dir = File(context.filesDir, "phyphox").also { it.mkdirs() }
        val file = File(dir, filename)

        if (file.exists() && file.length() > 0) return file

        return try {
            context.assets.open("$ASSET_DIR/$filename").use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            Log.i(TAG, "Experiment kopiert: $filename")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Asset nicht gefunden: $ASSET_DIR/$filename")
            null
        }
    }

    private fun showPhyphoxNotInstalledHint(context: Context) {
        // Einfacher Toast — Phyphox muss aus Play Store installiert sein
        android.widget.Toast.makeText(
            context,
            "Phyphox muss installiert sein. App Store öffnen?",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }

    data class PhyphoxExperiment(
        val filename: String,
        val label: String,
        val emoji: String,
        val description: String
    )
}
