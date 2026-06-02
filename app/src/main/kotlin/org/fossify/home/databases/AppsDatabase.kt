package org.fossify.home.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.fossify.home.helpers.Converters
import org.fossify.home.interfaces.AppLaunchersDao
import org.fossify.home.interfaces.HiddenIconsDao
import org.fossify.home.interfaces.HomeScreenGridItemsDao
import org.fossify.home.models.AppLauncher
import org.fossify.home.models.HiddenIcon
import org.fossify.home.models.HomeScreenGridItem

// LAUNCHPAD M1 entities
import org.fossify.home.databases.AllowedApp
import org.fossify.home.databases.CryptoCashTransaction
import org.fossify.home.databases.ParentCommand
import org.fossify.home.databases.ExploreAllowedDomain
import org.fossify.home.databases.ExploreBlockedDomain
import org.fossify.home.databases.ExploreSuggestion

// LAUNCHPAD M2 entities
import org.fossify.home.databases.Zusage
import org.fossify.home.databases.DogeRequest

// LAUNCHPAD M1+M2 DAOs
import org.fossify.home.interfaces.AllowedAppDao
import org.fossify.home.interfaces.CryptoCashDao
import org.fossify.home.interfaces.ParentCommandDao
import org.fossify.home.interfaces.ExploreDao
import org.fossify.home.interfaces.ZusageDao
import org.fossify.home.interfaces.DogeRequestDao

@Database(
    entities = [
        // Existing Fossify entities
        AppLauncher::class,
        HomeScreenGridItem::class,
        HiddenIcon::class,
        // LAUNCHPAD M1 entities
        AllowedApp::class,
        CryptoCashTransaction::class,
        ParentCommand::class,
        ExploreAllowedDomain::class,
        ExploreBlockedDomain::class,
        ExploreSuggestion::class,
        // LAUNCHPAD M2 entities
        Zusage::class,
        DogeRequest::class
    ],
    version = 6
)
@TypeConverters(Converters::class)
abstract class AppsDatabase : RoomDatabase() {

    // Existing Fossify DAOs
    abstract fun AppLaunchersDao(): AppLaunchersDao
    abstract fun HomeScreenGridItemsDao(): HomeScreenGridItemsDao
    abstract fun HiddenIconsDao(): HiddenIconsDao

    // LAUNCHPAD M1 DAOs
    abstract fun allowedAppDao(): AllowedAppDao
    abstract fun cryptoCashDao(): CryptoCashDao
    abstract fun parentCommandDao(): ParentCommandDao
    abstract fun exploreDao(): ExploreDao

    // LAUNCHPAD M2 DAOs
    abstract fun zusageDao(): ZusageDao
    abstract fun dogeRequestDao(): DogeRequestDao

    companion object {
        private var db: AppsDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // LAUNCHPAD M1: Whitelist
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS allowed_apps (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        package_name TEXT NOT NULL,
                        display_name TEXT NOT NULL,
                        is_enabled INTEGER NOT NULL DEFAULT 1,
                        added_at INTEGER NOT NULL,
                        category TEXT NOT NULL DEFAULT 'other',
                        time_limit_minutes INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_allowed_apps_pkg ON allowed_apps(package_name)")

                // LAUNCHPAD M1: Krypto-Cash ledger
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS crypto_cash_transactions (
                        id TEXT PRIMARY KEY NOT NULL,
                        delta_minutes INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        balance_after INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        is_deleted INTEGER NOT NULL DEFAULT 0,
                        week_number INTEGER NOT NULL,
                        year INTEGER NOT NULL
                    )
                """.trimIndent())

                // LAUNCHPAD M1: Parent commands
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS parent_commands (
                        id TEXT PRIMARY KEY NOT NULL,
                        type TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        executed_at INTEGER,
                        is_executed INTEGER NOT NULL DEFAULT 0,
                        source TEXT NOT NULL DEFAULT 'local'
                    )
                """.trimIndent())

                // LAUNCHPAD M1: Explore allowlist
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS explore_allowed_domains (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        domain TEXT NOT NULL,
                        display_name TEXT NOT NULL,
                        is_active INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_explore_allowed_domain ON explore_allowed_domains(domain)")

                // LAUNCHPAD M1: Explore blocklist
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS explore_blocked_domains (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        domain TEXT NOT NULL,
                        reason TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_explore_blocked_domain ON explore_blocked_domains(domain)")

                // LAUNCHPAD M1: Explore suggestions
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS explore_suggestions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        url TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        icon_emoji TEXT NOT NULL DEFAULT '',
                        category TEXT NOT NULL DEFAULT 'learning'
                    )
                """.trimIndent())

                // LAUNCHPAD M2: Zusagen (family promises)
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS zusagen (
                        id TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        condition_text TEXT NOT NULL,
                        reward_minutes INTEGER NOT NULL DEFAULT 0,
                        reward_description TEXT NOT NULL DEFAULT '',
                        state TEXT NOT NULL DEFAULT 'pending',
                        created_at INTEGER NOT NULL,
                        approved_at INTEGER,
                        fulfilled_at INTEGER,
                        expires_at INTEGER,
                        created_by TEXT NOT NULL DEFAULT 'parent'
                    )
                """.trimIndent())

                // LAUNCHPAD M2: Doge-Coin media requests
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS doge_requests (
                        id TEXT PRIMARY KEY NOT NULL,
                        content_title TEXT NOT NULL,
                        content_url TEXT NOT NULL DEFAULT '',
                        content_type TEXT NOT NULL,
                        platform TEXT NOT NULL,
                        requested_minutes INTEGER NOT NULL,
                        approved_minutes INTEGER NOT NULL DEFAULT 0,
                        state TEXT NOT NULL DEFAULT 'pending',
                        requested_at INTEGER NOT NULL,
                        decided_at INTEGER,
                        expires_at INTEGER,
                        deny_reason TEXT NOT NULL DEFAULT '',
                        access_count INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // Seed default Explore allowlist
                val allowedDomains = listOf(
                    Triple("youtube.com", "YouTube", 1),
                    Triple("wikipedia.org", "Wikipedia", 1),
                    Triple("khanacademy.org", "Khan Academy", 1),
                    Triple("scratch.mit.edu", "Scratch", 1),
                    Triple("codecademy.com", "Codecademy", 1),
                    Triple("duolingo.com", "Duolingo", 1)
                )
                for ((domain, name, _) in allowedDomains) {
                    database.execSQL(
                        "INSERT OR IGNORE INTO explore_allowed_domains (domain, display_name) VALUES ('$domain', '$name')"
                    )
                }

                // Seed default Explore blocklist
                val blockedDomains = listOf(
                    Pair("twitter.com", "Social media"), Pair("x.com", "Social media"),
                    Pair("tiktok.com", "Social media"), Pair("instagram.com", "Social media"),
                    Pair("reddit.com", "Forum"), Pair("discord.com", "Chat"),
                    Pair("snapchat.com", "Social media"), Pair("facebook.com", "Social media")
                )
                for ((domain, reason) in blockedDomains) {
                    database.execSQL(
                        "INSERT OR IGNORE INTO explore_blocked_domains (domain, reason) VALUES ('$domain', '$reason')"
                    )
                }
            }
        }

        fun getInstance(context: Context): AppsDatabase {
            if (db == null) {
                synchronized(AppsDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(
                            context.applicationContext,
                            AppsDatabase::class.java,
                            "apps.db"
                        )
                            .addMigrations(MIGRATION_5_6)
                            .build()
                    }
                }
            }
            return db!!
        }
    }
}
