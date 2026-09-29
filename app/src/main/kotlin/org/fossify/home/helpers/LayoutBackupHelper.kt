package org.fossify.home.helpers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import org.fossify.home.extensions.config
import org.fossify.home.extensions.homeScreenGridItemsDB
import org.fossify.home.interfaces.HomeScreenGridItemsDao
import org.fossify.home.models.HomeScreenGridItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exports and imports the icon and folder layout of the home screen as JSON.
 *
 * Deliberately scoped to [ITEM_TYPE_ICON] and [ITEM_TYPE_FOLDER] only. Widgets and pinned
 * shortcuts are skipped in both directions: a widget id only means something to the
 * AppWidgetHost that allocated it, and a shortcut id only means something to the app that
 * pinned it. Writing either back on a different device (which is the whole point of a
 * layout backup) would bind to an unrelated widget or to nothing at all.
 *
 * Icons and folders are safe to restore blind because they are just grid coordinates plus a
 * package/activity reference, and both can be validated against the current device before
 * anything is written to the database.
 */
object LayoutBackupHelper {

    const val BACKUP_MIME_TYPE = "application/json"

    private const val BACKUP_VERSION = 1

    /**
     * Highest page an item may be restored onto. HomeScreenGrid derives its page count from
     * the largest page in use and draws one indicator per page, so a corrupt file must not be
     * able to ask for an unbounded number of them. No real home screen comes close to this.
     */
    private const val MAX_PAGE = 100

    private const val KEY_VERSION = "version"
    private const val KEY_ITEMS = "items"
    private const val KEY_TEMP_ID = "tempId"
    private const val KEY_PARENT_TEMP_ID = "parentTempId"
    private const val KEY_LEFT = "left"
    private const val KEY_TOP = "top"
    private const val KEY_RIGHT = "right"
    private const val KEY_BOTTOM = "bottom"
    private const val KEY_PAGE = "page"
    private const val KEY_PACKAGE_NAME = "packageName"
    private const val KEY_ACTIVITY_NAME = "activityName"
    private const val KEY_TITLE = "title"
    private const val KEY_TYPE = "type"
    private const val KEY_CLASS_NAME = "className"
    private const val KEY_DOCKED = "docked"

    data class ExportResult(
        val json: String,
        val exportedCount: Int,
        val skippedCount: Int
    )

    data class ImportResult(
        val restoredCount: Int,
        val skippedCount: Int
    )

    /**
     * What [importLayout] does when a restored item wants a cell that is already taken.
     *
     * The three modes cover the two things a layout backup is actually used for: making this
     * device look like the one the file came from ([REPLACE]), and pulling a few icons in
     * without disturbing what is already here ([KEEP_EXISTING], [DISPLACE_EXISTING]). In all
     * three, no two items ever end up on the same cell, which is what the previous
     * add-on-top behaviour could not promise.
     */
    enum class ImportMode {
        /** Current items stay put, a restored item that collides goes to the next free cell. */
        KEEP_EXISTING,

        /** The restored item gets the cell and the current occupant moves to a free one. */
        DISPLACE_EXISTING,

        /** Deletes the current icons and folders first, so the file is reproduced as it is. */
        REPLACE
    }

    fun buildBackupFilename() = "launcher-layout-backup-${System.currentTimeMillis()}.json"

    fun createExportIntent(filename: String) = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        type = BACKUP_MIME_TYPE
        addCategory(Intent.CATEGORY_OPENABLE)
        putExtra(Intent.EXTRA_TITLE, filename)
    }

    fun createImportIntent() = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        type = BACKUP_MIME_TYPE
        addCategory(Intent.CATEGORY_OPENABLE)
    }

    fun exportLayout(context: Context): ExportResult {
        val allItems = context.homeScreenGridItemsDB.getAllItems()
        val exportable = allItems.filter {
            it.type == ITEM_TYPE_ICON || it.type == ITEM_TYPE_FOLDER
        }

        val itemsArray = JSONArray()
        exportable.forEach { item ->
            itemsArray.put(
                JSONObject().apply {
                    put(KEY_TEMP_ID, item.id ?: -1L)
                    put(KEY_PARENT_TEMP_ID, item.parentId ?: JSONObject.NULL)
                    put(KEY_LEFT, item.left)
                    put(KEY_TOP, item.top)
                    put(KEY_RIGHT, item.right)
                    put(KEY_BOTTOM, item.bottom)
                    put(KEY_PAGE, item.page)
                    put(KEY_PACKAGE_NAME, item.packageName)
                    put(KEY_ACTIVITY_NAME, item.activityName)
                    put(KEY_TITLE, item.title)
                    put(KEY_TYPE, item.type)
                    put(KEY_CLASS_NAME, item.className)
                    put(KEY_DOCKED, item.docked)
                }
            )
        }

        val root = JSONObject().apply {
            put(KEY_VERSION, BACKUP_VERSION)
            put(KEY_ITEMS, itemsArray)
        }

        return ExportResult(
            json = root.toString(2),
            exportedCount = exportable.size,
            skippedCount = allItems.size - exportable.size
        )
    }

    /**
     * Restores the icons and folders stored in [json] onto the current home screen, resolving
     * collisions with what is already there as [mode] asks.
     *
     * Restores each item's page, so multiple home screens come back, its cell within that
     * page, its dock flag, and for an item in a folder its slot in that folder. Items are
     * inserted in file order, which is the order they were exported in, so that the order of
     * the icons inside a folder survives the round trip.
     *
     * An item is skipped when its app is no longer installed, when its folder was not
     * restored, when it does not fit this device's grid, so that a backup taken on a larger
     * grid cannot create items that are impossible to reach, or when neither it nor the
     * occupant of the cell it wants can be given a free cell. Throws if [json] is not a
     * backup file this version can read.
     */
    fun importLayout(context: Context, json: String, mode: ImportMode): ImportResult {
        val root = JSONObject(json)
        val version = root.optInt(KEY_VERSION, -1)
        require(version in 1..BACKUP_VERSION) { "unsupported layout backup version $version" }

        val itemsArray = root.optJSONArray(KEY_ITEMS) ?: JSONArray()
        val installedApps = getInstalledApps(context)
        val rowCount = context.config.homeRowCount
        val columnCount = context.config.homeColumnCount
        val itemsDB = context.homeScreenGridItemsDB

        val folders = ArrayList<ParsedItem>()
        val icons = ArrayList<ParsedItem>()
        var skippedCount = sortParsedItems(itemsArray, rowCount, columnCount, folders, icons)

        if (mode == ImportMode.REPLACE) {
            // only what this format exports is dropped: widgets and pinned shortcuts are
            // never in the file, so deleting them would make them unrecoverable
            itemsDB.getAllItems()
                .filter { it.type == ITEM_TYPE_ICON || it.type == ITEM_TYPE_FOLDER }
                .forEach { itemsDB.deleteById(it.id!!) }
        }

        // whatever survived still owns its cells, widgets and shortcuts included
        val grid = GridOccupancy(rowCount, columnCount, itemsDB)
        itemsDB.getAllItems().forEach(grid::occupy)

        // folders are inserted first so that their icons can point at a real parent id
        var restoredCount = 0
        val idMap = HashMap<Long, Long>()
        folders.forEach { (tempId, _, item) ->
            if (grid.place(item, mode)) {
                idMap[tempId] = itemsDB.insert(item)
                restoredCount++
            } else {
                skippedCount++
            }
        }

        icons.forEach { (_, parentTempId, item) ->
            val parentId = parentTempId?.let { idMap[it] }
            // an icon is only usable if its app is here and its folder, if any, was restored
            if (!installedApps.canLaunch(item) || (parentTempId != null && parentId == null)) {
                skippedCount++
                return@forEach
            }

            // an icon in a folder holds a slot in that folder, not a cell on the grid
            if (parentId == null && !grid.place(item, mode)) {
                skippedCount++
                return@forEach
            }

            item.parentId = parentId
            itemsDB.insert(item)
            restoredCount++
        }

        return ImportResult(restoredCount, skippedCount)
    }

    /**
     * Splits the items of a backup file into [folders] and [icons], dropping the ones this
     * device cannot show, and returns how many were dropped.
     */
    private fun sortParsedItems(
        itemsArray: JSONArray,
        rowCount: Int,
        columnCount: Int,
        folders: MutableList<ParsedItem>,
        icons: MutableList<ParsedItem>
    ): Int {
        var skippedCount = 0
        for (i in 0 until itemsArray.length()) {
            val parsed = parseItem(itemsArray.getJSONObject(i))
            when {
                !parsed.fitsGrid(rowCount, columnCount) -> skippedCount++
                parsed.item.type == ITEM_TYPE_FOLDER -> folders.add(parsed)
                parsed.item.type == ITEM_TYPE_ICON -> icons.add(parsed)
                else -> skippedCount++
            }
        }

        return skippedCount
    }

    /** A cell of the home screen. Docked items share one row across all pages, hence [DOCK]. */
    private data class Cell(val page: Int, val left: Int, val top: Int) {
        companion object {
            /** Stands in for the page of a docked item, which is on every page at once. */
            const val DOCK = -1
        }
    }

    /**
     * Which grid cells are taken, so that an import never stacks two items on one cell.
     *
     * Kept in memory and updated as items are placed, so items from the file collide with
     * each other the same way they collide with what was already on the device.
     */
    private class GridOccupancy(
        private val rowCount: Int,
        private val columnCount: Int,
        private val itemsDB: HomeScreenGridItemsDao
    ) {
        private val cells = HashMap<Cell, HomeScreenGridItem>()

        fun occupy(item: HomeScreenGridItem) {
            // an item in a folder is not on the grid at all
            if (item.parentId == null) {
                cellsOf(item).forEach { cells[it] = item }
            }
        }

        /**
         * Gives [item] a cell and returns whether it got one. Sets its position, so the
         * caller only has to insert it.
         */
        fun place(item: HomeScreenGridItem, mode: ImportMode): Boolean {
            val wanted = cellOf(item, item.left, item.getDockAdjustedTop(rowCount))
            val blocker = cells[wanted]
            val target = when {
                blocker == null -> wanted
                // a widget covers several cells and is left alone, so the restored item moves
                mode == ImportMode.KEEP_EXISTING || blocker.type == ITEM_TYPE_WIDGET ->
                    freeCell(item.page, item.docked) ?: return false

                else -> {
                    val movedTo = freeCell(blocker.page, blocker.docked) ?: return false
                    cellsOf(blocker).forEach { cells.remove(it) }
                    moveTo(blocker, movedTo)
                    itemsDB.updateItemPosition(
                        left = blocker.left,
                        top = blocker.top,
                        right = blocker.right,
                        bottom = blocker.bottom,
                        page = blocker.page,
                        docked = blocker.docked,
                        parentId = null,
                        id = blocker.id!!
                    )
                    cells[movedTo] = blocker
                    wanted
                }
            }

            moveTo(item, target)
            cells[target] = item
            return true
        }

        /**
         * First free cell for an item currently on [fromPage]. Later pages are searched too,
         * so a full page spills onto the next one instead of dropping the item, but earlier
         * pages are not, so that restoring keeps the page grouping of the file.
         */
        private fun freeCell(fromPage: Int, docked: Boolean): Cell? {
            if (docked) {
                return (0 until columnCount)
                    .map { Cell(Cell.DOCK, it, rowCount - 1) }
                    .firstOrNull { it !in cells }
            }

            // the dock owns the last row, so it is not searched here
            return (fromPage..MAX_PAGE).firstNotNullOfOrNull { page ->
                (0 until rowCount - 1).firstNotNullOfOrNull { top ->
                    (0 until columnCount)
                        .map { Cell(page, it, top) }
                        .firstOrNull { it !in cells }
                }
            }
        }

        private fun moveTo(item: HomeScreenGridItem, cell: Cell) {
            val width = item.right - item.left
            val height = item.bottom - item.top
            item.left = cell.left
            item.right = cell.left + width
            item.top = cell.top
            item.bottom = cell.top + height
            if (!item.docked) {
                item.page = cell.page
            }
        }

        private fun cellsOf(item: HomeScreenGridItem): List<Cell> {
            val top = item.getDockAdjustedTop(rowCount)
            val bottom = item.getDockAdjustedBottom(rowCount)
            return (item.left..item.right).flatMap { left ->
                (top..bottom).map { cellOf(item, left, it) }
            }
        }

        private fun cellOf(item: HomeScreenGridItem, left: Int, top: Int) = Cell(
            page = if (item.docked) Cell.DOCK else item.page,
            left = left,
            top = top
        )
    }

    private data class ParsedItem(
        val tempId: Long,
        val parentTempId: Long?,
        val item: HomeScreenGridItem
    ) {
        /**
         * Mirrors HomeScreenGrid.outOfBounds(), which silently refuses to draw anything it
         * considers off the grid. An item that this returns false for would be restored into
         * the database and then be invisible and unreachable, so it is skipped instead.
         */
        fun fitsGrid(rowCount: Int, columnCount: Int): Boolean {
            if (parentTempId != null) {
                // inside a folder left is the slot in the folder, not a grid column
                return item.left in 0 until HomeScreenGridItem.FOLDER_MAX_CAPACITY
            }

            // a page below zero is on no page at all, and a page far past the end would add
            // that many page indicators to every frame
            if (item.page !in 0..MAX_PAGE) {
                return false
            }

            if (item.left < 0 || item.right !in item.left until columnCount) {
                return false
            }

            if (item.docked) {
                // a docked item ignores top, bottom and page, it is drawn in the dock row of
                // every page
                return true
            }

            // the dock occupies the last row, so rowCount - 1 is already out of bounds here
            return item.top >= 0 && item.bottom in item.top until rowCount - 1
        }
    }

    private fun parseItem(obj: JSONObject): ParsedItem {
        val item = HomeScreenGridItem(
            id = null,
            left = obj.optInt(KEY_LEFT, -1),
            top = obj.optInt(KEY_TOP, -1),
            right = obj.optInt(KEY_RIGHT, -1),
            bottom = obj.optInt(KEY_BOTTOM, -1),
            page = obj.optInt(KEY_PAGE, 0),
            packageName = obj.optString(KEY_PACKAGE_NAME),
            activityName = obj.optString(KEY_ACTIVITY_NAME),
            title = obj.optString(KEY_TITLE),
            type = obj.optInt(KEY_TYPE, ITEM_TYPE_ICON),
            className = obj.optString(KEY_CLASS_NAME),
            widgetId = -1,
            shortcutId = "",
            docked = obj.optBoolean(KEY_DOCKED, false)
        )

        // icons and folders always cover exactly one cell here, and HomeScreenGrid places
        // them from left and top alone while still testing right and bottom for bounds and
        // occupancy, so a file that disagrees must not claim cells the item does not cover
        if (item.type == ITEM_TYPE_ICON || item.type == ITEM_TYPE_FOLDER) {
            item.right = item.left
            item.bottom = item.top
        }

        return ParsedItem(
            tempId = obj.optLong(KEY_TEMP_ID, -1L),
            parentTempId = if (obj.isNull(KEY_PARENT_TEMP_ID)) {
                null
            } else {
                obj.optLong(KEY_PARENT_TEMP_ID)
            },
            item = item
        )
    }

    // same queryIntentActivities() enumeration MainActivity.getAllAppLaunchers() uses
    private fun getInstalledApps(context: Context): InstalledApps {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val activities = context.packageManager
            .queryIntentActivities(intent, PackageManager.PERMISSION_GRANTED)

        return InstalledApps(
            identifiers = activities
                .map { "${it.activityInfo.applicationInfo.packageName}/${it.activityInfo.name}" }
                .toSet(),
            packageNames = activities
                .map { it.activityInfo.applicationInfo.packageName }
                .toSet()
        )
    }

    private class InstalledApps(
        private val identifiers: Set<String>,
        private val packageNames: Set<String>
    ) {
        /**
         * The launcher stores its own default icons without an activity name (see
         * MainActivity.getDefaultAppPackages) and launches them through
         * getLaunchIntentForPackage, so those can only be matched by package. Items that do
         * carry an activity name are matched on the full
         * "$packageName/$activityName" identifier, the same format
         * HomeScreenGridItem.getItemIdentifier() produces.
         */
        fun canLaunch(item: HomeScreenGridItem) = if (item.activityName.isEmpty()) {
            packageNames.contains(item.packageName)
        } else {
            identifiers.contains(item.getItemIdentifier())
        }
    }
}
