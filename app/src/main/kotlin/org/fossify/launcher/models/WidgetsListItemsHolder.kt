package org.fossify.launcher.models

open class WidgetsListItemsHolder(val widgets: ArrayList<AppWidget>) : WidgetsListItem() {
    override fun getHashToCompare() = widgets.sumOf { it.getHashToCompare() }
}
