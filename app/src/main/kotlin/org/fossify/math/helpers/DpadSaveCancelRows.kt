package org.fossify.math.helpers

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.fossify.math.R
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getProperTextColor

/**
 * The "Cancel" (red cross) and "Save" (green check) rows under a divider (layout/dpad_save_cancel_rows), used by the
 * dialogs instead of small text buttons in the corner. Returns the Save row, the one to jump to when done.
 */
fun setupDpadSaveCancelRows(rows: View, onCancel: () -> Unit, onSave: () -> Unit): View {
    val context = rows.context
    val textColor = context.getProperTextColor()
    rows.findViewById<ImageView>(R.id.dpad_save_row_icon).applyColorFilter(ContextCompat.getColor(context, R.color.dpad_save_green))
    rows.findViewById<ImageView>(R.id.dpad_cancel_row_icon).applyColorFilter(ContextCompat.getColor(context, R.color.dpad_cancel_red))
    rows.findViewById<TextView>(R.id.dpad_save_row_label).setTextColor(textColor)
    rows.findViewById<TextView>(R.id.dpad_cancel_row_label).setTextColor(textColor)

    val cancelRow = rows.findViewById<View>(R.id.dpad_cancel_row_holder)
    val saveRow = rows.findViewById<View>(R.id.dpad_save_row_holder)
    cancelRow.applyDpadFocusHighlight()
    saveRow.applyDpadFocusHighlight()
    cancelRow.setOnClickListener { onCancel() }
    saveRow.setOnClickListener { onSave() }
    return saveRow
}
