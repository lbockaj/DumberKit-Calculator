package org.fossify.math.helpers

import android.content.Context
import android.graphics.Color
import android.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.core.widget.NestedScrollView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getColoredMaterialStatusBarColor
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.views.MyAppBarLayout
import org.fossify.commons.extensions.getProperBackgroundColor
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.math.R

/**
 * Helpers for keypad / D-pad phones (Qin F21, Dumber Mini, Doov R77 Pro…).
 */

/** Clearly visible highlight for the focused item, used as a View foreground. */
fun Context.dpadFocusForeground(): Drawable {
    return StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), dpadHighlight())
        addState(intArrayOf(), ColorDrawable(0))
    }
}

/** The highlight frame itself, also for items marked by the app rather than focused (the history entry on ↑/↓). */
fun Context.dpadHighlight(): Drawable {
    val primary = getProperPrimaryColor()
    val strokeWidth = (2 * resources.displayMetrics.density).toInt().coerceAtLeast(2)
    return GradientDrawable().apply {
        setColor(ColorUtils.setAlphaComponent(primary, 0x38))
        setStroke(strokeWidth, primary)
        cornerRadius = 4 * resources.displayMetrics.density
    }
}

fun View.applyDpadFocusHighlight() {
    foreground = context.dpadFocusForeground()
    setTag(R.id.dpad_focus_prepared, true)
}

/** Light or dark theme for popups/dialogs, following the app's background color (or Material You). */
fun Context.isDpadDarkTheme(): Boolean = getProperBackgroundColor().getContrastColor() == Color.WHITE

fun Context.dpadPopupTheme(): Int = if (isDpadDarkTheme()) R.style.DpadPopupOverlay_Dark else R.style.DpadPopupOverlay_Light

/** A popup menu matching the app's light/dark colors (the plain one is always dark). */
fun Context.dpadPopupMenu(anchor: View) = PopupMenu(ContextThemeWrapper(this, dpadPopupTheme()), anchor)

/**
 * Top bar for keypad phones: always filled with the colored top bar color (instead of turning colored only when
 * scrolled), 40dp high with a smaller title, and no back arrow (the phone has a Back key).
 */
fun BaseSimpleActivity.dpadCompactTopBar(appBar: MyAppBarLayout, scrollingView: NestedScrollView?) {
    scrollingView?.setOnScrollChangeListener(null as NestedScrollView.OnScrollChangeListener?)
    appBar.toolbar?.apply {
        navigationIcon = null
        layoutParams = layoutParams.apply { height = resources.getDimensionPixelSize(R.dimen.dpad_toolbar_height) }
        minimumHeight = 0
        setTitleTextAppearance(context, R.style.DpadTopBarTitle)
    }
    updateTopBarColors(appBar, getColoredMaterialStatusBarColor())
}
