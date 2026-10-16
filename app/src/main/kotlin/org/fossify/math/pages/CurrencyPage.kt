package org.fossify.math.pages

import android.text.format.DateFormat
import android.view.KeyEvent
import android.view.View
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.performHapticFeedback
import org.fossify.commons.extensions.toast
import org.fossify.math.R
import org.fossify.math.activities.SimpleActivity
import org.fossify.math.databinding.PageCurrencyBinding
import org.fossify.math.extensions.config
import org.fossify.math.helpers.CurrencyRates
import org.fossify.math.helpers.applyDpadFocusHighlight
import org.fossify.math.helpers.converters.CurrencyConverter
import org.fossify.math.helpers.dpadPopupMenu
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

/**
 * The currency tab: the unit converter's two rows with currencies at the ECB's rates. Digits and '#' type the amount
 * of the selected row, ↑/↓ pick a row, OK lists all currencies, '*' swaps them, Back deletes (with nothing typed:
 * back to the calculator), hold Back clears. ←/→ stay the tab switch here, so currencies change through OK.
 * The rates are downloaded when the tab is shown and the kept ones are a few hours old, or from the menu.
 */
class CurrencyPage(
    private val activity: SimpleActivity,
    private val binding: PageCurrencyBinding,
    /** The menu pops up at the top bar, like on the other screens. */
    private val menuAnchor: View,
    private val onLeave: () -> Unit,
    private val onUseInCalculator: (BigDecimal) -> Unit
) {
    private val converterView get() = binding.currencyConverter.root
    private var rates = CurrencyRates.load(activity)
    private var updating = false

    private var starHeld = false
    private var backHeld = false

    init {
        val converter = CurrencyConverter(rates.perEuro)
        converterView.setConverter(converter)
        try {
            activity.config.getLastConverterUnits(converter)?.let { converterView.updateUnits(it.topUnit, it.bottomUnit) }
        } catch (ignored: Exception) {
            // a currency the ECB doesn't list anymore: the default pair
        }

        listOf(converterView.topUnitRow, converterView.bottomUnitRow).forEach { row ->
            row.isFocusable = true
            row.applyDpadFocusHighlight()
            row.isLongClickable = false
            row.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    converterView.setInputRow(row == converterView.topUnitRow)
                }
            }
        }
        binding.currencyConverter.swapButton?.apply {
            isFocusable = false
            isClickable = false
        }
    }

    fun refresh() {
        converterView.updateColors()
        val textColor = activity.getProperTextColor()
        binding.currencyStatus.setTextColor(textColor)
        binding.currencyKeyHints.setTextColor(textColor)
        binding.currencyKeyHints.beVisibleIf(activity.config.dpadShowKeyHints)
        showStatus()
    }

    /** The tab is shown: the upper row focused, and fresh rates if the kept ones are a few hours old. */
    fun onShown() {
        val focused = activity.currentFocus
        if (focused != converterView.topUnitRow && focused != converterView.bottomUnitRow) {
            converterView.topUnitRow.requestFocusFromTouch()
        }
        if (CurrencyRates.isOld(rates)) {
            update(manual = false)
        }
    }

    private fun update(manual: Boolean) {
        if (updating) {
            return
        }
        updating = true
        CurrencyRates.refresh(activity) { newRates ->
            updating = false
            if (activity.isDestroyed) {
                return@refresh
            }
            if (newRates != null) {
                rates = newRates
                converterView.replaceConverter(CurrencyConverter(newRates.perEuro))
            } else if (manual) {
                activity.toast(R.string.dpad_rates_update_failed)
            }
            showStatus()
        }
    }

    /**
     * One line, "ECB rates from 16 Oct 2026 20:33": when they were last downloaded, or the ECB's date for the ones
     * that came with the app.
     */
    private fun showStatus() {
        val time = if (rates.checkedAt > 0) {
            val checked = Date(rates.checkedAt)
            "${DateFormat.getMediumDateFormat(activity).format(checked)} ${DateFormat.getTimeFormat(activity).format(checked)}"
        } else {
            try {
                LocalDate.parse(rates.date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            } catch (ignored: Exception) {
                rates.date
            }
        }
        binding.currencyStatus.text = activity.getString(R.string.dpad_rates_from, time)
    }

    /** The keys of the tab (←/→ are the tab switch, handled before). True if used. */
    fun onKey(event: KeyEvent): Boolean {
        val isDown = event.action == KeyEvent.ACTION_DOWN
        val isFirstDown = isDown && event.repeatCount == 0
        val isUp = event.action == KeyEvent.ACTION_UP && !event.isCanceled
        val isTopRowFocused = activity.currentFocus != converterView.bottomUnitRow

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
                isDown -> starHeld = true
                isUp && !starHeld -> typed { converterView.switch() }
            }

            KeyEvent.KEYCODE_BACK -> when {
                isFirstDown -> backHeld = false
                isDown && !backHeld -> {
                    backHeld = true
                    typed { converterView.clear() }
                }
                isUp && !backHeld -> if (converterView.isEmpty) onLeave() else typed { converterView.deleteCharacter() }
            }

            KeyEvent.KEYCODE_DEL -> if (isDown) typed { converterView.deleteCharacter() }
            KeyEvent.KEYCODE_DPAD_UP -> if (isDown) converterView.topUnitRow.requestFocus()
            KeyEvent.KEYCODE_DPAD_DOWN -> if (isDown) converterView.bottomUnitRow.requestFocus()

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (isUp) converterView.openUnitList(isTopRowFocused)
            }

            KeyEvent.KEYCODE_MENU -> if (isUp) showMenu()
            else -> return false
        }
        return true
    }

    private fun typed(change: () -> Unit) {
        change()
        if (activity.config.vibrateOnButtonPress) {
            binding.root.performHapticFeedback()
        }
    }

    private fun showMenu() {
        val items = listOf(
            R.string.dpad_swap_currencies to { converterView.switch() },
            R.string.dpad_update_rates to { update(manual = true) },
            R.string.dpad_use_in_calculator to { converterView.computedValue()?.let(onUseInCalculator) ?: Unit },
            R.string.dpad_clear_value to { converterView.clear() }
        )

        activity.dpadPopupMenu(menuAnchor).apply {
            items.forEachIndexed { index, (title, _) -> menu.add(0, index, index, title) }
            setOnMenuItemClickListener {
                items[it.itemId].second()
                true
            }
            show()
        }
    }
}
