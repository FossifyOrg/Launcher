// File: shared/src/main/java/org/fossify/launchpad/config/CooldownRules.kt
// M2: Cool-down rules configuration via JSON import

package org.fossify.launchpad.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Cool-down Rules: JSON-configurable rule set for when/how cool-down activates.
 *
 * Default (M1):
 * - Activates when time budget = 0
 * - 15-minute duration
 * - Audiobooks, drawing, LEGO, reading allowed
 *
 * Customizable (M2):
 * Parents can import rules JSON like:
 * {
 *   "duration": 20,
 *   "allowed_apps": ["org.librarysimplified.r2.simplereader", "com.ibis.paintx"],
 *   "start_time": "18:00",
 *   "end_time": "09:00",
 *   "weekdays_only": false
 * }
 */
@Serializable
data class CooldownRulesConfig(
    val duration: Int = 15, // Minutes
    val allowed_apps: List<String> = defaultAllowedApps(),
    val trigger_on_zero_balance: Boolean = true,
    val trigger_on_scheduled_time: Boolean = false,
    val scheduled_times: List<String> = emptyList(), // HH:mm format
    val weekdays_only: Boolean = false,
    val start_time: String = "00:00", // Daily start
    val end_time: String = "23:59", // Daily end
    val message: String = "Bildschirmpause! Dein Hirn braucht eine Pause.",
    val enabled: Boolean = true
) {
    fun isValidJson(): Boolean {
        // Basic validation
        if (duration <= 0 || duration > 120) return false
        if (allowed_apps.isEmpty()) return false
        if (!isValidTimeFormat(start_time)) return false
        if (!isValidTimeFormat(end_time)) return false
        return true
    }

    fun isActiveNow(): Boolean {
        if (!enabled) return false

        val now = java.util.Calendar.getInstance()
        val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(java.util.Calendar.MINUTE)
        val currentTime = String.format("%02d:%02d", currentHour, currentMinute)

        // Check if current time is within active window
        if (currentTime < start_time || currentTime > end_time) return false

        // Check weekdays_only
        if (weekdays_only) {
            val dayOfWeek = now.get(java.util.Calendar.DAY_OF_WEEK)
            if (dayOfWeek in listOf(java.util.Calendar.SATURDAY, java.util.Calendar.SUNDAY)) {
                return false
            }
        }

        return true
    }

    private fun isValidTimeFormat(time: String): Boolean {
        return time.matches(Regex("""\d{2}:\d{2}"""))
    }

    companion object {
        fun defaultAllowedApps() = listOf(
            "org.librarysimplified.r2.simplereader", // Audiobooks
            "com.audible.application",
            "com.ibis.paintx", // Drawing
            "com.medibang.android.paint",
            "com.lego.common", // LEGO
            "com.amazon.kindle" // Reading
        )

        fun fromJson(jsonString: String): CooldownRulesConfig {
            return try {
                Json.decodeFromString<CooldownRulesConfig>(jsonString)
            } catch (e: Exception) {
                // Return defaults if JSON invalid
                CooldownRulesConfig()
            }
        }

        fun toJson(config: CooldownRulesConfig): String {
            return Json.encodeToString(config)
        }

        fun defaultJson(): String {
            return """
            {
              "duration": 15,
              "allowed_apps": [
                "org.librarysimplified.r2.simplereader",
                "com.ibis.paintx",
                "com.lego.common"
              ],
              "trigger_on_zero_balance": true,
              "trigger_on_scheduled_time": false,
              "scheduled_times": [],
              "weekdays_only": false,
              "start_time": "00:00",
              "end_time": "23:59",
              "message": "Bildschirmpause! Dein Hirn braucht eine Pause.",
              "enabled": true
            }
            """.trimIndent()
        }
    }
}

/**
 * CooldownRulesValidator: Validate rules JSON before applying.
 */
class CooldownRulesValidator {
    fun validate(jsonString: String): ValidationResult {
        return try {
            val config = Json.decodeFromString<CooldownRulesConfig>(jsonString)

            when {
                config.duration <= 0 -> ValidationResult(
                    isValid = false,
                    error = "Duration must be > 0 minutes"
                )
                config.duration > 120 -> ValidationResult(
                    isValid = false,
                    error = "Duration should not exceed 120 minutes"
                )
                config.allowed_apps.isEmpty() -> ValidationResult(
                    isValid = false,
                    error = "At least one allowed app required"
                )
                !config.isValidJson() -> ValidationResult(
                    isValid = false,
                    error = "Invalid configuration"
                )
                else -> ValidationResult(isValid = true)
            }
        } catch (e: Exception) {
            ValidationResult(
                isValid = false,
                error = "JSON parsing error: ${e.message}"
            )
        }
    }
}

/**
 * Example JSON imports:

{
  "duration": 20,
  "allowed_apps": ["org.librarysimplified.r2.simplereader", "com.ibis.paintx"],
  "enabled": true
}

{
  "duration": 15,
  "allowed_apps": ["com.lego.common", "com.amazon.kindle"],
  "weekdays_only": true,
  "start_time": "18:00",
  "end_time": "20:00",
  "message": "Abendliche Bildschirmpause!"
}

{
  "duration": 10,
  "allowed_apps": ["com.audible.application"],
  "trigger_on_scheduled_time": true,
  "scheduled_times": ["13:00", "18:00"],
  "message": "Zeit zum Ausruhen und zur Achtsamkeit"
}
 */
