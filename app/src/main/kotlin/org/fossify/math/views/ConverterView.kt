package org.fossify.math.views

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.TextViewCompat
import me.grantland.widget.AutofitHelper
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.MEDIUM_ALPHA_INT
import org.fossify.commons.models.RadioItem
import org.fossify.math.R
import org.fossify.math.databinding.ViewConverterBinding
import org.fossify.math.extensions.config
import org.fossify.math.helpers.BOTTOM_UNIT
import org.fossify.math.helpers.CONVERTER_VALUE
import org.fossify.math.helpers.NumberFormatHelper
import org.fossify.math.helpers.TOP_UNIT
import org.fossify.math.helpers.converters.Converter
import org.fossify.math.helpers.converters.TemperatureConverter
import java.math.BigDecimal
import kotlin.reflect.KMutableProperty0

class ConverterView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private lateinit var binding: ViewConverterBinding

    private var converter: Converter? = null
    private var topUnit: Converter.Unit? = null
    private var bottomUnit: Converter.Unit? = null

    private val formatter = NumberFormatHelper()

    private val decimalSeparator: String get() = formatter.decimalSeparator
    private val groupingSeparator: String get() = formatter.groupingSeparator

    private var unitChangedListener: OnUnitChangedListener? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = ViewConverterBinding.bind(this)

        AutofitHelper.create(binding.topUnitText)
        AutofitHelper.create(binding.bottomUnitText)

        binding.swapButton?.setOnClickListener { switch() }

        binding.topUnitHolder.setClickListenerForUnitSelector(::topUnit, ::bottomUnit)
        binding.bottomUnitHolder.setClickListenerForUnitSelector(::bottomUnit, ::topUnit)
        binding.topUnitHolder.setOnLongClickListener {
            context.copyToClipboard(binding.topUnitText.text.toString())
            true
        }
        binding.bottomUnitHolder.setOnLongClickListener {
            context.copyToClipboard(binding.bottomUnitText.text.toString())
            true
        }

        updateColors()
    }

    fun setConverter(converter: Converter) {
        this.converter = converter
        topUnit = converter.defaultTopUnit
        bottomUnit = converter.defaultBottomUnit

        binding.topUnitText.text = "0"
        updateBottomValue()
        updateUnitLabelsAndSymbols()
        notifyUnitsChanged()
    }

    fun setOnUnitChangedListener(listener: OnUnitChangedListener?) {
        unitChangedListener = listener
    }

    private fun notifyUnitsChanged() {
        unitChangedListener?.onUnitsChanged(topUnit, bottomUnit)
    }

    fun updateColors() {
        listOf(
            binding.topUnitText,
            binding.bottomUnitText,
            binding.topUnitName,
            binding.bottomUnitName
        ).forEach {
            it.setTextColor(context.getProperTextColor())
        }
        listOf(binding.topUnitName, binding.bottomUnitName).forEach {
            TextViewCompat.setCompoundDrawableTintList(
                it,
                ColorStateList.valueOf(context.getProperPrimaryColor())
            )
        }

        // keypad phones: no colored block behind the upper row, the focus frame shows which row is picked
        binding.topUnitHolder.background = null
        binding.bottomUnitHolder.background = null
        binding.swapButton?.applyColorFilter(context.getProperPrimaryColor())

        listOf(binding.topUnitSymbol, binding.bottomUnitSymbol).forEach {
            val drawable = ResourcesCompat.getDrawable(
                resources, org.fossify.commons.R.drawable.pill_background, context.theme
            )?.constantState?.newDrawable()?.mutate() as RippleDrawable
            val bgLayerList = drawable.findDrawableByLayerId(
                org.fossify.commons.R.id.button_pill_background_holder
            ) as LayerDrawable
            val bgLayer = bgLayerList.findDrawableByLayerId(
                org.fossify.commons.R.id.button_pill_background_shape
            ) as GradientDrawable
            bgLayer.cornerRadius = context.resources.getDimension(
                org.fossify.commons.R.dimen.rounded_corner_radius_big
            )
            it.background = drawable
            it.background?.alpha = MEDIUM_ALPHA_INT
        }
    }

    fun clear() {
        binding.topUnitText.text = "0"
        updateBottomValue()
    }

    fun deleteCharacter() {
        try {
            val currentText = binding.topUnitText.text.toString()
            if (currentText.length == 1) {
                binding.topUnitText.text = "0"
            } else {
                if (currentText.startsWith("-") && currentText.length == 2) {
                    binding.topUnitText.text = "0"
                } else {
                    var newValue = currentText.dropLast(1)
                    newValue = newValue.trimEnd(groupingSeparator.single())
                    if (newValue == "-" || newValue.isEmpty()) {
                        newValue = "0"
                    }
                    @Suppress("SwallowedException")
                    val value = try {
                        formatter.removeGroupingSeparator(newValue).toBigDecimal()
                    } catch (_: NumberFormatException) {
                        // Return zero if input cannot be parsed as a valid number
                        BigDecimal.ZERO
                    }
                    binding.topUnitText.text = formatter.bigDecimalToString(value)
                }
            }
            updateBottomValue()
        } catch (_: Exception) {
            binding.topUnitText.text = "0"
            binding.bottomUnitText.text = "0"
        }
    }

    fun typeDigit(digit: Int) {
        if (digit == 0) zeroClicked() else addDigit(digit)
        updateBottomValue()
    }

    fun typeDecimalPoint() {
        decimalClicked()
        updateBottomValue()
    }

    /** ←/→ on a unit row: the previous / next unit of the list (going round), skipping the other row's unit. */
    fun changeUnit(top: Boolean, step: Int) {
        val converter = converter ?: return
        val property = if (top) ::topUnit else ::bottomUnit
        val other = if (top) ::bottomUnit else ::topUnit
        val units = converter.units
        var index = (units.indexOf(property.get()) + step).mod(units.size)
        if (units[index] == other.get()) {
            index = (index + step).mod(units.size)
        }
        property.set(units[index])
        updateBottomValue()
        updateUnitLabelsAndSymbols()
        notifyUnitsChanged()
        context.config.putLastConverterUnits(converter, topUnit!!, bottomUnit!!)
    }

    /** OK on a unit row: the whole list of units to pick from. */
    fun openUnitList(top: Boolean) {
        (if (top) binding.topUnitHolder else binding.bottomUnitHolder).performClick()
    }

    val topUnitRow: View get() = binding.topUnitHolder
    val bottomUnitRow: View get() = binding.bottomUnitHolder

    val currentConverter: Converter? get() = converter
    val currentTopUnit: Converter.Unit? get() = topUnit

    /** The computed lower value, null if there's none (nothing typed yet). */
    fun bottomValue(): BigDecimal? = try {
        formatter.removeGroupingSeparator(binding.bottomUnitText.text.toString()).toBigDecimal()
    } catch (_: NumberFormatException) {
        null
    }

    val isEmpty get() = binding.topUnitText.text.toString() == "0"

    private fun decimalClicked() {
        var value = binding.topUnitText.text.toString()
        if (!value.contains(decimalSeparator)) {
            when (value) {
                "0" -> value = "0$decimalSeparator"
                "" -> value += "0$decimalSeparator"
                else -> value += decimalSeparator
            }

            binding.topUnitText.text = value
        }
    }

    private fun zeroClicked() {
        val value = binding.topUnitText.text
        if (value != "0" || value.contains(decimalSeparator)) {
            addDigit(0)
        }
    }

    private fun addDigit(digit: Int) {
        var value = binding.topUnitText.text.toString()
        if (value == "0") {
            value = digit.toString()
        } else {
            value += digit
        }
        binding.topUnitText.text = formatter.formatForDisplay(value)
    }

    fun switch() {
        ::topUnit.swapWith(::bottomUnit)
        updateBottomValue()
        updateUnitLabelsAndSymbols()
        notifyUnitsChanged()
        if (converter != null) {
            context.config.putLastConverterUnits(converter!!, topUnit!!, bottomUnit!!)
        }
    }

    private fun updateUnitLabelsAndSymbols() {
        binding.topUnitName.text = topUnit?.nameResId?.let { context.getString(it) }
        binding.bottomUnitName.text = bottomUnit?.nameResId?.let { context.getString(it) }

        binding.topUnitSymbol.text = topUnit?.symbolResId?.let { context.getString(it) }
        binding.bottomUnitSymbol.text = bottomUnit?.symbolResId?.let { context.getString(it) }

        binding.topUnitSymbol.layoutParams.width = LayoutParams.WRAP_CONTENT
        binding.bottomUnitSymbol.layoutParams.width = LayoutParams.WRAP_CONTENT

        val symbolHeight = context.resources.getDimensionPixelSize(R.dimen.unit_symbol_size)
        binding.topUnitSymbol.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(symbolHeight, MeasureSpec.EXACTLY)
        )
        binding.bottomUnitSymbol.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(symbolHeight, MeasureSpec.EXACTLY)
        )

        val width = listOf(
            symbolHeight,
            binding.topUnitSymbol.measuredWidth,
            binding.bottomUnitSymbol.measuredWidth
        ).max()
        binding.topUnitSymbol.layoutParams.width = width
        binding.bottomUnitSymbol.layoutParams.width = width
        binding.topUnitSymbol.requestLayout()
        binding.bottomUnitSymbol.requestLayout()
    }

    private fun updateBottomValue() {
        converter?.apply {
            val rawText = binding.topUnitText.text.toString()
            val clampedText = checkTemperatureLimits(rawText)
            if (clampedText != rawText) {
                binding.topUnitText.text = clampedText
            }

            @Suppress("SwallowedException")
            val topValue = try {
                formatter.removeGroupingSeparator(clampedText).toBigDecimal()
            } catch (_: NumberFormatException) {
                // Return zero if input cannot be parsed as a valid number
                BigDecimal.ZERO
            }

            if (key == TemperatureConverter.key) {
                when (topUnit?.key) {
                    TemperatureConverter.Unit.Kelvin.key,
                    TemperatureConverter.Unit.Rankine.key -> {
                        if (topValue < BigDecimal.ZERO) {
                            binding.topUnitText.text = "0"
                            return
                        }
                    }
                }
            }

            // For unit conversion, now using BigDecimal throughout
            val converted = convert(topUnit!!.withValue(topValue), bottomUnit!!).value
            binding.bottomUnitText.text = formatter.bigDecimalToString(converted)
        }
    }

    private fun <T> KMutableProperty0<T>.swapWith(other: KMutableProperty0<T>) {
        this.get().also {
            this.set(other.get())
            other.set(it)
        }
    }

    private fun View.setClickListenerForUnitSelector(
        propertyToChange: KMutableProperty0<Converter.Unit?>,
        otherProperty: KMutableProperty0<Converter.Unit?>
    ) {
        setOnClickListener {
            val items = ArrayList(converter!!.units.mapIndexed { index, unit ->
                RadioItem(index, unit.getNameWithSymbol(context), unit)
            })
            RadioGroupDialog(
                context as Activity,
                items,
                converter!!.units.indexOf(propertyToChange.get())
            ) {
                val unit = it as Converter.Unit
                if (unit == otherProperty.get()) {
                    switch()
                } else if (unit != propertyToChange.get()) {
                    propertyToChange.set(unit)
                    updateBottomValue()
                    notifyUnitsChanged()
                }
                updateUnitLabelsAndSymbols()
                context.config.putLastConverterUnits(converter!!, topUnit!!, bottomUnit!!)
            }
        }
    }

    fun saveState(): Bundle = Bundle().apply {
        putInt(TOP_UNIT, converter!!.units.indexOf(topUnit!!))
        putInt(BOTTOM_UNIT, converter!!.units.indexOf(bottomUnit!!))
        putString(CONVERTER_VALUE, binding.topUnitText.text.toString())
    }

    fun restoreFromSavedState(state: Bundle) {
        binding.topUnitText.text = state.getString(CONVERTER_VALUE)
        val storedTopUnit = converter!!.units[state.getInt(TOP_UNIT)]
        val storedBottomUnit = converter!!.units[state.getInt(BOTTOM_UNIT)]
        updateUnits(storedTopUnit, storedBottomUnit)
    }

    fun updateUnits(newTopUnit: Converter.Unit, newBottomUnit: Converter.Unit) {
        topUnit = newTopUnit
        bottomUnit = newBottomUnit

        updateBottomValue()
        updateUnitLabelsAndSymbols()
        notifyUnitsChanged()
    }

    fun toggleNegative() {
        var value = binding.topUnitText.text.toString()

        value = when {
            value == "0" -> "-"
            value.startsWith("-") -> value.substring(1)
            else -> "-$value"
        }

        binding.topUnitText.text = value
        updateBottomValue()
    }

    private fun checkTemperatureLimits(value: String): String {
        if (converter?.key != TemperatureConverter.key) return value

        @Suppress("SwallowedException")
        val numericValue = try {
            formatter.removeGroupingSeparator(value).toBigDecimal()
        } catch (_: NumberFormatException) {
            // Return original value if it cannot be parsed as a valid number
            return value
        }

        return when (topUnit?.key) {
            TemperatureConverter.Unit.Celsius.key -> {
                val minCelsius = BigDecimal("-273.15")
                if (numericValue < minCelsius) formatter.bigDecimalToString(minCelsius) else value
            }

            TemperatureConverter.Unit.Fahrenheit.key -> {
                val minFahrenheit = BigDecimal("-459.67")
                if (numericValue < minFahrenheit) {
                    formatter.bigDecimalToString(minFahrenheit)
                } else {
                    value
                }
            }

            TemperatureConverter.Unit.Kelvin.key,
            TemperatureConverter.Unit.Rankine.key -> {
                if (numericValue < BigDecimal.ZERO) "0" else value
            }

            else -> value
        }
    }

    interface OnUnitChangedListener {
        fun onUnitsChanged(topUnit: Converter.Unit?, bottomUnit: Converter.Unit?)
    }
}
