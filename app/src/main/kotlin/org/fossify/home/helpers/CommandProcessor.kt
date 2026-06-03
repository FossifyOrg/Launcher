// File: app/src/main/kotlin/org/fossify/home/helpers/CommandProcessor.kt
// M4: transport-agnostic parent-command apply engine. Takes a decrypted command JSON and
// mutates the local state (ledger, whitelist, cool-down rules, Zusagen, Doge approvals), then
// records the command into the parent_commands audit table. Works the same whether the command
// arrived via QR pairing, LAN sync, or a manual paste.

@file:Suppress("MagicNumber", "TooGenericExceptionCaught") // fail-safe catches

package org.fossify.home.helpers

import android.content.Context
import android.util.Log
import org.fossify.home.databases.AllowedApp
import org.fossify.home.databases.AppsDatabase
import org.fossify.home.databases.CryptoCashTransaction
import org.fossify.home.databases.ParentCommand
import org.fossify.home.models.DogeManager
import org.fossify.home.models.ZusageManager
import org.json.JSONObject
import java.util.UUID

class CommandProcessor(
    private val context: Context,
    private val database: AppsDatabase,
    private val parentId: String = "parent"
) {
    private val tag = "CommandProcessor"

    data class Result(val ok: Boolean, val message: String)

    /**
     * Apply a single command. Returns a human-readable result. Never throws — failures are
     * captured in the Result and the command is still recorded (status REJECTED).
     */
    suspend fun apply(commandJson: String): Result {
        val obj = try {
            JSONObject(commandJson)
        } catch (e: Exception) {
            android.util.Log.w("LAUNCHPAD", "Invalid command JSON", e)
            return Result(false, "Ungültiges JSON")
        }
        val type = obj.optString("type", "")
        return try {
            val message = when (type) {
                "adjust_time" -> applyAdjustTime(obj)
                "toggle_app" -> applyToggleApp(obj)
                "set_cooldown_rules" -> applyCooldownRules(obj)
                "approve_zusage" -> applyApproveZusage(obj)
                "approve_doge" -> applyApproveDoge(obj)
                else -> {
                    record(type, commandJson, applied = false, note = "Unbekannter Typ")
                    return Result(false, "Unbekannter Befehl: $type")
                }
            }
            record(type, commandJson, applied = true, note = message)
            Result(true, message)
        } catch (e: Exception) {
            Log.e(tag, "apply failed for $type", e)
            record(type, commandJson, applied = false, note = e.message ?: "Fehler")
            Result(false, "Fehler: ${e.message}")
        }
    }

    private suspend fun applyAdjustTime(obj: JSONObject): String {
        val minutes = obj.getInt("minutes")
        val reason = obj.optString("reason", "Eltern-Anpassung")
        val current = database.cryptoCashDao().getCurrentBalance()
        // Preserve the No-Regression invariant: delta is the ACTUAL change, balance never < 0.
        val effectiveDelta = if (minutes >= 0) minutes else maxOf(minutes, -current)
        val newBalance = current + effectiveDelta
        val type = if (effectiveDelta >= 0) LaunchpadConstants.TX_TYPE_EARN
        else LaunchpadConstants.TX_TYPE_CORRECTION
        val sign = if (effectiveDelta >= 0) "+" else ""
        database.cryptoCashDao().insertTransaction(
            CryptoCashTransaction(
                deltaMinutes = effectiveDelta,
                type = type,
                actor = parentId,
                reasonType = "remote_adjustment",
                reasonText = reason,
                childVisibleText = "$reason $sign$effectiveDelta Min",
                source = "parent_app",
                balanceAfter = newBalance
            )
        )
        return "Zeit angepasst: $sign$effectiveDelta Min (neu: $newBalance)"
    }

    private suspend fun applyToggleApp(obj: JSONObject): String {
        val pkg = obj.getString("package")
        val enabled = obj.optBoolean("enabled", true)
        val category = obj.optString("category", LaunchpadConstants.CATEGORY_NEUTRAL)
        if (enabled) {
            database.allowedAppDao().insertApp(AllowedApp(packageName = pkg, category = category))
            return "App aktiviert: $pkg"
        }
        database.allowedAppDao().deleteApp(pkg)
        return "App deaktiviert: $pkg"
    }

    private fun applyCooldownRules(obj: JSONObject): String {
        val rules = obj.get("rules").toString() // accepts object or string
        val result = CooldownRulesValidator().validate(rules)
        if (!result.isValid) throw IllegalArgumentException(result.error ?: "Ungültige Regeln")
        context.getSharedPreferences(LaunchpadPrefs.PREFS_FILE, Context.MODE_PRIVATE)
            .edit().putString(LaunchpadPrefs.PREF_COOLDOWN_RULES_JSON, rules).apply()
        return "Ruhezeiten aktualisiert"
    }

    private suspend fun applyApproveZusage(obj: JSONObject): String {
        val id = obj.getString("id")
        val entity = database.zusageDao().getById(id)
            ?: throw IllegalArgumentException("Zusage $id nicht gefunden")
        val updated = ZusageManager().approveZusage(entity.toModel(), parentId)
        database.zusageDao().updateZusage(updated.toEntity())
        return "Zusage genehmigt: ${updated.text}"
    }

    private suspend fun applyApproveDoge(obj: JSONObject): String {
        val id = obj.getString("id")
        val minutes = obj.optInt("minutes", 20)
        val entity = database.dogeRequestDao().getById(id)
            ?: throw IllegalArgumentException("Anfrage $id nicht gefunden")
        val updated = DogeManager().approveRequest(entity.toModel(), parentId, minutes)
        database.dogeRequestDao().updateRequest(updated.toEntity())
        return "Medien-Anfrage genehmigt: $minutes Min"
    }

    private suspend fun record(type: String, payloadJson: String, applied: Boolean, note: String) {
        try {
            database.parentCommandDao().insertCommand(
                ParentCommand(
                    commandId = UUID.randomUUID().toString(),
                    actor = parentId,
                    source = "qr_pairing",
                    type = type.ifEmpty { "unknown" },
                    payloadJson = payloadJson,
                    reasonType = "remote",
                    reasonText = note,
                    childVisibleText = note,
                    status = if (applied) LaunchpadConstants.CMD_STATUS_APPLIED
                    else LaunchpadConstants.CMD_STATUS_REJECTED,
                    appliedAt = if (applied) System.currentTimeMillis() else null
                )
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to record command", e)
        }
    }
}
