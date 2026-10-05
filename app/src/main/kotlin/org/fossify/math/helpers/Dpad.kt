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
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.view.KeyEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
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

/**
 * Round frame for round items (week days, color circles) when focused: an outer ring in the text color and an inner
 * one in the background color, so it stands out on filled and empty circles alike.
 */
fun View.applyDpadRoundFocusHighlight() {
    val density = resources.displayMetrics.density
    val outer = (3 * density).toInt()
    val ring = LayerDrawable(
        arrayOf(
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(outer, context.getProperTextColor())
            },
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke((2 * density).toInt(), context.getProperBackgroundColor())
            }
        )
    ).apply { setLayerInset(1, outer, outer, outer, outer) }

    foreground = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), ring)
        addState(intArrayOf(), ColorDrawable(0))
    }
    setTag(R.id.dpad_focus_prepared, true)
}

fun RecyclerView.focusedAdapterPosition(): Int {
    val focused = findFocus() ?: return RecyclerView.NO_POSITION
    val item = findContainingItemView(focused) ?: return RecyclerView.NO_POSITION
    return getChildAdapterPosition(item)
}

/**
 * Focuses [position] (or the first visible item), scrolling to it and waiting for it to be laid out if needed.
 * Returns false if the list is empty.
 */
fun RecyclerView.focusItem(position: Int = RecyclerView.NO_POSITION): Boolean {
    val count = adapter?.itemCount ?: 0
    if (count == 0 || visibility != View.VISIBLE) {
        return false
    }

    val target = if (position in 0 until count) {
        position
    } else {
        (layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition()?.takeIf { it >= 0 } ?: 0
    }

    if (findViewHolderForAdapterPosition(target) == null) {
        scrollToPosition(target)
    }

    fun tryFocus(attemptsLeft: Int) {
        val holder = findViewHolderForAdapterPosition(target)
        when {
            // requestFocusFromTouch also leaves touch mode, list items can't take focus in it
            holder != null -> holder.itemView.requestFocusFromTouch()
            attemptsLeft > 0 -> postDelayed({ tryFocus(attemptsLeft - 1) }, FOCUS_RETRY_DELAY_MS)
        }
    }

    tryFocus(FOCUS_RETRY_ATTEMPTS)
    return true
}

private const val FOCUS_RETRY_ATTEMPTS = 20
private const val FOCUS_RETRY_DELAY_MS = 50L

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

/**
 * One-line text fields in dialogs keep the up/down arrows for the text cursor, so the focus never got to the rows or
 * buttons below them: move the focus with up/down instead (left/right still move the cursor).
 */
fun View.moveFocusOnUpDown() {
    setOnKeyListener { view, keyCode, event ->
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
            KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
            else -> return@setOnKeyListener false
        }
        val next = view.focusSearch(direction) ?: if (direction == View.FOCUS_DOWN) view.focusSearch(View.FOCUS_FORWARD) else null
        if (next == null || next == view) {
            return@setOnKeyListener false
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            next.requestFocus()
        }
        true
    }
}
