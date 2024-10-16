package org.fossify.home.extensions

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.view.RoundedCorner.POSITION_TOP_LEFT
import android.view.RoundedCorner.POSITION_TOP_RIGHT
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.res.ResourcesCompat
import org.fossify.commons.R
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.helpers.isSPlus

fun View.animateScale(
    from: Float,
    to: Float,
    duration: Long,
) = animate()
    .scaleX(to)
    .scaleY(to)
    .setDuration(duration)
    .setInterpolator(AccelerateDecelerateInterpolator())
    .withStartAction {
        scaleX = from
        scaleY = from
    }

fun View.setupDrawerBackground() {
    val backgroundColor = context.getProperBackgroundColor()
    background = ColorDrawable(backgroundColor)

    val insets = rootWindowInsets
    if (isSPlus() && insets != null) {
        val topRightCorner = insets.getRoundedCorner(POSITION_TOP_RIGHT)?.radius ?: 0
        val topLeftCorner = insets.getRoundedCorner(POSITION_TOP_LEFT)?.radius ?: 0
        if (topRightCorner > 0 && topLeftCorner > 0) {
            background = ResourcesCompat.getDrawable(
                context.resources, R.drawable.bottom_sheet_bg, context.theme
            ).apply {
                (this as LayerDrawable)
                    .findDrawableByLayerId(R.id.bottom_sheet_background)
                    .applyColorFilter(backgroundColor)
            }
        }
    }
}