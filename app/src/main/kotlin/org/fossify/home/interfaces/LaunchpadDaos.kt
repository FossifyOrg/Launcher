package org.fossify.home.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.fossify.home.databases.AllowedApp
import org.fossify.home.databases.CryptoCashTransaction
import org.fossify.home.databases.ParentCommand
import org.fossify.home.databases.ExploreAllowedDomain
import org.fossify.home.databases.ExploreBlockedDomain
import org.fossify.home.databases.ExploreSuggestion
import org.fossify.home.databases.Zusage
import org.fossify.home.databases.DogeRequest

// ─── AllowedApp DAO ───────────────────────────────────────────────────────────

@Dao
interface AllowedAppDao {
    @Query("SELECT * FROM allowed_apps WHERE is_enabled = 1")
    suspend fun getAllEnabledApps(): List<AllowedApp>

    @Query("SELECT * FROM allowed_apps")
    suspend fun getAll(): List<AllowedApp>

    @Query("SELECT EXISTS(SELECT 1 FROM allowed_apps WHERE package_name = :pkg AND is_enabled = 1)")
    suspend fun isAppAllowed(pkg: String): Boolean

    @Query("SELECT category FROM allowed_apps WHERE package_name = :pkg LIMIT 1")
    suspend fun getAppCategory(pkg: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: AllowedApp)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<AllowedApp>)

    @Update
    suspend fun updateApp(app: AllowedApp)

    @Query("UPDATE allowed_apps SET is_enabled = :enabled WHERE package_name = :pkg")
    suspend fun setEnabled(pkg: String, enabled: Int)

    @Query("DELETE FROM allowed_apps WHERE package_name = :pkg")
    suspend fun deleteApp(pkg: String)
}

// ─── CryptoCash DAO ───────────────────────────────────────────────────────────

@Dao
interface CryptoCashDao {
    @Query("SELECT * FROM crypto_cash_transactions WHERE is_deleted = 0 ORDER BY created_at ASC")
    suspend fun getAllTransactions(): List<CryptoCashTransaction>

    @Query("SELECT * FROM crypto_cash_transactions WHERE week_number = :week AND year = :year AND is_deleted = 0")
    suspend fun getTransactionsForWeek(week: Int, year: Int): List<CryptoCashTransaction>

    @Query("SELECT * FROM crypto_cash_transactions WHERE is_deleted = 0 ORDER BY created_at DESC LIMIT 1")
    suspend fun getLastTransaction(): CryptoCashTransaction?

    @Query("SELECT * FROM crypto_cash_transactions WHERE is_deleted = 0 ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatestBalance(): CryptoCashTransaction?

    @Query("SELECT COALESCE((SELECT balance_after FROM crypto_cash_transactions WHERE is_deleted = 0 ORDER BY created_at DESC LIMIT 1), 0)")
    suspend fun getCurrentBalance(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(tx: CryptoCashTransaction)

    @Query("UPDATE crypto_cash_transactions SET is_deleted = 1 WHERE id = :id")
    suspend fun softDelete(id: String)
}

// ─── ParentCommand DAO ────────────────────────────────────────────────────────

@Dao
interface ParentCommandDao {
    @Query("SELECT * FROM parent_commands WHERE is_executed = 0 ORDER BY created_at ASC")
    suspend fun getPendingCommands(): List<ParentCommand>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommand(cmd: ParentCommand)

    @Query("UPDATE parent_commands SET is_executed = 1, executed_at = :at WHERE id = :id")
    suspend fun markExecuted(id: String, at: Long)
}

// ─── Explore DAO ──────────────────────────────────────────────────────────────

@Dao
interface ExploreDao {
    @Query("SELECT * FROM explore_allowed_domains WHERE is_active = 1")
    suspend fun getAllowedDomains(): List<ExploreAllowedDomain>

    @Query("SELECT * FROM explore_blocked_domains")
    suspend fun getBlockedDomains(): List<ExploreBlockedDomain>

    @Query("SELECT * FROM explore_suggestions")
    suspend fun getSuggestions(): List<ExploreSuggestion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllowedDomain(domain: ExploreAllowedDomain)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedDomain(domain: ExploreBlockedDomain)

    @Query("SELECT EXISTS(SELECT 1 FROM explore_allowed_domains WHERE domain = :domain AND is_active = 1)")
    suspend fun isDomainAllowed(domain: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM explore_blocked_domains WHERE domain = :domain)")
    suspend fun isDomainBlocked(domain: String): Boolean
}

// ─── Zusage DAO ───────────────────────────────────────────────────────────────

@Dao
interface ZusageDao {
    @Query("SELECT * FROM zusagen ORDER BY created_at DESC")
    suspend fun getAllZusagen(): List<Zusage>

    @Query("SELECT * FROM zusagen WHERE state = :state ORDER BY created_at DESC")
    suspend fun getZusagenByState(state: String): List<Zusage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZusage(zusage: Zusage)

    @Update
    suspend fun updateZusage(zusage: Zusage)

    @Query("UPDATE zusagen SET state = :state WHERE id = :id")
    suspend fun updateState(id: String, state: String)

    @Query("SELECT * FROM zusagen WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Zusage?
}

// ─── DogeRequest DAO ──────────────────────────────────────────────────────────

@Dao
interface DogeRequestDao {
    @Query("SELECT * FROM doge_requests ORDER BY requested_at DESC")
    suspend fun getAllRequests(): List<DogeRequest>

    @Query("SELECT * FROM doge_requests WHERE state = :state ORDER BY requested_at DESC")
    suspend fun getByState(state: String): List<DogeRequest>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: DogeRequest)

    @Update
    suspend fun updateRequest(request: DogeRequest)

    @Query("SELECT * FROM doge_requests WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DogeRequest?

    @Query("SELECT content_type, COUNT(*) as cnt FROM doge_requests GROUP BY content_type ORDER BY cnt DESC LIMIT 10")
    suspend fun getContentTypeStats(): List<ContentTypeCount>

    data class ContentTypeCount(val content_type: String, val cnt: Int)
}
