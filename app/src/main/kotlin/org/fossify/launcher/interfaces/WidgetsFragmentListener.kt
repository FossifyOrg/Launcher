package org.fossify.launcher.interfaces

import org.fossify.launcher.models.AppWidget

interface WidgetsFragmentListener {
    fun onWidgetLongPressed(appWidget: AppWidget)
}
