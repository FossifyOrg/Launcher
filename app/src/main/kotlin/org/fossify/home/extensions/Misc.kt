package org.fossify.home.extensions

import android.content.pm.ShortcutInfo

fun ShortcutInfo?.getLabel() = this?.longLabel?.toString().ifNullOrEmpty { this?.shortLabel?.toString() } ?: ""

private fun String?.ifNullOrEmpty(block: () -> String?) = this?.ifEmpty { block() } ?: block()
