package org.fossify.home.models

import android.graphics.drawable.Drawable
import androidx.room.*

@Entity(tableName = "hidden_icons", indices = [(Index(value = ["id"], unique = true))])
data class HiddenIcon(
    @PrimaryKey(autoGenerate = true) var id: Long?,
    @ColumnInfo(name = "package_name") var packageName: String,
    @ColumnInfo(name = "activity_name") var activityName: String,
    @ColumnInfo(name = "title") var title: String,

    @Ignore var drawable: Drawable? = null,
) {
    constructor() : this(null, "", "", "", null)

    fun getIconIdentifier() = "$packageName/$activityName"
}
