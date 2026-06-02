package org.fossify.home.helpers

import android.content.Context
import android.net.VpnService
import android.util.Log
import java.net.InetAddress

/**
 * DNS-SINKHOLE: Lokaler Filter für Tracking- und Werbedomain-Anfragen.
 *
 * Architektur: VPN-Interface (kein echter VPN-Server) fängt DNS-Anfragen
 * lokal ab. Geblockte Domains erhalten 0.0.0.0 als Antwort.
 *
 * Verhindert:
 *   - HyperOS/Xiaomi-Telemetrie (miui.com, data.mistat.xiaomi.com etc.)
 *   - Google Analytics, Firebase Logging
 *   - App-interne Ad-Networks (Unity Ads, AdMob, Applovin)
 *   - Facebook/Meta-Pixel
 *
 * Kein Internet-Server nötig — läuft vollständig lokal auf dem Gerät.
 *
 * Integration: NetGuard (github.com/M66B/NetGuard) als VpnService-Basis empfohlen.
 * Cursor-Prompt: "Integriere NetGuard's DnsServerPacket.java als VpnService-Subklasse.
 *                 Nutze DnsBlocker.shouldBlock() als Filter. Kein UI."
 */
object DnsBlocker {

    private const val TAG = "DnsBlocker"

    val BLOCKED_DOMAINS: Set<String> = setOf(
        // Xiaomi / HyperOS Telemetrie
        "ad.xiaomi.com",
        "data.mistat.xiaomi.com",
        "tracking.miui.com",
        "sdkconfig.ad.xiaomi.com",
        "msg.mipush.global.xiaomi.com",
        "tracking.intl.miui.com",
        "global.miui.com",
        "f.miui.com",
        "miui.com",

        // Google Ads & Analytics
        "ads.google.com",
        "analytics.google.com",
        "doubleclick.net",
        "googleadservices.com",
        "pagead2.googlesyndication.com",
        "adservice.google.com",
        "googletagmanager.com",
        "googletagservices.com",
        "app-measurement.com",
        "firebaselogging-pa.googleapis.com",

        // Firebase Crash / Analytics (NICHT firebase.google.com selbst — nötig für QR-Pairing)
        "firebase-settings.crashlytics.com",
        "crashlytics.com",
        "e.crashlytics.com",

        // Facebook / Meta
        "graph.facebook.com",
        "connect.facebook.net",
        "an.facebook.com",
        "pixel.facebook.com",

        // Ad Networks (Unity, AppLovin, etc.)
        "unityads.unity3d.com",
        "admob.com",
        "applovin.com",
        "chartboost.com",
        "vungle.com",
        "ironsource.com",
        "supersonicads.com",

        // Analytics SDKs
        "amplitude.com",
        "api.amplitude.com",
        "api.segment.io",
        "cdn.segment.com",
        "api.mixpanel.com",
        "d.appsflyer.com",
        "app.adjust.com",
        "api.branch.io",

        // Tracking / Scoring
        "b.scorecardresearch.com",
        "e.nexac.com",
        "moat.com",
    )

    /**
     * SECURITY: Prüft ob eine Domain geblockt werden soll.
     * Subdomains werden erkannt: "sub.doubleclick.net" → geblockt.
     */
    fun shouldBlock(domain: String): Boolean {
        val normalized = domain.trimEnd('.').lowercase()
        return BLOCKED_DOMAINS.any { blocked ->
            normalized == blocked || normalized.endsWith(".$blocked")
        }
    }

    /** Sinkhole-Adresse für geblockte Domains */
    fun getSinkholeAddress(): InetAddress = InetAddress.getByName("0.0.0.0")

    /** VPN-Permission prüfen — null = Permission schon vorhanden */
    fun checkPermission(context: Context) = VpnService.prepare(context)

    fun logStats(blocked: Int, total: Int) {
        if (total > 0) {
            Log.i(TAG, "DNS-Filter: $blocked/$total geblockt (${blocked * 100 / total}%)")
        }
    }
}
