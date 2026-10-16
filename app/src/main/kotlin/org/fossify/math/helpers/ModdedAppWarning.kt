package org.fossify.math.helpers

import android.annotation.SuppressLint
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * Commons warns "You are using a fake version of the app…" (its OK button opens Fossify's store page) in every app
 * whose package doesn't start with org.fossify: at random in about every 50th screen and at every 100th start, the
 * very first one included. Not true for this app, built from its own source on purpose; on Android 16 the dialog
 * even came up empty and only dimmed the screen. Its window is removed as soon as the screen has been created, before
 * anything is drawn. The window roots are found the same way as in NoTouch.kt.
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
fun Activity.removeModdedAppWarning() {
    val roots = try {
        val globalClass = Class.forName("android.view.WindowManagerGlobal")
        val global = globalClass.getMethod("getInstance").invoke(null)
        val field = globalClass.getDeclaredField("mViews")
        field.isAccessible = true
        (field.get(global) as? List<*>)?.filterIsInstance<View>().orEmpty()
    } catch (ignored: Exception) {
        return
    }

    roots.filter { it != window.decorView && it.showsModdedAppWarning() }.forEach {
        try {
            windowManager.removeViewImmediate(it)
        } catch (ignored: Exception) {
        }
    }
}

private fun View.showsModdedAppWarning(): Boolean = when (this) {
    is TextView -> text?.startsWith(MODDED_APP_WARNING_START) == true
    is ViewGroup -> (0 until childCount).any { getChildAt(it).showsModdedAppWarning() }
    else -> false
}

private const val MODDED_APP_WARNING_START = "You are using a fake version of the app"
