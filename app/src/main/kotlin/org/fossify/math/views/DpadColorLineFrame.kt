package org.fossify.math.views

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import org.fossify.commons.views.LineColorPicker
import org.fossify.math.helpers.applyDpadFocusHighlight

/**
 * Wraps Commons' touch-only LineColorPicker (used for the primary color and the app icon color): the wrapper takes
 * the D-pad focus and left/right "tap" the neighbouring color stripe, exactly like choosing it with a finger.
 */
class DpadColorLineFrame @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    init {
        isFocusable = true
        focusable = View.FOCUSABLE
        isFocusedByDefault = true
        applyDpadFocusHighlight()
    }

    private val picker get() = getChildAt(0) as? LineColorPicker

    // when the picker inside is hidden (the app icon dialog has one line only), the wrapper isn't a focus stop
    override fun addFocusables(views: ArrayList<View>, direction: Int, focusableMode: Int) {
        if (picker?.visibility == View.VISIBLE) {
            super.addFocusables(views, direction, focusableMode)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) {
            return super.onKeyDown(keyCode, event)
        }

        val picker = picker ?: return super.onKeyDown(keyCode, event)
        val count = picker.childCount
        if (count == 0) {
            return true
        }

        val current = currentIndex(picker)
        val step = if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
        val target = if (current == -1) 0 else (current + step).coerceIn(0, count - 1)
        if (target != current) {
            tapStripe(picker, target)
        }
        return true
    }

    private fun currentIndex(picker: LineColorPicker): Int {
        val color = try {
            picker.getCurrentColor()
        } catch (e: Exception) {
            return -1
        }

        return (0 until picker.childCount).firstOrNull { (picker.getChildAt(it).background as? ColorDrawable)?.color == color } ?: -1
    }

    private fun tapStripe(picker: LineColorPicker, index: Int) {
        // the picker maps x to a stripe with its own whole-number stripe width (width / count), not the real child
        // positions, which drift apart towards the right end; tap the middle of the stripe as the picker sees it
        val stripeWidth = picker.width / picker.childCount
        if (stripeWidth == 0) {
            return
        }
        val x = index * stripeWidth + stripeWidth / 2f
        val y = picker.height / 2f
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x, y, 0)
        picker.dispatchTouchEvent(event)
        event.recycle()
    }
}
