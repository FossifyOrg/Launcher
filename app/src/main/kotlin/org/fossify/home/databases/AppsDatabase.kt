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

private const val DATABASE_VERSION = 6
private const val MIGRATION_FROM_5_TO_6_START_VERSION = 5
private const val MIGRATION_FROM_5_TO_6_END_VERSION = 6

@Database(
    entities = [AppLauncher::class, HomeScreenGridItem::class, HiddenIcon::class],
    version = DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class AppsDatabase : RoomDatabase() {

    abstract fun AppLaunchersDao(): AppLaunchersDao

    abstract fun HomeScreenGridItemsDao(): HomeScreenGridItemsDao

    abstract fun HiddenIconsDao(): HiddenIconsDao

    companion object {
        private var db: AppsDatabase? = null
        private val MIGRATION_FROM_5_TO_6 = object : Migration(
            MIGRATION_FROM_5_TO_6_START_VERSION,
            MIGRATION_FROM_5_TO_6_END_VERSION
        ) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE apps ADD COLUMN user_serial INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE home_screen_grid_items ADD COLUMN user_serial INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE hidden_icons ADD COLUMN user_serial INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "DROP INDEX IF EXISTS index_apps_package_name"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_apps_package_name_activity_name_user_serial " +
                            "ON apps(package_name, activity_name, user_serial)"
                )
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
                        ).addMigrations(MIGRATION_FROM_5_TO_6)
                            .build()
                    }
                }
            }
            return db!!
        }
    }
}
