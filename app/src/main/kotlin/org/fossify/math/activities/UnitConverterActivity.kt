package org.fossify.math.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.performHapticFeedback
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.math.R
import org.fossify.math.databinding.ActivityUnitConverterBinding
import org.fossify.math.extensions.config
import org.fossify.math.helpers.CONVERTER_STATE
import org.fossify.math.helpers.CalculatorInput
import org.fossify.math.helpers.applyDpadFocusHighlight
import org.fossify.math.helpers.converters.Converter
import org.fossify.math.helpers.converters.TemperatureConverter
import org.fossify.math.helpers.dpadCompactTopBar
import org.fossify.math.helpers.dpadPopupMenu
import org.fossify.math.views.ConverterView

/**
 * One kind of units. Digits and '#' (decimal point) type the value of the focused row, the other one is computed. ↑/↓
 * pick a unit row, ←/→ change its unit, OK lists all units, '*' swaps the units, hold '*' makes a temperature
 * negative, Back deletes (and closes when nothing is typed), hold Back clears.
 */
class UnitConverterActivity : SimpleActivity(), ConverterView.OnUnitChangedListener {
    companion object {
        const val EXTRA_CONVERTER_ID = "converter_id"
    }

    private val binding by viewBinding(ActivityUnitConverterBinding::inflate)
    private val converterView get() = binding.viewConverter.root
    private lateinit var converter: Converter

    private var starHeld = false
    private var backHeld = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.converterHolder))

        val converter = Converter.ALL.getOrNull(intent.getIntExtra(EXTRA_CONVERTER_ID, 0))
        if (converter == null) {
            finish()
            return
        }
        this.converter = converter

        converterView.setOnUnitChangedListener(this)
        converterView.setConverter(converter)
        binding.unitConverterToolbar.setTitle(converter.nameResId)

        if (savedInstanceState != null) {
            savedInstanceState.getBundle(CONVERTER_STATE)?.also {
                converterView.restoreFromSavedState(it)
            }
        } else {
            val storedState = config.getLastConverterUnits(converter)
            if (storedState != null) {
                converterView.updateUnits(
                    newTopUnit = storedState.topUnit,
                    newBottomUnit = storedState.bottomUnit
                )
            }
        }

        listOf(converterView.topUnitRow, converterView.bottomUnitRow).forEach { row ->
            row.isFocusable = true
            row.applyDpadFocusHighlight()
            // the unit list opens from the keys (on key up), copying by a long press is for touch only
            row.isLongClickable = false
            // both ways: the focused row is the one typed into
            row.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    converterView.setInputRow(row == converterView.topUnitRow)
                }
            }
        }
        // the swap arrow between the rows is only a picture here ('*' swaps)
        binding.viewConverter.swapButton?.apply {
            isFocusable = false
            isClickable = false
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.unitConverterAppbar, NavigationIcon.None)
        dpadCompactTopBar(binding.unitConverterAppbar, null)
        converterView.updateColors()
        binding.converterKeyHints.setTextColor(getProperTextColor())
        binding.converterKeyHints.beVisibleIf(config.dpadShowKeyHints)

        if (config.preventPhoneFromSleeping) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        val focused = currentFocus
        if (focused != converterView.topUnitRow && focused != converterView.bottomUnitRow) {
            converterView.topUnitRow.requestFocusFromTouch()
        }
    }

    override fun onPause() {
        super.onPause()
        if (config.preventPhoneFromSleeping) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle(CONVERTER_STATE, converterView.saveState())
    }

    override fun onUnitsChanged(topUnit: Converter.Unit?, bottomUnit: Converter.Unit?) {}

    /** Negative values only make sense for °C and °F (Kelvin and Rankine start at absolute zero). */
    private fun canBeNegative(): Boolean {
        return converter.key == TemperatureConverter.key && when (converterView.currentFocusedUnit?.key) {
            TemperatureConverter.Unit.Kelvin.key, TemperatureConverter.Unit.Rankine.key -> false
            else -> true
        }
    }

    // Back is handled as a key on purpose (it deletes), predictive back is off in the manifest
    @SuppressLint("GestureBackNavigation")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isDown = event.action == KeyEvent.ACTION_DOWN
        val isFirstDown = isDown && event.repeatCount == 0
        val isUp = event.action == KeyEvent.ACTION_UP && !event.isCanceled
        val isTopRowFocused = currentFocus != converterView.bottomUnitRow

        when (val keyCode = event.keyCode) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                if (isFirstDown) typed { converterView.typeDigit(keyCode - KeyEvent.KEYCODE_0) }
            }

            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> {
                if (isFirstDown) typed { converterView.typeDigit(keyCode - KeyEvent.KEYCODE_NUMPAD_0) }
            }

            KeyEvent.KEYCODE_POUND, KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_NUMPAD_DOT -> {
                if (isFirstDown) typed { converterView.typeDecimalPoint() }
            }

            KeyEvent.KEYCODE_STAR -> when {
                isFirstDown -> starHeld = false
                isDown && !starHeld -> {
                    starHeld = true
                    if (canBeNegative()) typed { converterView.toggleNegative() }
                }
                isUp && !starHeld -> typed { converterView.switch() }
            }

            KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> {
                if (isFirstDown && canBeNegative()) typed { converterView.toggleNegative() }
            }

            KeyEvent.KEYCODE_BACK -> when {
                isFirstDown -> backHeld = false
                isDown && !backHeld -> {
                    backHeld = true
                    typed { converterView.clear() }
                }
                isUp && !backHeld -> if (converterView.isEmpty) finish() else typed { converterView.deleteCharacter() }
            }

            KeyEvent.KEYCODE_DEL -> if (isDown) typed { converterView.deleteCharacter() }

            KeyEvent.KEYCODE_DPAD_LEFT -> if (isDown) converterView.changeUnit(isTopRowFocused, -1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (isDown) converterView.changeUnit(isTopRowFocused, 1)

            KeyEvent.KEYCODE_DPAD_UP -> if (isDown) converterView.topUnitRow.requestFocus()
            KeyEvent.KEYCODE_DPAD_DOWN -> if (isDown) converterView.bottomUnitRow.requestFocus()

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (isUp) converterView.openUnitList(isTopRowFocused)
            }

            KeyEvent.KEYCODE_MENU -> if (isUp) showMenu()

            else -> return super.dispatchKeyEvent(event)
        }
        return true
    }

    private fun typed(change: () -> Unit) {
        change()
        if (config.vibrateOnButtonPress) {
            binding.root.performHapticFeedback()
        }
    }

    /** The converted (lower) value goes to the calculator tab, like a result to compute with. */
    private fun useInCalculator() {
        val value = converterView.computedValue() ?: return
        Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_VALUE, CalculatorInput.toExpressionText(value))
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(this)
        }
        finish()
    }

    private fun showMenu() {
        val items = buildList {
            add(R.string.swap_units to { converterView.switch() })
            if (canBeNegative()) {
                add(R.string.dpad_function_sign to { converterView.toggleNegative() })
            }
            add(R.string.dpad_use_in_calculator to ::useInCalculator)
            add(R.string.dpad_clear_value to { converterView.clear() })
        }

        dpadPopupMenu(binding.unitConverterToolbar).apply {
            items.forEachIndexed { index, (title, _) -> menu.add(0, index, index, title) }
            setOnMenuItemClickListener {
                items[it.itemId].second()
                true
            }
            show()
        }
    }
}
