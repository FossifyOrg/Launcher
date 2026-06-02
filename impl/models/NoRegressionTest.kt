// File: shared/src/test/java/org/fossify/launchpad/models/NoRegressionTest.kt
// M1: Unit tests for No-Regression ledger validation

package org.fossify.launchpad.models

import org.junit.Test
import org.junit.Assert.*

class NoRegressionTest {

    @Test
    fun testValidLedgerAccepted() {
        val txs = listOf(
            LedgerEntry(
                id = "tx1",
                deltaMinutes = 10,
                type = "EARN",
                actor = "system",
                reasonType = "homework",
                reasonText = "Math homework completed",
                childVisibleText = "Mathe +10 Min",
                source = "launcher_rule",
                balanceAfter = 10
            ),
            LedgerEntry(
                id = "tx2",
                deltaMinutes = -5,
                type = "SPEND",
                actor = "jake",
                reasonType = "leisure_redeem",
                reasonText = "5 min YouTube",
                childVisibleText = "YouTube -5 Min",
                source = "launcher_rule",
                balanceAfter = 5
            )
        )

        val ledger = LedgerState(
            transactions = txs,
            currentBalance = 5,
            weekStart = System.currentTimeMillis(),
            weekTransactionsMinutes = 10
        )

        val result = ledger.validateNoRegression()
        assertTrue("Valid ledger should pass", result.isValid)
    }

    @Test
    fun testBalanceMismatchDetected() {
        val txs = listOf(
            LedgerEntry(
                id = "tx1",
                deltaMinutes = 10,
                type = "EARN",
                actor = "system",
                reasonType = "homework",
                reasonText = "Math homework",
                childVisibleText = "Mathe +10 Min",
                source = "launcher_rule",
                balanceAfter = 15  // WRONG: should be 10
            )
        )

        val ledger = LedgerState(
            transactions = txs,
            currentBalance = 10,
            weekStart = System.currentTimeMillis(),
            weekTransactionsMinutes = 10
        )

        val result = ledger.validateNoRegression()
        assertFalse("Balance mismatch should fail", result.isValid)
        assertNotNull("Should have error message", result.error)
        assertTrue("Error should mention balance", result.error?.contains("Balance mismatch") ?: false)
    }

    @Test
    fun testNegativeBalanceRejected() {
        val txs = listOf(
            LedgerEntry(
                id = "tx1",
                deltaMinutes = 10,
                type = "EARN",
                actor = "system",
                reasonType = "homework",
                reasonText = "Math homework",
                childVisibleText = "Mathe +10 Min",
                source = "launcher_rule",
                balanceAfter = 10
            ),
            LedgerEntry(
                id = "tx2",
                deltaMinutes = -15,  // Trying to spend more than available
                type = "SPEND",
                actor = "jake",
                reasonType = "leisure_redeem",
                reasonText = "15 min YouTube",
                childVisibleText = "YouTube -15 Min",
                source = "launcher_rule",
                balanceAfter = -5  // VIOLATION: balance went negative
            )
        )

        val ledger = LedgerState(
            transactions = txs,
            currentBalance = -5,
            weekStart = System.currentTimeMillis(),
            weekTransactionsMinutes = 10
        )

        val result = ledger.validateNoRegression()
        assertFalse("Negative balance should be rejected", result.isValid)
        assertTrue("Error should mention negative", result.error?.contains("negative") ?: false)
    }

    @Test
    fun testWeeklyCapsEnforced() {
        // Try to accumulate more than 120 minutes in a week
        val txs = mutableListOf<LedgerEntry>()
        var balance = 0
        for (i in 0..12) {
            val delta = 10
            balance += delta
            txs.add(
                LedgerEntry(
                    id = "tx$i",
                    deltaMinutes = delta,
                    type = "EARN",
                    actor = "system",
                    reasonType = "homework",
                    reasonText = "Task $i",
                    childVisibleText = "Task $i +10 Min",
                    source = "launcher_rule",
                    balanceAfter = balance
                )
            )
        }

        val ledger = LedgerState(
            transactions = txs,
            currentBalance = balance,
            weekStart = System.currentTimeMillis(),
            weekTransactionsMinutes = balance  // 130 minutes
        )

        val result = ledger.validateNoRegression()
        assertFalse("Weekly cap exceeded should be rejected", result.isValid)
        assertTrue("Error should mention cap", result.error?.contains("cap") ?: false)
    }

    @Test
    fun testImmutabilityCheckPasses() {
        val tx1 = LedgerEntry(
            id = "tx1",
            deltaMinutes = 10,
            type = "EARN",
            actor = "system",
            reasonType = "homework",
            reasonText = "Math homework",
            childVisibleText = "Mathe +10 Min",
            source = "launcher_rule",
            balanceAfter = 10
        )

        val prior = LedgerState(
            transactions = listOf(tx1),
            currentBalance = 10,
            weekStart = System.currentTimeMillis(),
            weekTransactionsMinutes = 10
        )

        val current = LedgerState(
            transactions = listOf(
                tx1,
                LedgerEntry(
                    id = "tx2",
                    deltaMinutes = 5,
                    type = "EARN",
                    actor = "system",
                    reasonType = "homework",
                    reasonText = "Homework 2",
                    childVisibleText = "Homework 2 +5 Min",
                    source = "launcher_rule",
                    balanceAfter = 15
                )
            ),
            currentBalance = 15,
            weekStart = System.currentTimeMillis(),
            weekTransactionsMinutes = 15
        )

        val result = current.checkImmutability(prior)
        assertTrue("Immutability check should pass for new tx", result.isValid)
    }

    @Test
    fun testImmutabilityCheckFailsOnDeletion() {
        val tx1 = LedgerEntry(
            id = "tx1",
            deltaMinutes = 10,
            type = "EARN",
            actor = "system",
            reasonType = "homework",
            reasonText = "Math homework",
            childVisibleText = "Mathe +10 Min",
            source = "launcher_rule",
            balanceAfter = 10
        )

        val prior = LedgerState(
            transactions = listOf(tx1),
            currentBalance = 10,
            weekStart = System.currentTimeMillis(),
            weekTransactionsMinutes = 10
        )

        val current = LedgerState(
            transactions = listOf(), // DELETED tx1!
            currentBalance = 0,
            weekStart = System.currentTimeMillis(),
            weekTransactionsMinutes = 0
        )

        val result = current.checkImmutability(prior)
        assertFalse("Immutability check should fail on deletion", result.isValid)
        assertTrue("Error should mention deletion", result.error?.contains("deleted") ?: false)
    }

    @Test
    fun testImmutabilityCheckFailsOnModification() {
        val tx1 = LedgerEntry(
            id = "tx1",
            deltaMinutes = 10,
            type = "EARN",
            actor = "system",
            reasonType = "homework",
            reasonText = "Math homework",
            childVisibleText = "Mathe +10 Min",
            source = "launcher_rule",
            balanceAfter = 10
        )

        val prior = LedgerState(
            transactions = listOf(tx1),
            currentBalance = 10,
            weekStart = System.currentTimeMillis(),
            weekTransactionsMinutes = 10
        )

        val modifiedTx = tx1.copy(deltaMinutes = 20) // MODIFIED!
        val current = LedgerState(
            transactions = listOf(modifiedTx),
            currentBalance = 20,
            weekStart = System.currentTimeMillis(),
            weekTransactionsMinutes = 20
        )

        val result = current.checkImmutability(prior)
        assertFalse("Immutability check should fail on modification", result.isValid)
        assertTrue("Error should mention modification", result.error?.contains("modified") ?: false)
    }

    @Test
    fun testCorrectionTransactionAllowed() {
        // Corrections are allowed for legitimate reasons (parental error correction)
        val txs = listOf(
            LedgerEntry(
                id = "tx1",
                deltaMinutes = 10,
                type = "EARN",
                actor = "system",
                reasonType = "homework",
                reasonText = "Math homework",
                childVisibleText = "Mathe +10 Min",
                source = "launcher_rule",
                balanceAfter = 10
            ),
            LedgerEntry(
                id = "correction1",
                deltaMinutes = 2,  // Correction adds 2 min (legitimate)
                type = "CORRECTION",
                actor = "papa",
                reasonType = "manual_correction",
                reasonText = "Manually added 2 min due to task system error",
                childVisibleText = "Correction +2 Min",
                source = "parent_app",
                balanceAfter = 12
            )
        )

        val ledger = LedgerState(
            transactions = txs,
            currentBalance = 12,
            weekStart = System.currentTimeMillis(),
            weekTransactionsMinutes = 10
        )

        val result = ledger.validateNoRegression()
        assertTrue("Correction transaction should be allowed", result.isValid)
    }

    @Test
    fun testEmptyLedgerValid() {
        val ledger = LedgerState(
            transactions = listOf(),
            currentBalance = 0,
            weekStart = System.currentTimeMillis(),
            weekTransactionsMinutes = 0
        )

        val result = ledger.validateNoRegression()
        assertTrue("Empty ledger should be valid", result.isValid)
    }
}
