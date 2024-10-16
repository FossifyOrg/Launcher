package org.fossify.home.models

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.drawable.Drawable
import androidx.room.*
import org.fossify.home.helpers.ITEM_TYPE_ICON

// grid cells are from 0-5 by default. Icons and shortcuts occupy 1 slot only, widgets can be bigger
@Entity(tableName = "home_screen_grid_items", indices = [(Index(value = ["id"], unique = true))])
data class HomeScreenGridItem(
    @PrimaryKey(autoGenerate = true) var id: Long?,
    @ColumnInfo(name = "left") var left: Int,
    @ColumnInfo(name = "top") var top: Int,
    @ColumnInfo(name = "right") var right: Int,
    @ColumnInfo(name = "bottom") var bottom: Int,
    @ColumnInfo(name = "page") var page: Int,
    @ColumnInfo(name = "package_name") var packageName: String,
    @ColumnInfo(name = "activity_name") var activityName: String,   // needed at apps that create multiple icons at install, not just the launcher
    @ColumnInfo(name = "title") var title: String,
    @ColumnInfo(name = "type") var type: Int,
    @ColumnInfo(name = "class_name") var className: String,
    @ColumnInfo(name = "widget_id") var widgetId: Int,
    @ColumnInfo(name = "shortcut_id") var shortcutId: String,   // used at pinned shortcuts at startLauncher call
    @ColumnInfo(name = "icon") var icon: Bitmap? = null,        // store images of pinned shortcuts, those cannot be retrieved after creating
    @ColumnInfo(name = "docked") var docked: Boolean = false,   // special flag, meaning that page, top and bottom don't matter for this item, it is always at the bottom of the screen
    @ColumnInfo(name = "parent_id") var parentId: Long? = null, // id of folder this item is in (if it is in any)

    @Ignore var drawable: Drawable? = null,
    @Ignore var providerInfo: AppWidgetProviderInfo? = null,    // used at widgets
    @Ignore var activityInfo: ActivityInfo? = null,             // used at shortcuts
    @Ignore var widthCells: Int = 1,
    @Ignore var heightCells: Int = 1
) {
    companion object {
        const val FOLDER_MAX_CAPACITY = 16
    }

    constructor() : this(null, -1, -1, -1, -1, 0, "", "", "", ITEM_TYPE_ICON, "", -1, "", null, false, null, null, null, null, 1, 1)

    fun getWidthInCells() = if (right == -1 || left == -1) {
        widthCells
    } else {
        right - left + 1
    }

    fun getHeightInCells() = if (bottom == -1 || top == -1) {
        heightCells
    } else {
        bottom - top + 1
    }

    fun getDockAdjustedTop(rowCount: Int): Int {
        return if (!docked) {
            top
        } else {
            rowCount - 1
        }
    }

    fun getDockAdjustedBottom(rowCount: Int): Int {
        return if (!docked) {
            bottom
        } else {
            rowCount - 1
        }
    }

    fun getItemIdentifier() = "$packageName/$activityName"

    fun getTopLeft(rowCount: Int) = Point(left, getDockAdjustedTop(rowCount))
}
