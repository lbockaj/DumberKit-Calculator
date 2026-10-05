package org.fossify.math.helpers

import android.annotation.SuppressLint
import android.app.Activity
import android.view.View
import android.view.Window
import android.view.WindowManager
import org.fossify.math.R
import org.fossify.math.extensions.config

/*
 * The app is used with the keys only, touches are ignored everywhere in it (screens, dialogs, popup menus) so a finger
 * or a pocket can't press anything by accident, unless touch input is allowed in the settings. Other apps opened from
 * here (file picker, dialer…) keep their touch.
 *
 * The windows are made untouchable (FLAG_NOT_TOUCHABLE): touches never reach them, so Android doesn't even switch to
 * touch mode, which would take the D-pad focus and highlight away. Keys work the same.
 */

private const val COVER_INTERVAL_MS = 250L

/** Activity window callback that, while a dialog or popup of this app has the focus, makes those windows untouchable. */
class NoTouchWindowCallback(private val wrapped: Window.Callback, private val activity: Activity) : Window.Callback by wrapped {
    private var hasWindowFocus = true

    // look for new windows as long as one of them has the focus: a dialog can open right from the menu or another
    // dialog, without the activity getting the focus in between
    private val coverWhileUnfocused = object : Runnable {
        override fun run() {
            if (!hasWindowFocus && !activity.isDestroyed) {
                coverOtherWindows()
                activity.window.decorView.postDelayed(this, COVER_INTERVAL_MS)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        wrapped.onWindowFocusChanged(hasFocus)
        hasWindowFocus = hasFocus
        val decor = activity.window.decorView
        decor.removeCallbacks(coverWhileUnfocused)
        if (!hasFocus) {
            decor.post(coverWhileUnfocused)
        }
    }
}

fun Activity.ignoreTouches() {
    applyTouchInputSetting()
    val callback = window.callback ?: return
    if (callback !is NoTouchWindowCallback) {
        window.callback = NoTouchWindowCallback(callback, this)
    }
}

/** The activity window takes touches only with "Allow touch input" on; called again when the setting changes. */
fun Activity.applyTouchInputSetting() {
    if (config.dpadTouchEnabled) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    } else {
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }
}

/**
 * Dialogs and popups have their own windows, which the app doesn't get a direct handle to: find them among the app's
 * window roots (the list Android keeps of them) and make each new one untouchable, also for touches next to it,
 * which would otherwise close it.
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
private fun coverOtherWindows() {
    val roots = try {
        val globalClass = Class.forName("android.view.WindowManagerGlobal")
        val global = globalClass.getMethod("getInstance").invoke(null)
        val field = globalClass.getDeclaredField("mViews")
        field.isAccessible = true
        (field.get(global) as? List<*>)?.toList().orEmpty()
    } catch (ignored: Exception) {
        // not reachable on this Android version: dialogs and popups stay touchable
        return
    }

    roots.filterIsInstance<View>().forEach { root ->
        val params = root.layoutParams as? WindowManager.LayoutParams ?: return@forEach
        if (params.type == WindowManager.LayoutParams.TYPE_BASE_APPLICATION || root.getTag(R.id.dpad_touch_blocker) != null) {
            return@forEach
        }

        root.setTag(R.id.dpad_touch_blocker, true)
        // the dialog's content may still be filled in right after it opens
        root.post { prepareWindowForKeys(root) }
        if (root.context.config.dpadTouchEnabled) {
            return@forEach
        }

        params.flags = (params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL) and
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH.inv()
        try {
            (root.context.getSystemService(Activity.WINDOW_SERVICE) as WindowManager).updateViewLayout(root, params)
        } catch (ignored: Exception) {
        }
    }
}
