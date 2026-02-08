package org.fossify.home.models

import android.graphics.drawable.Drawable
import androidx.room.*
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.helpers.SORT_BY_TITLE
import org.fossify.commons.helpers.SORT_DESCENDING

@Entity(
    tableName = "apps",
    indices = [(Index(value = ["package_name", "activity_name", "user_serial"], unique = true))]
)
data class AppLauncher(
    @PrimaryKey(autoGenerate = true) var id: Long?,
    @ColumnInfo(name = "title") var title: String,
    @ColumnInfo(name = "package_name") var packageName: String,
    @ColumnInfo(name = "activity_name") var activityName: String,   // some apps create multiple icons, this is needed at clicking them
    @ColumnInfo(name = "user_serial") var userSerial: Long,
    @ColumnInfo(name = "order") var order: Int,
    @ColumnInfo(name = "thumbnail_color") var thumbnailColor: Int,

    @Ignore var drawable: Drawable?
) : Comparable<AppLauncher> {

    constructor() : this(null, "", "", "", 0L, 0, 0, null)

    companion object {
        var sorting = 0
    }

    override fun equals(other: Any?): Boolean {
        if (other !is AppLauncher) return false
        return packageName.equals(other.packageName, true) &&
                activityName.equals(other.activityName, true) &&
                userSerial == other.userSerial
    }

    override fun hashCode(): Int {
        return getLauncherIdentifier().lowercase().hashCode()
    }

    fun getBubbleText() = title

    fun getLauncherIdentifier() = "$packageName/$activityName/$userSerial"

    override fun compareTo(other: AppLauncher): Int {
        var result = when {
            sorting and SORT_BY_TITLE != 0 -> title.normalizeString().lowercase().compareTo(other.title.normalizeString().lowercase())
            else -> {
                if (order > 0 && other.order == 0) {
                    -1
                } else if (order == 0 && other.order > 0) {
                    1
                } else if (order > 0 && other.order > 0) {
                    order.compareTo(other.order)
                } else {
                    title.lowercase().compareTo(other.title.lowercase())
                }
            }
        }

        if (sorting and SORT_DESCENDING != 0) {
            result *= -1
        }

        return result
    }
}
