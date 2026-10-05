package org.fossify.math.views

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.math.R
import org.fossify.math.helpers.dpadHighlight

/**
 * Hold '*': the operators and functions as square tiles laid out like the keypad (1 2 3 / 4 5 6 / 7 8 9 / 0). A symbol
 * is picked with the digit at its place, or with the arrows and OK; a new press of '*' or Back closes it.
 *
 * It's a layer over the calculator, not a dialog: it can open while '*' is still held, and a dialog window opened
 * during a key press gets no keys on some Android versions. The activity hands it all keys while it's shown.
 */
class SymbolPalette @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    /** [key] is the digit of its place on the keypad, 0 is the middle of the row under 7 8 9. */
    class Symbol(val label: String, val key: Int, val action: () -> Unit)

    private var symbols: List<Symbol> = emptyList()
    private var tiles: List<View> = emptyList()
    private var selected = 0
    private var onPick: ((Symbol) -> Unit)? = null

    /** Letting go of the '*' that opened it must not close it, only a new press does. */
    private var starPressedHere = false

    val isShowing get() = visibility == View.VISIBLE

    init {
        visibility = View.GONE
        isClickable = true
    }

    fun show(symbols: List<Symbol>, onPick: (Symbol) -> Unit) {
        this.symbols = symbols
        this.onPick = onPick
        selected = 0
        starPressedHere = false
        build()
        beVisible()
    }

    fun hide() {
        beGone()
        removeAllViews()
    }

    /** All keys while the palette is shown; true if used. */
    fun onKey(event: KeyEvent): Boolean {
        val isDown = event.action == KeyEvent.ACTION_DOWN
        val isUp = event.action == KeyEvent.ACTION_UP && !event.isCanceled
        val digit = event.keyCode - KeyEvent.KEYCODE_0
        when (event.keyCode) {
            KeyEvent.KEYCODE_STAR -> {
                if (isDown && event.repeatCount == 0) {
                    starPressedHere = true
                } else if (isUp && starPressedHere) {
                    hide()
                }
            }

            KeyEvent.KEYCODE_BACK -> if (isUp) hide()
            KeyEvent.KEYCODE_DPAD_UP -> if (isDown) move(-1, 0)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (isDown) move(1, 0)
            KeyEvent.KEYCODE_DPAD_LEFT -> if (isDown) move(0, -1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (isDown) move(0, 1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (isUp) pick(symbols[selected])
            }

            else -> if (digit in 0..9) {
                // on key up, like the other keys of the calculator
                if (isUp) symbols.firstOrNull { it.key == digit }?.let { pick(it) }
            }
        }
        // nothing reaches the calculator under it meanwhile
        return true
    }

    private fun pick(symbol: Symbol) {
        hide()
        onPick?.invoke(symbol)
    }

    private fun place(symbol: Symbol) = if (symbol.key == 0) LAST_ROW to 1 else (symbol.key - 1) / COLUMNS to (symbol.key - 1) % COLUMNS

    /** The next tile in that direction; down from 7 or 9 also reaches 0 under 8. */
    private fun move(rowStep: Int, columnStep: Int) {
        val (row, column) = place(symbols[selected])
        val target = if (columnStep != 0) {
            symbols.indexOfFirst { place(it) == row to column + columnStep }
        } else {
            val nextRow = row + rowStep
            symbols.withIndex().filter { place(it.value).first == nextRow }
                .minByOrNull { kotlin.math.abs(place(it.value).second - column) }?.index ?: -1
        }
        if (target >= 0) {
            select(target)
        }
    }

    private fun select(index: Int) {
        tiles.getOrNull(selected)?.foreground = null
        selected = index
        tiles.getOrNull(selected)?.foreground = context.dpadHighlight()
    }

    private fun build() {
        removeAllViews()
        val res = context.resources
        val density = res.displayMetrics.density
        setBackgroundColor(ColorUtils.setAlphaComponent(Color.BLACK, SCRIM_ALPHA))

        val grid = GridLayout(context).apply {
            columnCount = COLUMNS
            useDefaultMargins = false
            val padding = res.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
            setPadding(padding, padding, padding, padding)
            background = GradientDrawable().apply {
                cornerRadius = CARD_CORNER_DP * density
                // a bit lighter than the screen, so the card stands out on a black background too
                setColor(ColorUtils.blendARGB(context.getProperBackgroundColor(), context.getProperTextColor(), CARD_TINT))
            }
        }

        tiles = symbols.map { createTile(it) }
        symbols.forEachIndexed { index, symbol ->
            val (row, column) = place(symbol)
            grid.addView(tiles[index], GridLayout.LayoutParams(GridLayout.spec(row), GridLayout.spec(column)).apply {
                val margin = res.getDimensionPixelSize(R.dimen.dpad_palette_tile_margin)
                setMargins(margin, margin, margin, margin)
            })
        }
        addView(grid, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        select(0)
    }

    private fun createTile(symbol: Symbol): View {
        val size = resources.getDimensionPixelSize(R.dimen.dpad_palette_tile_size)
        val density = resources.displayMetrics.density
        val textColor = context.getProperTextColor()

        return FrameLayout(context).apply {
            minimumWidth = size
            minimumHeight = size
            background = GradientDrawable().apply {
                cornerRadius = TILE_CORNER_DP * density
                setColor(ColorUtils.setAlphaComponent(textColor, TILE_FILL_ALPHA))
                setStroke(density.toInt().coerceAtLeast(1), ColorUtils.setAlphaComponent(textColor, TILE_STROKE_ALPHA))
            }

            addView(TextView(context).apply {
                text = symbol.label
                textSize = SYMBOL_TEXT_SP
                setTextColor(context.getProperPrimaryColor())
                gravity = Gravity.CENTER
            }, LayoutParams(size, size))

            addView(TextView(context).apply {
                text = symbol.key.toString()
                textSize = DIGIT_TEXT_SP
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(textColor)
                alpha = DIGIT_ALPHA
                val padding = (DIGIT_PADDING_DP * density).toInt()
                setPadding(padding, padding / 2, padding, padding)
            }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
        }
    }

    companion object {
        private const val COLUMNS = 3
        private const val LAST_ROW = 3
        private const val SCRIM_ALPHA = 0x99
        private const val CARD_CORNER_DP = 16f
        private const val CARD_TINT = 0.1f
        private const val TILE_CORNER_DP = 10f
        private const val TILE_FILL_ALPHA = 0x14
        private const val TILE_STROKE_ALPHA = 0x40
        private const val SYMBOL_TEXT_SP = 30f
        private const val DIGIT_TEXT_SP = 11f
        private const val DIGIT_ALPHA = 0.55f
        private const val DIGIT_PADDING_DP = 6f
    }
}
