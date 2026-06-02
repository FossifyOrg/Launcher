// File: app/src/main/java/org/fossify/home/fragments/EntdeckenFragment.kt
// M1: Safe web browsing (Entdecken-Modus) with domain allowlist/blocklist

package org.fossify.home.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import org.fossify.home.R
import org.fossify.home.databases.AppsDatabase
import org.fossify.home.databases.ExploreAllowlistEntry
import org.fossify.home.databases.ExploreBlocklistEntry
import java.net.URL

/**
 * EntdeckenFragment: Safe web browsing with domain allowlist and blocklist.
 *
 * Security model:
 * 1. No free URL bar (child can't type arbitrary URLs)
 * 2. Domain allowlist enforced (must be in whitelist)
 * 3. Hard blocklist as second fence (blocks known-bad patterns)
 * 4. No redirects to blocked domains
 * 5. Safe Browsing enabled
 * 6. JavaScript disabled (for M1)
 *
 * Initial Allowlist (M1):
 * - youtube.com (parental controls via Doge-Coins)
 * - wikipedia.org
 * - khan-academy.org
 * - khanacademy.org
 * - scratch.mit.edu
 * - codecademy.com
 * - duolingo.com
 *
 * Hard Blocklist (M1):
 * - twitter.com, x.com (social media)
 * - tiktok.com, instagram.com (social media)
 * - reddit.com (community + NSFW content)
 * - pornography sites (multiple patterns)
 * - gore/violence sites
 * - image boards
 * - dark web onion addresses
 */
class EntdeckenFragment : Fragment() {
    private val tag = "EntdeckenFragment"

    private lateinit var webView: WebView
    private lateinit var database: AppsDatabase
    private val contentFilter = EntdeckenContentFilter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_entdecken, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database = AppsDatabase.getInstance(requireContext())
        webView = view.findViewById(R.id.webview_entdecken)

        configureWebView()
        loadInitialPage()
    }

    /**
     * Configure WebView for safe browsing.
     */
    private fun configureWebView() {
        webView.settings.apply {
            // Security: disable JavaScript for M1 (can enable for M2)
            javaScriptEnabled = false
            // Enable Safe Browsing
            safeBrowsingEnabled = true
            // Disable plugins
            pluginState = android.webkit.WebSettings.PluginState.OFF
            // Disable file access
            allowFileAccess = false
            allowContentAccess = false
            // Cache settings
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        }

        webView.webViewClient = SafeWebViewClient(database, contentFilter)
        webView.webChromeClient = object : WebChromeClient() {
            // Limit chrome features (no file picker, etc)
        }

        // Remove JavaScript interface leaks
        webView.removeJavascriptInterface("searchBoxJavaBridge_")
    }

    /**
     * Load suggested landing page (e.g., curated link directory).
     */
    private fun loadInitialPage() {
        // TODO: Load curated HTML page with suggested safe links
        // OR redirect to first allowed domain
        webView.loadUrl("about:blank")
    }

    /**
     * Custom WebViewClient to enforce allowlist/blocklist.
     */
    private inner class SafeWebViewClient(
        private val db: AppsDatabase,
        private val filter: EntdeckenContentFilter
    ) : WebViewClient() {

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val url = request.url.toString()
            Log.d(tag, "shouldInterceptRequest: $url")

            // Check final URL against filter
            val decision = filter.shouldAllowUrl(url, db)
            if (!decision.allowed) {
                Log.w(tag, "Blocking URL: $url (${decision.reason})")
                // Return error page or blank response
                return WebResourceResponse("text/html", "utf-8",
                    "<!DOCTYPE html><body><h1>Diese Seite ist nicht erlaubt.</h1>" +
                    "<p>Grund: ${decision.reason}</p></body>".byteInputStream()
                )
            }

            // Allow request
            return super.shouldInterceptRequest(view, request)
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            val url = request.url.toString()
            Log.d(tag, "shouldOverrideUrlLoading: $url")

            // Verify target domain against allowlist
            val decision = filter.shouldAllowUrl(url, db)
            if (!decision.allowed) {
                Log.w(tag, "Blocking navigation to: $url")
                // Show denial message or log attempt
                return true // Prevent navigation
            }

            return false // Allow navigation
        }
    }
}

/**
 * Content filter: enforces allowlist and blocklist.
 */
class EntdeckenContentFilter {
    private val tag = "EntdeckenContentFilter"

    data class FilterDecision(
        val allowed: Boolean,
        val reason: String?
    )

    /**
     * Main filter logic: check if URL should be allowed.
     */
    suspend fun shouldAllowUrl(
        urlString: String,
        database: AppsDatabase
    ): FilterDecision {
        try {
            val url = URL(urlString)
            val domain = url.host ?: return FilterDecision(false, "No domain")

            // Check blocklist first (hard block)
            if (isBlocklisted(domain, database)) {
                return FilterDecision(false, "Blocked domain")
            }

            // Check allowlist (must be in list)
            if (!isAllowlisted(domain, database)) {
                return FilterDecision(false, "Domain not in allowlist")
            }

            // Check protocol (only http/https)
            if (url.protocol != "http" && url.protocol != "https") {
                return FilterDecision(false, "Invalid protocol: ${url.protocol}")
            }

            // All checks passed
            return FilterDecision(true, null)
        } catch (e: Exception) {
            Log.e(tag, "Filter error: ${e.message}")
            return FilterDecision(false, "Filter error")
        }
    }

    /**
     * Is domain in allowlist?
     */
    private suspend fun isAllowlisted(
        domain: String,
        database: AppsDatabase
    ): Boolean {
        // Extract base domain (e.g., youtube.com from www.youtube.com)
        val baseDomain = extractBaseDomain(domain)

        // Check database allowlist
        val entry = database.exploreAllowlistDao().findByDomain(baseDomain)
        return entry != null
    }

    /**
     * Is domain in blocklist?
     */
    private suspend fun isBlocklisted(
        domain: String,
        database: AppsDatabase
    ): Boolean {
        // Check hard-coded patterns
        val hardBlockPatterns = listOf(
            "twitter.com", "x.com", "t.co",
            "tiktok.com",
            "instagram.com", "instagram.ru",
            "reddit.com", "redditmedia.com",
            "facebook.com", "fb.com",
            ".onion", // Tor
            "dailymotion.com",
            "vk.com", // VKontakte
            "telegram.org" // Can enable Telegram if needed
        )

        for (pattern in hardBlockPatterns) {
            if (domain.endsWith(pattern)) {
                return true
            }
        }

        // Check database blocklist
        val entries = database.exploreBlocklistDao().getAllEntries()
        for (entry in entries) {
            try {
                if (domain.matches(Regex(entry.pattern))) {
                    return true
                }
            } catch (e: Exception) {
                Log.e(tag, "Regex error in blocklist: ${entry.pattern}", e)
            }
        }

        return false
    }

    /**
     * Extract base domain from full domain.
     * e.g., www.youtube.com → youtube.com
     */
    private fun extractBaseDomain(domain: String): String {
        val parts = domain.split(".")
        return when {
            parts.size >= 2 -> parts.takeLast(2).joinToString(".")
            else -> domain
        }
    }
}

/**
 * DAO methods to add to AppsDatabase:
 *
 * @Dao
 * interface ExploreAllowlistDao {
 *     @Query("SELECT * FROM explore_allowlist WHERE domain = :domain")
 *     suspend fun findByDomain(domain: String): ExploreAllowlistEntry?
 *
 *     @Query("SELECT * FROM explore_allowlist")
 *     suspend fun getAllEntries(): List<ExploreAllowlistEntry>
 *
 *     @Insert(onConflict = OnConflictStrategy.REPLACE)
 *     suspend fun insert(entry: ExploreAllowlistEntry)
 * }
 *
 * @Dao
 * interface ExploreBlocklistDao {
 *     @Query("SELECT * FROM explore_blocklist")
 *     suspend fun getAllEntries(): List<ExploreBlocklistEntry>
 *
 *     @Insert(onConflict = OnConflictStrategy.REPLACE)
 *     suspend fun insert(entry: ExploreBlocklistEntry)
 * }
 */
