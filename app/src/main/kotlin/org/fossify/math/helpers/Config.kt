package org.fossify.math.helpers

import android.content.Context
import org.fossify.commons.helpers.BaseConfig
import org.fossify.math.helpers.converters.Converter
import org.fossify.math.models.ConverterUnitsState

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    var dpadTouchEnabled: Boolean
        get() = prefs.getBoolean(DPAD_TOUCH_ENABLED, false)
        set(dpadTouchEnabled) = prefs.edit().putBoolean(DPAD_TOUCH_ENABLED, dpadTouchEnabled).apply()

    var dpadShowKeyHints: Boolean
        get() = prefs.getBoolean(DPAD_SHOW_KEY_HINTS, true)
        set(dpadShowKeyHints) = prefs.edit().putBoolean(DPAD_SHOW_KEY_HINTS, dpadShowKeyHints).apply()

    /** The expression on the calculator screen, kept when the app is closed. */
    var dpadExpression: String
        get() = prefs.getString(DPAD_EXPRESSION, "")!!
        set(dpadExpression) = prefs.edit().putString(DPAD_EXPRESSION, dpadExpression).apply()

    var dpadExpressionIsResult: Boolean
        get() = prefs.getBoolean(DPAD_EXPRESSION_IS_RESULT, false)
        set(dpadExpressionIsResult) = prefs.edit().putBoolean(DPAD_EXPRESSION_IS_RESULT, dpadExpressionIsResult).apply()

    fun getLastConverterUnits(converter: Converter): ConverterUnitsState? {
        val storedState = prefs.getString("$CONVERTER_UNITS_PREFIX.${converter.key}", null)
        return if (!storedState.isNullOrEmpty()) {
            val parts = storedState.split(",").map { part ->
                converter.units.first { it.key == part }
            }
            if (parts.size == 2) {
                ConverterUnitsState(parts[0], parts[1])
            } else {
                null
            }
        } else {
            null
        }
    }

    fun putLastConverterUnits(
        converter: Converter,
        topUnit: Converter.Unit,
        bottomUnit: Converter.Unit
    ) {
        prefs.edit().putString(
            "$CONVERTER_UNITS_PREFIX.${converter.key}",
            "${topUnit.key},${bottomUnit.key}"
        ).apply()
    }
}
