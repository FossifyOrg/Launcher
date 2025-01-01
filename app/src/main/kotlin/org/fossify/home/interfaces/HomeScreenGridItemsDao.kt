package org.fossify.home.interfaces

import androidx.room.*
import org.fossify.home.models.HomeScreenGridItem

@Dao
interface HomeScreenGridItemsDao {
    @Query("SELECT * FROM home_screen_grid_items")
    fun getAllItems(): List<HomeScreenGridItem>

    @Query("SELECT * FROM home_screen_grid_items WHERE parent_id = :folderId")
    fun getFolderItems(folderId: Long): List<HomeScreenGridItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: HomeScreenGridItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<HomeScreenGridItem>)

    @Query("UPDATE home_screen_grid_items SET title = :title WHERE package_name = :packageName")
    fun updateAppTitle(title: String, packageName: String)

    @Query("UPDATE home_screen_grid_items SET title = :title WHERE id = :id")
    fun updateItemTitle(title: String, id: Long): Int

    @Query("UPDATE home_screen_grid_items SET `left` = :left, `top` = :top, `right` = :right, `bottom` = :bottom, `page` = :page, `docked` = :docked , `parent_id` = :parentId WHERE id = :id")
    fun updateItemPosition(left: Int, top: Int, right: Int, bottom: Int, page: Int, docked: Boolean, parentId: Long?, id: Long)

    @Query("UPDATE home_screen_grid_items SET widget_id = :widgetId WHERE id = :id")
    fun updateWidgetId(widgetId: Int, id: Long): Int

    @Query("DELETE FROM home_screen_grid_items WHERE id = :id")
    fun deleteItemById(id: Long)

    @Query("DELETE FROM home_screen_grid_items WHERE parent_id = :id")
    fun deleteItemsWithParentId(id: Long)

    @Transaction
    fun deleteById(id: Long) {
        deleteItemById(id)
        deleteItemsWithParentId(id)
    }

    @Query("DELETE FROM home_screen_grid_items WHERE package_name = :packageName")
    fun deleteItemByPackageName(packageName: String)

    @Query("DELETE FROM home_screen_grid_items WHERE parent_id IN (SELECT id FROM home_screen_grid_items WHERE package_name = :packageName)")
    fun deleteItemsByParentPackageName(packageName: String)

    @Query("UPDATE home_screen_grid_items SET `left` = `left` + :shiftBy WHERE parent_id == :folderId AND `left` > :shiftFrom AND id != :excludingId")
    fun shiftFolderItems(folderId: Long, shiftFrom: Int, shiftBy: Int, excludingId: Long? = null)

    @Query("UPDATE home_screen_grid_items SET `page` = `page` + :shiftBy WHERE `page` > :shiftFrom")
    fun shiftPage(shiftFrom: Int, shiftBy: Int)

    @Transaction
    fun deleteByPackageName(packageName: String) {
        deleteItemByPackageName(packageName)
        deleteItemsByParentPackageName(packageName)
    }
}
