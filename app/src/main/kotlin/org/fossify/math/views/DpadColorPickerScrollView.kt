package org.fossify.math.views

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import android.widget.ImageView
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.math.R
import org.fossify.math.helpers.applyDpadFocusHighlight

private const val SQUARE_STEPS = 16
private const val HUE_STEPS = 36

private const val STEP_HUE = 0
private const val STEP_SQUARE = 1
private const val STEP_BUTTONS = 2

/**
 * Root of Commons' color picker dialog (text, background and accent colors), whose square and hue bar can only be
 * used by touch. On keypad phones it works in steps, OK (D-pad center) moves on: the arrows move along the hue bar,
 * then the cursor in the square (saturation and brightness), then Cancel / Save rows replace the dialog buttons.
 * Back goes a step back. The arrows "touch" the views, so Commons' own code picks the color and updates the preview.
 */
class DpadColorPickerScrollView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ScrollView(context, attrs) {
    private val title by lazy { findViewById<TextView>(R.id.dpad_color_step_title) }
    private val square by lazy { findViewById<View>(org.fossify.commons.R.id.color_picker_square) }
    private val squareCursor by lazy { findViewById<View>(org.fossify.commons.R.id.color_picker_cursor) }
    private val hue by lazy { findViewById<View>(org.fossify.commons.R.id.color_picker_hue) }
    private val hueCursor by lazy { findViewById<View>(org.fossify.commons.R.id.color_picker_hue_cursor) }
    private val hex by lazy { findViewById<TextView>(org.fossify.commons.R.id.color_picker_new_hex) }
    private val cancelRow by lazy { findViewById<View>(R.id.dpad_cancel_row_holder) }
    private val saveRow by lazy { findViewById<View>(R.id.dpad_save_row_holder) }

    private var step = STEP_HUE

    // the dialog buttons are focused by default (newer Android moves the focus there once the window gets it)
    private var stepFocused = false

    // cursor positions we set, in view coordinates; Commons rounds the color through its hex code after every square
    // touch, which loses small steps of dark colors and the hue of greys, so we don't read our position back from it
    private var squareX = 0f
    private var squareY = 0f
    private var hueY = 0f
    private var positionKnown = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Commons shows the recent colors when there are some, they would be another touch-only row
        findViewById<View>(org.fossify.commons.R.id.recent_colors)?.visibility = View.GONE
        hex.isFocusable = false

        listOf(square, hue).forEach {
            it.isFocusable = true
            // Android's own focus overlay would grey out the colors, the active part gets a frame instead
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.defaultFocusHighlightEnabled = false
            }
        }
        setupRows()
        showStep(STEP_HUE)
        post { hideDialogButtons() }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && !stepFocused) {
            stepFocused = true
            post { activeView().requestFocus() }
        }
    }

    private fun activeView() = when (step) {
        STEP_HUE -> hue
        STEP_SQUARE -> square
        else -> saveRow
    }

    private fun showStep(newStep: Int) {
        step = newStep
        title.setText(if (step == STEP_HUE) R.string.dpad_pick_color else R.string.dpad_pick_shade)
        // only the active part has a frame, so it's clear which one the arrows move
        val frame = GradientDrawable().apply {
            setStroke((2 * resources.displayMetrics.density).toInt(), context.getProperPrimaryColor())
        }
        hue.foreground = if (step == STEP_HUE) frame else null
        square.foreground = if (step == STEP_SQUARE) frame else null
        activeView().requestFocus()
    }

    private fun setupRows() {
        val textColor = context.getProperTextColor()
        findViewById<ImageView>(R.id.dpad_save_row_icon).applyColorFilter(ContextCompat.getColor(context, R.color.dpad_save_green))
        findViewById<ImageView>(R.id.dpad_cancel_row_icon).applyColorFilter(ContextCompat.getColor(context, R.color.dpad_cancel_red))
        findViewById<TextView>(R.id.dpad_save_row_label).setTextColor(textColor)
        findViewById<TextView>(R.id.dpad_cancel_row_label).setTextColor(textColor)
        listOf(cancelRow, saveRow).forEach { it.applyDpadFocusHighlight() }

        // the rows press the dialog's own (hidden) buttons, so Commons saves or discards the color as usual
        saveRow.setOnClickListener { rootView.findViewById<View>(android.R.id.button1)?.performClick() }
        cancelRow.setOnClickListener { rootView.findViewById<View>(android.R.id.button2)?.performClick() }
    }

    private fun hideDialogButtons() {
        (rootView.findViewById<View>(android.R.id.button1)?.parent as? View)?.visibility = View.GONE
        // the hidden OK button had the focus
        activeView().requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val focused = findFocus()
        if (event.keyCode == KeyEvent.KEYCODE_BACK && step != STEP_HUE) {
            if (event.action == KeyEvent.ACTION_UP) {
                showStep(step - 1)
            }
            return true
        }

        if (step == STEP_BUTTONS) {
            // up/down only between the two rows, OK presses the row
            if (isArrow(event.keyCode)) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> cancelRow.requestFocus()
                        KeyEvent.KEYCODE_DPAD_DOWN -> saveRow.requestFocus()
                    }
                }
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        if (focused != square && focused != hue) {
            return super.dispatchKeyEvent(event)
        }

        if (isConfirmKey(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_UP) {
                showStep(step + 1)
            }
            return true
        }

        if (isArrow(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (step == STEP_SQUARE) moveInSquare(event.keyCode) else moveInHue(event.keyCode)
            }
            // the arrows never move the focus away from the active part
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isConfirmKey(keyCode: Int) = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER

    private fun isArrow(keyCode: Int) = keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
        keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN

    private fun syncPosition() {
        if (positionKnown) {
            return
        }
        positionKnown = true
        squareX = squareCursor.x + squareCursor.width / 2f - square.left
        squareY = squareCursor.y + squareCursor.height / 2f - square.top
        hueY = hueCursor.y + hueCursor.height / 2f - hue.top
    }

    private fun moveInSquare(keyCode: Int) {
        val width = square.width.toFloat()
        val height = square.height.toFloat()
        if (width == 0f || height == 0f) {
            return
        }

        syncPosition()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> squareX -= width / SQUARE_STEPS
            KeyEvent.KEYCODE_DPAD_RIGHT -> squareX += width / SQUARE_STEPS
            KeyEvent.KEYCODE_DPAD_UP -> squareY -= height / SQUARE_STEPS
            KeyEvent.KEYCODE_DPAD_DOWN -> squareY += height / SQUARE_STEPS
        }
        squareX = squareX.coerceIn(0f, width)
        squareY = squareY.coerceIn(0f, height)
        apply()
    }

    /** Up/down along the bar; left/right as well, the hue is the only thing to change in this step. */
    private fun moveInHue(keyCode: Int) {
        val height = hue.height.toFloat()
        if (height == 0f) {
            return
        }

        syncPosition()
        val step = height / HUE_STEPS
        hueY += if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -step else step
        hueY = hueY.coerceIn(0f, height)
        apply()
    }

    /**
     * Holds the hue bar while touching the square, like two fingers: while the hue is being dragged Commons doesn't
     * parse its own rounded hex code back, so the hue, saturation and brightness stay exactly where we put them.
     */
    private fun apply() {
        val hueX = hue.width / 2f
        touch(hue, hueX, hueY, MotionEvent.ACTION_DOWN)
        touch(square, squareX, squareY, MotionEvent.ACTION_DOWN)
        touch(square, squareX, squareY, MotionEvent.ACTION_UP)
        touch(hue, hueX, hueY, MotionEvent.ACTION_UP)
    }

    private fun touch(view: View, x: Float, y: Float, action: Int) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        view.dispatchTouchEvent(event)
        event.recycle()
    }
}
