package org.fossify.math.helpers

import java.math.MathContext

// shared prefs
const val CONVERTER_UNITS_PREFIX = "converter_last_units"

// converter state
const val TOP_UNIT = "top_unit"
const val BOTTOM_UNIT = "bottom_unit"
const val CONVERTER_VALUE = "converter_value"
const val CONVERTER_STATE = "converter_state"

// big decimal configuration
val MATH_CONTEXT: MathContext? = MathContext.DECIMAL128

// DumberKit
const val DPAD_TOUCH_ENABLED = "dpad_touch_enabled"
const val DPAD_SHOW_KEY_HINTS = "dpad_show_key_hints"
const val DPAD_EXPRESSION = "dpad_expression"
const val DPAD_EXPRESSION_IS_RESULT = "dpad_expression_is_result"
const val DPAD_LAST_CONVERTER = "dpad_last_converter"
