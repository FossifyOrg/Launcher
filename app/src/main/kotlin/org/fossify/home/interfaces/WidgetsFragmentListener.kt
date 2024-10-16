package org.fossify.home.interfaces

import org.fossify.home.models.AppWidget

interface WidgetsFragmentListener {
    fun onWidgetLongPressed(appWidget: AppWidget)
}
