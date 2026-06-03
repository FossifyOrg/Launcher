// File: app/src/main/kotlin/org/fossify/home/helpers/LaunchpadServer.kt
// LAUNCHPAD M4: Minimal HTTP server that the parent companion app calls over LAN.
// Runs on port 7391. No external deps — pure Java ServerSocket.
//
// Endpoints:
//   GET  /api/status     → {balance, enforcement, cooldown}
//   GET  /api/pending    → {doge:[...], zusagen:[...]}
//   POST /api/command    → apply CommandProcessor; returns {ok, message}

// HTTP status codes + broad intentional fail-safe catches.
@file:Suppress("MagicNumber", "CyclomaticComplexMethod", "NestedBlockDepth", "TooGenericExceptionCaught")

package org.fossify.home.helpers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.fossify.home.databases.AppsDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

object LaunchpadServer {
    private const val TAG = "LaunchpadServer"
    const val PORT = 7391

    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null

    @Volatile var testQrPayload: String? = null
    @Volatile var testSessionKey: String? = null

    fun start(context: Context) {
        if (running) return
        running = true
        Thread {
            try {
                serverSocket = ServerSocket(PORT)
                Log.i(TAG, "Listening on :$PORT")
                while (running) {
                    val client = serverSocket?.accept() ?: break
                    CoroutineScope(Dispatchers.IO).launch { handle(context.applicationContext, client) }
                }
            } catch (e: Exception) {
                if (running) Log.e(TAG, "Server error", e)
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
    }

    private suspend fun handle(context: Context, client: Socket) {
        try {
            client.use {
                val reader = BufferedReader(InputStreamReader(it.getInputStream()))
                val writer = PrintWriter(it.getOutputStream(), true)

                // Read request line + headers
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val path = parts[1].substringBefore("?")

                var contentLength = 0
                var line = reader.readLine()
                while (!line.isNullOrBlank()) {
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                    line = reader.readLine()
                }
                val body = if (contentLength > 0) {
                    val buf = CharArray(contentLength)
                    reader.read(buf, 0, contentLength)
                    String(buf)
                } else ""

                val (status, responseBody) = when {
                    method == "GET" && path == "/api/status" -> handleStatus(context)
                    method == "GET" && path == "/api/pending" -> handlePending(context)
                    method == "POST" && path == "/api/command" -> handleCommand(context, body)
                    path == "/api/ip" -> 200 to """{"ip":"${getLocalIp()}","port":$PORT}"""
                    method == "GET" && path == "/api/test-pair" -> {
                        val p = testQrPayload
                        if (p != null) 200 to p else 404 to """{"error":"no test payload"}"""
                    }
                    method == "POST" && path == "/api/test-pair" -> {
                        testSessionKey = body
                        // Complete pairing immediately so it does not depend on a UI poll window.
                        val ok = PairingManager(context).receiveSessionKey(body)
                        if (ok) 200 to """{"ok":true}""" else 400 to """{"error":"decrypt failed"}"""
                    }
                    else -> 404 to """{"error":"not found"}"""
                }

                writer.println("HTTP/1.1 $status OK")
                writer.println("Content-Type: application/json")
                writer.println("Access-Control-Allow-Origin: *")
                writer.println("Connection: close")
                writer.println()
                writer.print(responseBody)
                writer.flush()
            }
        } catch (e: Exception) {
            Log.w(TAG, "handle error", e)
        }
    }

    private suspend fun handleStatus(context: Context): Pair<Int, String> {
        val db = AppsDatabase.getInstance(context)
        val balance = db.cryptoCashDao().getCurrentBalance()
        val prefs = context.getSharedPreferences(LaunchpadPrefs.PREFS_FILE, Context.MODE_PRIVATE)
        val enforcement = prefs.getBoolean(LaunchpadPrefs.PREF_ENFORCEMENT_ENABLED, false)
        val cooldown = System.currentTimeMillis() < prefs.getLong(LaunchpadPrefs.PREF_COOLDOWN_UNTIL, 0L)
        return 200 to JSONObject().apply {
            put("balance", balance)
            put("enforcement", enforcement)
            put("cooldown", cooldown)
        }.toString()
    }

    private suspend fun handlePending(context: Context): Pair<Int, String> {
        val db = AppsDatabase.getInstance(context)
        val dogeRequests = db.dogeRequestDao().getPending()
        val zusagen = db.zusageDao().getZusagenByStatus("ACTIVE")
            .filter { it.decidedAt == null }

        val dogeArr = JSONArray()
        dogeRequests.forEach { r ->
            dogeArr.put(JSONObject().apply {
                put("id", r.id)
                put("description", r.contentDescription)
                put("requestedAt", r.requestedAt)
            })
        }

        val zusageArr = JSONArray()
        zusagen.forEach { z ->
            zusageArr.put(JSONObject().apply {
                put("id", z.id)
                put("text", z.text)
                put("createdAt", z.createdAt)
            })
        }

        return 200 to JSONObject().apply {
            put("doge", dogeArr)
            put("zusagen", zusageArr)
        }.toString()
    }

    private suspend fun handleCommand(context: Context, body: String): Pair<Int, String> {
        val result = CommandProcessor(context, AppsDatabase.getInstance(context)).apply(body)
        return 200 to JSONObject().apply {
            put("ok", result.ok)
            put("message", result.message)
        }.toString()
    }

    private fun getLocalIp(): String = getLocalIp(null) ?: "unknown"

    @Suppress("UnusedParameter") // kept for API symmetry with getLocalIp()
    fun getLocalIp(context: android.content.Context?): String? {
        return try {
            java.net.NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
                ?.hostAddress
        } catch (e: Exception) {
            android.util.Log.w("LAUNCHPAD", "getLocalIp failed", e)
            null
        }
    }
}
