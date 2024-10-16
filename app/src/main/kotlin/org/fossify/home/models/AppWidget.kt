package org.fossify.home.models

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.ActivityInfo
import android.graphics.drawable.Drawable

data class AppWidget(
    var appPackageName: String,
    var appTitle: String,
    val appIcon: Drawable?,
    val widgetTitle: String,
    val widgetPreviewImage: Drawable?,
    var widthCells: Int,
    val heightCells: Int,
    val isShortcut: Boolean,
    val className: String,      // identifier to know which app widget are we using
    val providerInfo: AppWidgetProviderInfo?,       // used at widgets
    val activityInfo: ActivityInfo?                 // used at shortcuts
) : WidgetsListItem() {
    override fun getHashToCompare() = getStringToCompare().hashCode()

    private fun getStringToCompare(): String {
        return copy(appIcon = null, widgetPreviewImage = null).toString()
    }
}
