package org.fossify.launcher.models

import android.graphics.drawable.Drawable

data class WidgetsListSection(var appTitle: String, var appIcon: Drawable?) : WidgetsListItem() {
    override fun getHashToCompare() = getStringToCompare().hashCode()

    private fun getStringToCompare(): String {
        return copy(appIcon = null).toString()
    }
}
