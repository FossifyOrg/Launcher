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

// LAUNCHPAD entities
import org.fossify.home.databases.AllowedApp
import org.fossify.home.databases.CryptoCashTransaction
import org.fossify.home.databases.ParentCommand
import org.fossify.home.databases.Zusage
import org.fossify.home.databases.DogeRequest
import org.fossify.home.databases.ExploreAllowlistEntry
import org.fossify.home.databases.ExploreBlocklistEntry
import org.fossify.home.databases.ExploreSuggestion

// LAUNCHPAD DAOs
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
        ExploreAllowlistEntry::class,
        ExploreBlocklistEntry::class,
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

    // LAUNCHPAD DAOs
    abstract fun allowedAppDao(): AllowedAppDao
    abstract fun cryptoCashDao(): CryptoCashDao
    abstract fun parentCommandDao(): ParentCommandDao
    abstract fun exploreDao(): ExploreDao
    abstract fun zusageDao(): ZusageDao
    abstract fun dogeRequestDao(): DogeRequestDao

    companion object {
        private var db: AppsDatabase? = null

        // DDL below is generated to EXACTLY match Room's expected schema for the
        // LAUNCHPAD @Entity classes (column names = Kotlin field names, no DEFAULTs,
        // Boolean -> INTEGER, nullable -> no NOT NULL). Keep in sync with LaunchpadEntities.kt.
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `allowed_apps` (" +
                        "`packageName` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`packageName`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `crypto_cash_tx` (" +
                        "`id` TEXT NOT NULL, `deltaMinutes` INTEGER NOT NULL, `type` TEXT NOT NULL, " +
                        "`actor` TEXT NOT NULL, `reasonType` TEXT NOT NULL, `reasonText` TEXT NOT NULL, " +
                        "`childVisibleText` TEXT NOT NULL, `source` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `balanceAfter` INTEGER NOT NULL, " +
                        "`deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `parent_commands` (" +
                        "`commandId` TEXT NOT NULL, `actor` TEXT NOT NULL, `source` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `reasonType` TEXT NOT NULL, " +
                        "`reasonText` TEXT NOT NULL, `childVisibleText` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `status` TEXT NOT NULL, `appliedAt` INTEGER, " +
                        "PRIMARY KEY(`commandId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `zusagen` (" +
                        "`id` TEXT NOT NULL, `text` TEXT NOT NULL, `namedParent` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, `condition` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`autoApproveAt` INTEGER NOT NULL, `decidedAt` INTEGER, `reason` TEXT, " +
                        "`childVisibleText` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `doge_requests` (" +
                        "`id` TEXT NOT NULL, `contentDescription` TEXT NOT NULL, " +
                        "`requestedAt` INTEGER NOT NULL, `decision` TEXT, `decidedBy` TEXT, " +
                        "`reason` TEXT, `durationMinutes` INTEGER, `expiresAt` INTEGER, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `explore_allowlist` (" +
                        "`domain` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, `category` TEXT, " +
                        "PRIMARY KEY(`domain`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `explore_blocklist` (" +
                        "`pattern` TEXT NOT NULL, `reason` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`pattern`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `explore_suggestions` (" +
                        "`url` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, `status` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`url`))"
                )

                seedExploreDefaults(db)
            }
        }

        // Seed default safe-browsing lists. Runs on upgrade (migration) and on fresh
        // install (RoomDatabase.Callback.onCreate) so both paths get the defaults.
        private fun seedExploreDefaults(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()

            val allowed = listOf(
                "youtube.com", "wikipedia.org", "khanacademy.org",
                "scratch.mit.edu", "codecademy.com", "duolingo.com"
            )
            for (domain in allowed) {
                db.execSQL(
                    "INSERT OR IGNORE INTO `explore_allowlist` (`domain`, `addedAt`, `category`) VALUES (?, ?, ?)",
                    arrayOf(domain, now, "EDUCATIONAL")
                )
            }

            val blocked = listOf(
                "twitter.com" to "social_media",
                "x.com" to "social_media",
                "tiktok.com" to "social_media",
                "instagram.com" to "social_media",
                "reddit.com" to "forum",
                "facebook.com" to "social_media",
                "snapchat.com" to "social_media",
                ".onion" to "darkweb"
            )
            for ((pattern, reason) in blocked) {
                db.execSQL(
                    "INSERT OR IGNORE INTO `explore_blocklist` (`pattern`, `reason`, `addedAt`) VALUES (?, ?, ?)",
                    arrayOf(pattern, reason, now)
                )
            }
        }

        private val seedCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                seedExploreDefaults(db)
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
                            .addCallback(seedCallback)
                            .build()
                    }
                }
            }
            return db!!
        }
    }
}
