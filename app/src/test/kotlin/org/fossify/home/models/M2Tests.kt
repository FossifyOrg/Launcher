// File: shared/src/test/java/org/fossify/launchpad/models/M2Tests.kt
// M2: Tests for Zusagen, Doge-Coins, and cool-down rules

package org.fossify.home.models

import org.junit.Test
import org.junit.Assert.*
import org.fossify.home.helpers.CooldownRulesConfig
import org.fossify.home.helpers.CooldownRulesValidator

class ZusageTests {
    private val manager = ZusageManager()

    @Test
    fun testCreateZusage() {
        val zusage = manager.createZusage(
            "Nach Hausaufgaben, dann 20 Min Minecraft",
            "Mama"
        )

        assertEquals(zusage.status, "ACTIVE")
        assertEquals(zusage.namedParent, "Mama")
        assertNull(zusage.decidedAt)
    }

    @Test
    fun testApproveZusage() {
        val zusage = manager.createZusage("Test promise", "Papa")
        val approved = manager.approveZusage(zusage, "Papa")

        assertTrue(approved.isApproved())
        assertNotNull(approved.decidedAt)
        assertEquals(approved.decidedBy, "Papa")
    }

    @Test
    fun testCannotApproveAlreadyApproved() {
        val zusage = manager.createZusage("Test promise", "Mama")
        val approved = manager.approveZusage(zusage, "Mama")

        // Should throw when trying to approve already-approved zusage
        try {
            manager.approveZusage(approved, "Mama")
            fail("Should have thrown exception")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("cannot be approved") ?: false)
        }
    }

    @Test
    fun testRejectBeforeApproval() {
        val zusage = manager.createZusage("Test promise", "Mama")
        val rejected = manager.rejectZusage(zusage, "Papa", "Not appropriate now")

        assertEquals(rejected.status, "REVOKED")
        assertEquals(rejected.decidedBy, "Papa")
        assertNotNull(rejected.decidedAt)
    }

    @Test
    fun testCannotRejectAfterApproval() {
        val zusage = manager.createZusage("Test promise", "Mama")
        val approved = manager.approveZusage(zusage, "Mama")

        // Should throw when trying to reject already-approved zusage
        try {
            manager.rejectZusage(approved, "Papa", "Changed mind")
            fail("Should have thrown exception")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("already decided") ?: false)
        }
    }

    @Test
    fun testFulfillZusage() {
        val zusage = manager.createZusage("After homework, then 20 min Minecraft", "Mama")
        val approved = manager.approveZusage(zusage, "Mama")
        val fulfilled = manager.fulfillZusage(approved, "system")

        assertEquals(fulfilled.status, "FULFILLED")
        assertTrue(fulfilled.childVisibleText.contains("✓"))
    }

    @Test
    fun testAutoApproveAfter24Hours() {
        val zusage = manager.createZusage("Test promise", "Mama")
        // Manually set autoApproveAt to past
        val expiredZusage = zusage.copy(
            autoApproveAt = System.currentTimeMillis() - 1000
        )

        val autoApproved = manager.autoApproveExpired(expiredZusage)
        assertTrue(autoApproved.isApproved())
        assertEquals(autoApproved.decidedBy, "system")
    }

    @Test
    fun testNoRegressionViolation() {
        val zusage = manager.createZusage("Test promise", "Mama")
        val approved = manager.approveZusage(zusage, "Mama")

        // Try to validate regression with revoked version
        val revoked = approved.copy(status = "REVOKED")
        val result = manager.validateNoRegression(revoked, approved)

        assertFalse(result.isValid)
        assertTrue(result.error?.contains("cannot be unapproved") ?: false)
    }

    @Test
    fun testGetActiveZusagen() {
        val z1 = manager.createZusage("Promise 1", "Mama")
        val z2 = manager.createZusage("Promise 2", "Papa")
        val z3 = manager.createZusage("Promise 3", "Mama")

        val approved1 = manager.approveZusage(z1, "Mama")
        val approved3 = manager.approveZusage(z3, "Mama")

        val allZusagen = listOf(approved1, z2, approved3)
        val active = manager.getActiveZusagen(allZusagen)

        assertEquals(2, active.size)
        assertTrue(active.all { it.isApproved() })
    }
}

class DogeTests {
    private val manager = DogeManager()

    @Test
    fun testCreateRequest() {
        val request = manager.createRequest("20 Min YouTube - Minecraft")

        assertEquals(request.status, "PENDING")
        assertTrue(request.isPending())
        assertNull(request.decidedAt)
    }

    @Test
    fun testApproveRequest() {
        val request = manager.createRequest("YouTube - Education")
        val approved = manager.approveRequest(request, "Papa", 20, "Educational content")

        assertEquals(approved.decision, "APPROVED")
        assertEquals(approved.durationMinutes, 20)
        assertTrue(approved.isApproved())
        assertNotNull(approved.expiresAt)
    }

    @Test
    fun testCanAccessApprovedContent() {
        val request = manager.createRequest("YouTube - Minecraft")
        val approved = manager.approveRequest(request, "Mama", 20)

        assertTrue(manager.canAccessContent(approved))
    }

    @Test
    fun testCannotAccessRejectedContent() {
        val request = manager.createRequest("TikTok")
        val rejected = manager.rejectRequest(request, "Papa", "Too much stimulation")

        assertFalse(manager.canAccessContent(rejected))
    }

    @Test
    fun testRejectRequest() {
        val request = manager.createRequest("Instagram")
        val rejected = manager.rejectRequest(request, "Mama", "Not appropriate now")

        assertEquals(rejected.decision, "REJECTED")
        assertEquals(rejected.decidedBy, "Mama")
    }

    @Test
    fun testTimeRemainingInApproval() {
        val request = manager.createRequest("YouTube")
        val approved = manager.approveRequest(request, "Papa", 20)

        val remaining = manager.getTimeRemaining(approved)
        assertNotNull(remaining)
        assertTrue(remaining!! > 0)
        assertTrue(remaining <= 20)
    }

    @Test
    fun testExpireRequest() {
        val request = manager.createRequest("YouTube")
        val approved = manager.approveRequest(request, "Papa", 5)

        // Wait and expire
        Thread.sleep(100)
        val expired = manager.expireRequest(approved)

        assertEquals(expired.status, "EXPIRED")
        assertFalse(manager.canAccessContent(expired))
    }

    @Test
    fun testAnalyzeRequestPatterns() {
        val requests = listOf(
            manager.createRequest("YouTube - Minecraft"),
            manager.createRequest("YouTube - Drawing"),
            manager.createRequest("YouTube - Science"),
            manager.approveRequest(manager.createRequest("Discord"), "Papa", 30),
            manager.approveRequest(manager.createRequest("Audiobook - Fiction"), "Mama", 60)
        )

        val patterns = manager.analyzeRequestPatterns(requests)

        assertTrue(patterns.contains("YouTube"))
        assertTrue(patterns["YouTube"]!! >= 3)
    }

    @Test
    fun testSuggestApprovalDuration() {
        assertEquals(30, manager.suggestApprovalDuration("YouTube - Tutorial"))
        assertEquals(30, manager.suggestApprovalDuration("Educational video"))
        assertEquals(25, manager.suggestApprovalDuration("Minecraft building"))
        assertEquals(20, manager.suggestApprovalDuration("YouTube random"))
        assertEquals(15, manager.suggestApprovalDuration("Video streaming"))
    }

    @Test
    fun testPendingRequests() {
        val r1 = manager.createRequest("YouTube")
        val r2 = manager.createRequest("Discord")
        val r3 = manager.approveRequest(manager.createRequest("Audiobook"), "Papa", 30)

        val allRequests = listOf(r1, r2, r3)
        val pending = manager.getPendingRequests(allRequests)

        assertEquals(2, pending.size)
        assertTrue(pending.all { it.isPending() })
    }
}

class CooldownRulesTests {
    private val validator = CooldownRulesValidator()

    @Test
    fun testDefaultCooldownConfig() {
        val config = CooldownRulesConfig()

        assertEquals(15, config.duration)
        assertTrue(config.enabled)
        assertTrue(config.trigger_on_zero_balance)
        assertTrue(config.allowed_apps.size >= 3)
    }

    @Test
    fun testValidJson() {
        val json = CooldownRulesConfig.defaultJson()
        val result = validator.validate(json)

        assertTrue(result.isValid)
    }

    @Test
    fun testInvalidDuration() {
        val config = CooldownRulesConfig(duration = 0)
        val result = validator.validate(CooldownRulesConfig.toJson(config))

        assertFalse(result.isValid)
        assertTrue(result.error?.contains("must be") ?: false)
    }

    @Test
    fun testParseFromJson() {
        val json = """
        {
          "duration": 20,
          "allowed_apps": ["com.ibis.paintx", "com.lego.common"],
          "enabled": true
        }
        """.trimIndent()

        val config = CooldownRulesConfig.fromJson(json)

        assertEquals(20, config.duration)
        assertEquals(2, config.allowed_apps.size)
        assertTrue(config.enabled)
    }

    @Test
    fun testInvalidJsonFallsBackToDefaults() {
        val invalidJson = "not valid json at all"
        val config = CooldownRulesConfig.fromJson(invalidJson)

        // Should return defaults on parse error
        assertEquals(15, config.duration)
        assertTrue(config.enabled)
    }

    @Test
    fun testTimeFormatValidation() {
        val validConfig = CooldownRulesConfig(
            start_time = "08:00",
            end_time = "22:00"
        )
        assertTrue(validConfig.isValidJson())

        val invalidConfig = CooldownRulesConfig(
            start_time = "25:00" // Invalid hour
        )
        assertFalse(invalidConfig.isValidJson())
    }

    @Test
    fun testEmptyAllowedAppsInvalid() {
        val config = CooldownRulesConfig(allowed_apps = emptyList())
        assertFalse(config.isValidJson())
    }
}

class M2IntegrationTests {
    private val zusageManager = ZusageManager()
    private val dogeManager = DogeManager()

    @Test
    fun testFamilyPromiseAndApprovalFlow() {
        // Parent makes promise
        val promise = zusageManager.createZusage(
            "After homework, then 20 min Minecraft",
            "Mama"
        )

        // Child requests Minecraft
        val dogeRequest = dogeManager.createRequest("Minecraft")

        // Parent approves request (because promise)
        val approvedRequest = dogeManager.approveRequest(dogeRequest, "Mama", 20)

        // Parent approves promise
        val approvedPromise = zusageManager.approveZusage(promise, "Mama")

        // Verify Jake can access Minecraft
        assertTrue(dogeManager.canAccessContent(approvedRequest))
        assertTrue(approvedPromise.isApproved())
    }

    @Test
    fun testMultiplePromisesAndRequests() {
        // Multiple promises
        val promises = listOf(
            "After homework, then 20 Min Minecraft",
            "Before bed, 10 min audiobook",
            "Saturday afternoon, drawing time"
        ).map { zusageManager.createZusage(it, "Papa") }

        // Multiple requests
        val requests = listOf(
            "Minecraft",
            "Audiobooks",
            "Drawing app"
        ).map { dogeManager.createRequest(it) }

        // Approve all
        val approvedPromises = promises.map { zusageManager.approveZusage(it, "Papa") }
        val approvedRequests = requests.mapIndexed { idx, r ->
            dogeManager.approveRequest(r, "Papa", 20 + idx)
        }

        assertEquals(3, approvedPromises.size)
        assertEquals(3, approvedRequests.size)
    }
}
