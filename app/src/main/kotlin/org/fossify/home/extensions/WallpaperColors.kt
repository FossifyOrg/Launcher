package org.fossify.home.extensions

import android.app.WallpaperColors
import android.os.Build
import androidx.annotation.RequiresApi
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.helpers.DARK_GREY
import org.fossify.commons.helpers.isSPlus

@RequiresApi(Build.VERSION_CODES.O_MR1)
fun WallpaperColors.supportsDarkText(): Boolean {
    return if (isSPlus()) {
        (colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
    } else {
        primaryColor.toArgb().getContrastColor() == DARK_GREY
    }
}
