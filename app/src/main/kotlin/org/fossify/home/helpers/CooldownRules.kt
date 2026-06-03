// File: app/src/main/kotlin/org/fossify/home/helpers/CooldownRules.kt
// M2: Cool-down rules configuration via JSON import
//
// NOTE (LAUNCHPAD audit fix): originally used kotlinx.serialization, which requires the
// kotlin-serialization Gradle plugin + dependency that this project does not apply. Switched
// to android's built-in org.json so no extra plugin/dependency is needed. ValidationResult is
// now imported from org.fossify.home.models.

@file:Suppress("MagicNumber", "TooGenericExceptionCaught") // time/validation bounds; fail-safe catches

package org.fossify.home.helpers

import org.fossify.home.models.ValidationResult
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cool-down Rules: JSON-configurable rule set for when/how cool-down activates.
 *
 * Example JSON:
 * {
 *   "duration": 20,
 *   "allowed_apps": ["org.librarysimplified.r2.simplereader", "com.ibis.paintx"],
 *   "start_time": "18:00",
 *   "end_time": "09:00",
 *   "weekdays_only": false
 * }
 */
@Suppress("ConstructorParameterNaming") // snake_case mirrors the JSON schema keys
data class CooldownRulesConfig(
    val duration: Int = 15, // Minutes
    val allowed_apps: List<String> = defaultAllowedApps(),
    val trigger_on_zero_balance: Boolean = true,
    val trigger_on_scheduled_time: Boolean = false,
    val scheduled_times: List<String> = emptyList(), // HH:mm format
    val weekdays_only: Boolean = false,
    val start_time: String = "00:00",
    val end_time: String = "23:59",
    val message: String = "Bildschirmpause! Dein Hirn braucht eine Pause.",
    val enabled: Boolean = true
) {
    fun isValidJson(): Boolean {
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
        val currentTime = String.format(java.util.Locale.US, "%02d:%02d", currentHour, currentMinute)

        if (currentTime < start_time || currentTime > end_time) return false

        if (weekdays_only) {
            val dayOfWeek = now.get(java.util.Calendar.DAY_OF_WEEK)
            if (dayOfWeek in listOf(java.util.Calendar.SATURDAY, java.util.Calendar.SUNDAY)) {
                return false
            }
        }

        return true
    }

    private fun isValidTimeFormat(time: String): Boolean {
        if (!time.matches(Regex("""\d{2}:\d{2}"""))) return false
        val hh = time.substring(0, 2).toIntOrNull() ?: return false
        val mm = time.substring(3, 5).toIntOrNull() ?: return false
        return hh in 0..23 && mm in 0..59
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
                val obj = JSONObject(jsonString)
                CooldownRulesConfig(
                    duration = obj.optInt("duration", 15),
                    allowed_apps = obj.optJSONArray("allowed_apps").toStringList()
                        .ifEmpty { defaultAllowedApps() },
                    trigger_on_zero_balance = obj.optBoolean("trigger_on_zero_balance", true),
                    trigger_on_scheduled_time = obj.optBoolean("trigger_on_scheduled_time", false),
                    scheduled_times = obj.optJSONArray("scheduled_times").toStringList(),
                    weekdays_only = obj.optBoolean("weekdays_only", false),
                    start_time = obj.optString("start_time", "00:00"),
                    end_time = obj.optString("end_time", "23:59"),
                    message = obj.optString("message", "Bildschirmpause! Dein Hirn braucht eine Pause."),
                    enabled = obj.optBoolean("enabled", true)
                )
            } catch (e: Exception) {
                android.util.Log.w("LAUNCHPAD", "Invalid cooldown-rules JSON; using defaults", e)
                CooldownRulesConfig()
            }
        }

        fun toJson(config: CooldownRulesConfig): String {
            val obj = JSONObject()
            obj.put("duration", config.duration)
            obj.put("allowed_apps", JSONArray(config.allowed_apps))
            obj.put("trigger_on_zero_balance", config.trigger_on_zero_balance)
            obj.put("trigger_on_scheduled_time", config.trigger_on_scheduled_time)
            obj.put("scheduled_times", JSONArray(config.scheduled_times))
            obj.put("weekdays_only", config.weekdays_only)
            obj.put("start_time", config.start_time)
            obj.put("end_time", config.end_time)
            obj.put("message", config.message)
            obj.put("enabled", config.enabled)
            return obj.toString()
        }

        fun defaultJson(): String = toJson(CooldownRulesConfig())

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            val out = ArrayList<String>(length())
            for (i in 0 until length()) {
                out.add(getString(i))
            }
            return out
        }
    }
}

/**
 * CooldownRulesValidator: Validate rules JSON before applying.
 */
class CooldownRulesValidator {
    fun validate(jsonString: String): ValidationResult {
        return try {
            val config = CooldownRulesConfig.fromJson(jsonString)

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
