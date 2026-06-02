// File: app/src/main/java/org/fossify/home/databases/AppsDatabase.kt
// M1: Room database upgrade from v5 to v6 with LAUNCHPAD entities

package org.fossify.home.databases

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.fossify.home.databases.entities.*

/**
 * Room Database v6: Adds LAUNCHPAD entities for Krypto-Cash, app whitelist, etc.
 *
 * Migration path: v5 → v6
 * - Adds: allowed_apps, crypto_cash_tx, parent_commands, zusagen, doge_requests,
 *         explore_allowlist, explore_blocklist, explore_suggestions
 * - Keeps: All existing v5 tables (apps, favorites, widgets, etc)
 * - No data loss
 */
@Database(
    entities = [
        // Existing Fossify entities (v5)
        // App, AppShortcut, FavouriteTag, Widget, etc
        // (keep existing imports)

        // New LAUNCHPAD entities (v6)
        AllowedApp::class,
        CryptoCashTransaction::class,
        ParentCommand::class,
        Zusage::class,
        DogeRequest::class,
        ExploreAllowlistEntry::class,
        ExploreBlocklistEntry::class,
        ExploreSuggestion::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppsDatabase : RoomDatabase() {
    // Existing DAOs
    // abstract fun appDao(): AppDao
    // abstract fun appShortcutDao(): AppShortcutDao
    // etc.

    // New DAOs (M1)
    abstract fun allowedAppDao(): AllowedAppDao
    abstract fun cryptoCashDao(): CryptoCashDao
    abstract fun parentCommandDao(): ParentCommandDao
    abstract fun zusageDao(): ZusageDao
    abstract fun dogeRequestDao(): DogeRequestDao
    abstract fun exploreAllowlistDao(): ExploreAllowlistDao
    abstract fun exploreBlocklistDao(): ExploreBlocklistDao
    abstract fun exploreSuggestionDao(): ExploreSuggestionDao

    companion object {
        private var INSTANCE: AppsDatabase? = null

        fun getInstance(context: Context): AppsDatabase {
            return INSTANCE ?: synchronized(this) {
                androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppsDatabase::class.java,
                    "apps.db"
                )
                    .addMigrations(MIGRATION_5_6)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /**
         * Migration from v5 to v6: Add LAUNCHPAD tables.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create allowed_apps table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS allowed_apps (
                        packageName TEXT PRIMARY KEY NOT NULL,
                        category TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        addedAt INTEGER NOT NULL
                    )
                """)

                // Create crypto_cash_tx table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS crypto_cash_tx (
                        id TEXT PRIMARY KEY NOT NULL,
                        deltaMinutes INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        actor TEXT NOT NULL,
                        reasonType TEXT NOT NULL,
                        reasonText TEXT NOT NULL,
                        childVisibleText TEXT NOT NULL,
                        source TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        balanceAfter INTEGER NOT NULL,
                        deleted INTEGER NOT NULL DEFAULT 0
                    )
                """)

                // Create parent_commands table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS parent_commands (
                        commandId TEXT PRIMARY KEY NOT NULL,
                        actor TEXT NOT NULL,
                        source TEXT NOT NULL,
                        type TEXT NOT NULL,
                        payloadJson TEXT NOT NULL,
                        reasonType TEXT NOT NULL,
                        reasonText TEXT NOT NULL,
                        childVisibleText TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        appliedAt INTEGER
                    )
                """)

                // Create zusagen table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS zusagen (
                        id TEXT PRIMARY KEY NOT NULL,
                        text TEXT NOT NULL,
                        namedParent TEXT NOT NULL,
                        status TEXT NOT NULL,
                        condition TEXT,
                        createdAt INTEGER NOT NULL,
                        autoApproveAt INTEGER NOT NULL,
                        decidedAt INTEGER,
                        reason TEXT,
                        childVisibleText TEXT NOT NULL
                    )
                """)

                // Create doge_requests table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS doge_requests (
                        id TEXT PRIMARY KEY NOT NULL,
                        contentDescription TEXT NOT NULL,
                        requestedAt INTEGER NOT NULL,
                        decision TEXT,
                        decidedBy TEXT,
                        reason TEXT,
                        durationMinutes INTEGER,
                        expiresAt INTEGER
                    )
                """)

                // Create explore_allowlist table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS explore_allowlist (
                        domain TEXT PRIMARY KEY NOT NULL,
                        addedAt INTEGER NOT NULL,
                        category TEXT
                    )
                """)

                // Create explore_blocklist table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS explore_blocklist (
                        pattern TEXT PRIMARY KEY NOT NULL,
                        reason TEXT NOT NULL,
                        addedAt INTEGER NOT NULL
                    )
                """)

                // Create explore_suggestions table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS explore_suggestions (
                        url TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        category TEXT NOT NULL,
                        status TEXT NOT NULL,
                        addedAt INTEGER NOT NULL
                    )
                """)

                // Populate default whitelist
                populateDefaultWhitelist(database)

                // Populate default blocklist
                populateDefaultBlocklist(database)
            }

            private fun populateDefaultWhitelist(database: SupportSQLiteDatabase) {
                val defaultApps = listOf(
                    "com.android.systemui" to "NEUTRAL",
                    "com.android.phone" to "COMMUNICATION",
                    "com.android.messaging" to "COMMUNICATION",
                    "com.android.contacts" to "COMMUNICATION",
                    "com.android.clock" to "NEUTRAL",
                    "com.android.calendar" to "LEARNING",
                    "org.fossify.home" to "NEUTRAL"
                )

                for ((pkg, category) in defaultApps) {
                    database.execSQL("""
                        INSERT OR IGNORE INTO allowed_apps
                        (packageName, category, enabled, addedAt)
                        VALUES (?, ?, 1, ?)
                    """, arrayOf(pkg, category, System.currentTimeMillis()))
                }
            }

            private fun populateDefaultBlocklist(database: SupportSQLiteDatabase) {
                val blockedPatterns = listOf(
                    "twitter.com" to "social_media",
                    "x.com" to "social_media",
                    "tiktok.com" to "social_media",
                    "instagram.com" to "social_media",
                    "reddit.com" to "community_nsfw",
                    ".*\\.onion" to "darkweb"
                )

                for ((pattern, reason) in blockedPatterns) {
                    database.execSQL("""
                        INSERT OR IGNORE INTO explore_blocklist
                        (pattern, reason, addedAt)
                        VALUES (?, ?, ?)
                    """, arrayOf(pattern, reason, System.currentTimeMillis()))
                }
            }
        }
    }
}

// --- DAOs (to be implemented in separate files) ---

// @Dao
// interface AllowedAppDao {
//     @Query("SELECT * FROM allowed_apps WHERE enabled = 1 ORDER BY category")
//     suspend fun getAllEnabledApps(): List<AllowedApp>
//
//     @Query("SELECT enabled FROM allowed_apps WHERE packageName = :packageName LIMIT 1")
//     suspend fun isAppAllowed(packageName: String): Boolean
//
//     @Query("SELECT category FROM allowed_apps WHERE packageName = :packageName LIMIT 1")
//     suspend fun getAppCategory(packageName: String): String?
//
//     @Insert(onConflict = OnConflictStrategy.REPLACE)
//     suspend fun insertApp(app: AllowedApp)
//
//     @Update
//     suspend fun updateApp(app: AllowedApp)
//
//     @Delete
//     suspend fun deleteApp(app: AllowedApp)
//
//     @Query("UPDATE allowed_apps SET enabled = :enabled WHERE packageName = :packageName")
//     suspend fun setAppEnabled(packageName: String, enabled: Boolean)
// }

// @Dao
// interface CryptoCashDao {
//     @Query("SELECT * FROM crypto_cash_tx WHERE deleted = 0 ORDER BY createdAt DESC")
//     suspend fun getAllTransactions(): List<CryptoCashTransaction>
//
//     @Query("SELECT * FROM crypto_cash_tx WHERE deleted = 0 ORDER BY createdAt DESC LIMIT 1")
//     suspend fun getLastTransaction(): CryptoCashTransaction?
//
//     @Query("SELECT balanceAfter FROM crypto_cash_tx WHERE deleted = 0 ORDER BY createdAt DESC LIMIT 1")
//     suspend fun getLatestBalance(): Int?
//
//     @Insert
//     suspend fun insertTransaction(tx: CryptoCashTransaction)
//
//     @Query("UPDATE crypto_cash_tx SET deleted = 1 WHERE id = :id")
//     suspend fun softDeleteTransaction(id: String)
// }

// Similar DAOs for ParentCommand, Zusage, DogeRequest, ExploreAllowlist, ExploreBlocklist, ExploreSuggestion
