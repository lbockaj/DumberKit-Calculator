package org.fossify.math.helpers

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.math.R

/**
 * Dialogs from Commons (radio lists, sort options, alarm sounds…) were made for touch: their rows don't show the
 * focus and the focus starts on a button. Every new dialog window is prepared once (see NoTouch.kt): clickable rows
 * get the focus highlight, and if nothing else was focused yet, the checked option (or the first row) gets the focus.
 * Dialog buttons keep their own style (dpad_dialog_buttons.xml), only the focused one gets the text color: its label
 * in the primary color was hard to read on the tinted background. Our own dialogs set up their focus themselves.
 */
fun prepareWindowForKeys(root: View) {
    val rows = ArrayList<View>()
    fun collect(view: View) {
        if (!view.isShown) {
            return
        }

        // lists, pickers and popup menus handle their own items
        if (view is AbsListView || view is RecyclerView || view is NumberPicker) {
            return
        }

        // radio buttons and checkboxes are buttons too, the dialog's own buttons are not rows
        val isDialogButton = view is Button && view !is CompoundButton
        if (isDialogButton && view.getTag(R.id.dpad_focus_prepared) == null) {
            val button = view as Button
            button.setTag(R.id.dpad_focus_prepared, true)
            button.setTextColor(
                ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                    intArrayOf(button.context.getProperTextColor(), button.currentTextColor)
                )
            )
        }
        if (view.isClickable && !isDialogButton && view !is EditText && view.getTag(R.id.dpad_focus_prepared) == null) {
            rows.add(view)
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collect(view.getChildAt(i))
            }
        }
    }
    collect(root)

    rows.forEach {
        it.setTag(R.id.dpad_focus_prepared, true)
        it.isFocusable = true
        it.applyDpadFocusHighlight()
    }

    val focused = root.findFocus()
    // the dialog's scroll container can take the focus first (unit list), it doesn't show where the keys are
    if (focused == null || focused is Button || focused is ScrollView || focused is NestedScrollView) {
        val target = rows.firstOrNull { it is CompoundButton && it.isChecked } ?: rows.firstOrNull()
        target?.requestFocus()
    }
}
