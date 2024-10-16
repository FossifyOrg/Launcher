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

@Database(entities = [AppLauncher::class, HomeScreenGridItem::class, HiddenIcon::class], version = 5)
@TypeConverters(Converters::class)
abstract class AppsDatabase : RoomDatabase() {

    abstract fun AppLaunchersDao(): AppLaunchersDao

    abstract fun HomeScreenGridItemsDao(): HomeScreenGridItemsDao

    abstract fun HiddenIconsDao(): HiddenIconsDao

    companion object {
        private var db: AppsDatabase? = null

        fun getInstance(context: Context): AppsDatabase {
            if (db == null) {
                synchronized(AppsDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(context.applicationContext, AppsDatabase::class.java, "apps.db")
                            .addMigrations(MIGRATION_1_2)
                            .addMigrations(MIGRATION_2_3)
                            .addMigrations(MIGRATION_3_4)
                            .addMigrations(MIGRATION_4_5)
                            .build()
                    }
                }
            }
            return db!!
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE home_screen_grid_items ADD COLUMN intent TEXT default '' NOT NULL")
                database.execSQL("ALTER TABLE home_screen_grid_items ADD COLUMN shortcut_id TEXT default '' NOT NULL")
                database.execSQL("ALTER TABLE home_screen_grid_items ADD COLUMN icon BLOB")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE apps ADD COLUMN activity_name TEXT default '' NOT NULL")
                database.execSQL("ALTER TABLE home_screen_grid_items ADD COLUMN activity_name TEXT default '' NOT NULL")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `hidden_icons` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `package_name` TEXT NOT NULL, `activity_name` TEXT NOT NULL, `title` TEXT NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX `index_hidden_icons_id` ON `hidden_icons` (`id`)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE `home_screen_grid_items_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `left` INTEGER NOT NULL, `top` INTEGER NOT NULL, `right` INTEGER NOT NULL, `bottom` INTEGER NOT NULL, `page` INTEGER NOT NULL, `package_name` TEXT NOT NULL, `activity_name` TEXT NOT NULL, `title` TEXT NOT NULL, `type` INTEGER NOT NULL, `class_name` TEXT NOT NULL, `widget_id` INTEGER NOT NULL, `shortcut_id` TEXT NOT NULL, `icon` BLOB, `docked` INTEGER NOT NULL DEFAULT 0, `parent_id` INTEGER)")
                database.execSQL("INSERT INTO `home_screen_grid_items_new` (`id`, `left`, `top`, `right`, `bottom`, `page`, `package_name`, `activity_name`, `title`, `type`, `class_name`, `widget_id`, `shortcut_id`, `icon`, `docked`, `parent_id`)  SELECT `id`, `left`, `top`, `right`, `bottom`, 0 as `page`, `package_name`, `activity_name`, `title`, `type`, `class_name`, `widget_id`, `shortcut_id`, `icon`, CASE WHEN `type` != 1 AND `top` = 5 THEN 1 ELSE 0 END AS `docked`, NULL AS `parent_id` FROM `home_screen_grid_items` WHERE `intent` IS NULL OR `intent` = ''")
                database.execSQL("DROP TABLE `home_screen_grid_items`")
                database.execSQL("ALTER TABLE `home_screen_grid_items_new` RENAME TO `home_screen_grid_items`")
                database.execSQL("CREATE UNIQUE INDEX `index_home_screen_grid_items_id` ON `home_screen_grid_items` (`id`)")
            }
        }
    }
}
